package sg.edu.nus.facilityflow.technician;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.AiInvocationCoordinator;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.Status;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

/** Authorization-first orchestration for the Technician Work Plan Assistant. */
@Service
public final class TechnicianWorkPlanService {
    private static final String TEMPLATE_VERSION = "work-plan-v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnicianWorkPlanService.class);
    private final IdentityAuthorization authorization;
    private final AiAccessGuard aiAccessGuard;
    private final AiInvocationCoordinator invocationCoordinator;
    private final AiProviderLifecycle lifecycle;
    private final TechnicianWorkPlanRepository repository;
    private final WorkPlanAssistant assistant;
    private final AiInvocationTelemetry telemetry;
    private final String model;

    @Autowired
    public TechnicianWorkPlanService(
            IdentityAuthorization authorization,
            AiAccessGuard aiAccessGuard,
            AiInvocationCoordinator invocationCoordinator,
            AiProviderLifecycle lifecycle,
            TechnicianWorkPlanRepository repository,
            WorkPlanAssistant assistant,
            AiInvocationTelemetry telemetry,
            AiConfiguration configuration) {
        this.authorization = authorization;
        this.aiAccessGuard = aiAccessGuard;
        this.invocationCoordinator = invocationCoordinator;
        this.lifecycle = lifecycle;
        this.repository = repository;
        this.assistant = assistant;
        this.telemetry = telemetry;
        this.model = configuration instanceof AiConfiguration.Available available
                ? available.model()
                : null;
    }

    /** Direct-construction seam retained for feature tests without database telemetry. */
    public TechnicianWorkPlanService(
            IdentityAuthorization authorization,
            AiAccessGuard aiAccessGuard,
            AiInvocationCoordinator invocationCoordinator,
            AiProviderLifecycle lifecycle,
            TechnicianWorkPlanRepository repository,
            WorkPlanAssistant assistant) {
        this(authorization, aiAccessGuard, invocationCoordinator, lifecycle, repository, assistant,
                AiInvocationTelemetry.noop(), new AiConfiguration.Disabled());
    }

