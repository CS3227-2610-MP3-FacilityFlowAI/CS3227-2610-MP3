package sg.edu.nus.facilityflow.technician;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.AiInvocationCoordinator;
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
    private final IdentityAuthorization authorization;
    private final AiAccessGuard aiAccessGuard;
    private final AiInvocationCoordinator invocationCoordinator;
    private final AiProviderLifecycle lifecycle;
    private final TechnicianWorkPlanRepository repository;
    private final WorkPlanAssistant assistant;

    public TechnicianWorkPlanService(
            IdentityAuthorization authorization,
            AiAccessGuard aiAccessGuard,
            AiInvocationCoordinator invocationCoordinator,
            AiProviderLifecycle lifecycle,
            TechnicianWorkPlanRepository repository,
            WorkPlanAssistant assistant) {
        this.authorization = authorization;
        this.aiAccessGuard = aiAccessGuard;
        this.invocationCoordinator = invocationCoordinator;
        this.lifecycle = lifecycle;
        this.repository = repository;
        this.assistant = assistant;
    }

    public CompletionStage<WorkPlanResponse> request(String displayId) {
        if (displayId == null || !displayId.matches("FF-[0-9]{6}")) {
            return completedFailure(WorkPlanFailureCode.AI_INVALID_REQUEST);
        }
        CurrentAccount technician = authorization.requireRole(AccountRole.TECHNICIAN);
        CompletionStage<AiProviderLifecycle.State> readiness;
        try {
            readiness = lifecycle.readiness();
        } catch (RuntimeException readinessFailure) {
            return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
        }
        if (readiness == null) {
            return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
        }
        return readiness.handle((state, readinessFailure) ->
                readinessFailure == null && state == AiProviderLifecycle.State.READY)
                .thenCompose(ready -> {
                    if (!ready) {
                        return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
                    }
                    java.util.Optional<AiInvocationCoordinator.Permit> permit =
                            invocationCoordinator.tryAcquire(technician.id());
                    if (permit.isEmpty()) {
                        return completedFailure(WorkPlanFailureCode.AI_LIMITED);
                    }
                    OptionalContext loaded = load(displayId, technician.id());
                    if (loaded.context() == null) {
                        permit.get().release();
                        return completedFailure(loaded.failure());
                    }
                    WorkPlanContext context = loaded.context();
                    try {
                        CompletionStage<WorkPlanResponse> response = aiAccessGuard.forTechnician(
                                context.assignedTechnicianId(), () -> context, assistant::request);
                        return response.handle((value, failure) -> {
                            if (failure != null || value == null) {
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
                        }).whenComplete((value, failure) -> permit.get().release());
                    } catch (RuntimeException accessFailure) {
                        permit.get().release();
                        return completedFailure(WorkPlanFailureCode.AI_UNAVAILABLE);
                    }
                });
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