    public CompletionStage<WorkPlanResponse> request(String displayId) {
        if (displayId == null || !displayId.matches("FF-[0-9]{6}")) {
            return completedFailure(WorkPlanFailureCode.AI_INVALID_REQUEST);
        }
        CurrentAccount technician = authorization.requireRole(AccountRole.TECHNICIAN);
        try {
            if (!lifecycle.enabled()) {
                return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
            }
        } catch (RuntimeException disabled) {
            return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
        }
        java.util.Optional<AiInvocationCoordinator.Permit> permit;
        try {
            permit = invocationCoordinator.tryAcquire(technician.id());
        } catch (RuntimeException admissionFailure) {
            return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
        }
        if (permit.isEmpty()) {
            return completedFailure(WorkPlanFailureCode.AI_LIMITED);
        }
        AiInvocationTelemetry.Invocation invocation;
        try {
            invocation = telemetry.start(new AiInvocationTelemetry.StartRequest(
                    technician.id(), UUID.randomUUID(), AiInvocationTelemetry.Feature.WORK_PLAN,
                    model, TEMPLATE_VERSION));
        } catch (RuntimeException telemetryFailure) {
            permit.get().release();
            return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
        }
        CompletionStage<AiProviderLifecycle.State> readiness;
        try {
            readiness = lifecycle.readiness();
        } catch (RuntimeException readinessFailure) {
            return finish(permit.get(), invocation, WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE));
        }
        if (readiness == null) {
            return finish(permit.get(), invocation, WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE));
        }
        return readiness.handle((state, readinessFailure) ->
                readinessFailure == null && state == AiProviderLifecycle.State.READY)
                .thenCompose(ready -> {
                    if (!ready) {
                        return finish(permit.get(), invocation,
                                WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE));
                    }
                    OptionalContext loaded = load(displayId, technician.id());
                    if (loaded.context() == null) {
                        return finish(permit.get(), invocation, WorkPlanResponse.failure(loaded.failure()));
                    }
                    WorkPlanContext context = loaded.context();
                    try {
                        CompletionStage<WorkPlanResponse> response = aiAccessGuard.forTechnician(
                                context.assignedTechnicianId(), () -> context, assistant::request);
                        return response.handle((value, failure) -> {
                            if (failure != null || value == null) {
                                return WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE);
                            }
                            if (!permit.get().valid()) {
                                return WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE);
                            }
                            if (value instanceof WorkPlanResponse.Success) {
                                try {
                                    authorization.requireAssignedTechnician(context.assignedTechnicianId());
                                } catch (RuntimeException accessChanged) {
                                    return WorkPlanResponse.failure(
                                            WorkPlanFailureCode.DISCARDED_ACCESS_CHANGED);
                                }
                                try {
                                    if (!repository.isStillEligible(context)) {
                                        return WorkPlanResponse.failure(
                                                WorkPlanFailureCode.DISCARDED_ACCESS_CHANGED);
                                    }
                                } catch (RuntimeException recheckFailure) {
                                    return WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE);
                                }
                            }
                            return value;
                        }).thenCompose(value -> finish(permit.get(), invocation, value));
                    } catch (RuntimeException accessFailure) {
                        return finish(permit.get(), invocation,
                                WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE));
                    }
                });
    }

    private CompletionStage<WorkPlanResponse> finish(
            AiInvocationCoordinator.Permit permit,
            AiInvocationTelemetry.Invocation invocation,
            WorkPlanResponse response) {
        WorkPlanResponse terminalResponse = response;
        try {
            long latencyMs = Math.max(0, Duration.between(invocation.startedAt(), Instant.now()).toMillis());
            telemetry.finish(invocation, new AiInvocationTelemetry.Terminal(
                    status(response), latencyMs, 0, null, null, null));
        } catch (RuntimeException telemetryFailure) {
            LOGGER.warn("AI terminal telemetry persistence failed for invocation {}", invocation.id());
            terminalResponse = WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE);
        } finally {
            permit.release();
        }
        return CompletableFuture.completedFuture(terminalResponse).minimalCompletionStage();
    }

    private static Status status(WorkPlanResponse response) {
        if (response instanceof WorkPlanResponse.Success) {
            return Status.SUCCEEDED;
        }
        return switch (((WorkPlanResponse.Failure) response).code()) {
            case AI_UNAVAILABLE -> Status.AI_UNAVAILABLE;
            case AI_LIMITED -> Status.AI_LIMITED;
            case AI_INVALID_REQUEST -> Status.AI_INVALID_REQUEST;
            case AI_CONFIGURATION_ERROR -> Status.AI_CONFIGURATION_ERROR;
            case AI_TIMEOUT -> Status.AI_TIMEOUT;
            case AI_INVALID_OUTPUT -> Status.AI_INVALID_OUTPUT;
            case DISCARDED_ACCESS_CHANGED -> Status.DISCARDED_ACCESS_CHANGED;
        };
    }

    private OptionalContext load(String displayId, UUID technicianId) {
        try {
            return repository.loadEligible(displayId, technicianId)
                    .map(context -> new OptionalContext(context, null))
                    .orElseGet(() -> new OptionalContext(null, WorkPlanFailureCode.AI_UNAVAILABLE));
        } catch (RuntimeException failure) {
            return new OptionalContext(null, WorkPlanFailureCode.AI_UNAVAILABLE);
        }
    }

    private static CompletionStage<WorkPlanResponse> completedFailure(WorkPlanFailureCode code) {
        WorkPlanResponse response = WorkPlanResponse.failure(code);
        return CompletableFuture.<WorkPlanResponse>completedFuture(response).minimalCompletionStage();
    }

    private record OptionalContext(WorkPlanContext context, WorkPlanFailureCode failure) {
        private OptionalContext {
            if ((context == null) == (failure == null)) {
                throw new IllegalArgumentException("exactly one context or failure is required");
            }
        }
    }
}
