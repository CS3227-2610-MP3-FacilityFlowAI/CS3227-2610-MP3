package sg.edu.nus.facilityflow.requester;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.AiReadinessDeadline;
import sg.edu.nus.facilityflow.ai.AiInvocationCoordinator;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.Status;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.requester.SmartReportAssistant;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportRepository.ContextSnapshot;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportRepository.Eligibility;

/** Authorization-first Smart Report orchestration for REQ-RQ-012–017 and AI-SEC-001/008/017/028. */
@Service
public final class RequesterSmartReportService {
    private static final String TEMPLATE_VERSION = "smart-report-v2";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequesterSmartReportService.class);

    private final IdentityAuthorization authorization;
    private final AiAccessGuard accessGuard;
    private final AiInvocationCoordinator coordinator;
    private final AiProviderLifecycle lifecycle;
    private final RequesterSmartReportRepository repository;
    private final SmartReportAssistant assistant;
    private final AiInvocationTelemetry telemetry;
    private final Clock clock;
    private final AiReadinessDeadline readinessDeadline;
    private final String model;
    private final int timeoutSeconds;

    @Autowired
    public RequesterSmartReportService(
            IdentityAuthorization authorization,
            AiAccessGuard accessGuard,
            AiInvocationCoordinator coordinator,
            AiProviderLifecycle lifecycle,
            RequesterSmartReportRepository repository,
            SmartReportAssistant assistant,
            AiInvocationTelemetry telemetry,
            AiConfiguration configuration) {
        this(authorization, accessGuard, coordinator, lifecycle, repository, assistant,
                telemetry, configuration, Clock.systemUTC());
    }

    public RequesterSmartReportService(
            IdentityAuthorization authorization,
            AiAccessGuard accessGuard,
            AiInvocationCoordinator coordinator,
            AiProviderLifecycle lifecycle,
            RequesterSmartReportRepository repository,
            SmartReportAssistant assistant,
            AiInvocationTelemetry telemetry,
            AiConfiguration configuration,
            Clock clock) {
        this(authorization, accessGuard, coordinator, lifecycle, repository, assistant,
                telemetry, configuration, clock, AiReadinessDeadline.system());
    }

    /** Injectable readiness scheduler for deterministic deadline verification. */
    public RequesterSmartReportService(
            IdentityAuthorization authorization,
            AiAccessGuard accessGuard,
            AiInvocationCoordinator coordinator,
            AiProviderLifecycle lifecycle,
            RequesterSmartReportRepository repository,
            SmartReportAssistant assistant,
            AiInvocationTelemetry telemetry,
            AiConfiguration configuration,
            Clock clock,
            AiReadinessDeadline readinessDeadline) {
        this.authorization = authorization;
        this.accessGuard = accessGuard;
        this.coordinator = coordinator;
        this.lifecycle = lifecycle;
        this.repository = repository;
        this.assistant = assistant;
        this.telemetry = telemetry;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.readinessDeadline = java.util.Objects.requireNonNull(readinessDeadline, "readinessDeadline");
        this.model = configuration instanceof AiConfiguration.Available available
                ? available.model() : null;
        this.timeoutSeconds = configuration instanceof AiConfiguration.Available available
                ? available.timeoutSeconds() : 30;
    }

    public CompletionStage<SmartReportResponse> requestCreate(RequesterSmartReportInput input) {
        return request(null, input);
    }

    public CompletionStage<SmartReportResponse> requestEdit(
            String displayId, RequesterSmartReportInput input) {
        if (displayId == null || !displayId.matches("FF-[0-9]{6}")) {
            return completedFailure(SmartReportFailureCode.AI_INVALID_REQUEST);
        }
        return request(displayId, input);
    }

    private CompletionStage<SmartReportResponse> request(
            String displayId, RequesterSmartReportInput input) {
        try {
            if (input == null) {
                return completedFailure(SmartReportFailureCode.AI_INVALID_REQUEST);
            }
            input.validate();
        } catch (RuntimeException invalidInput) {
            return completedFailure(SmartReportFailureCode.AI_INVALID_REQUEST);
        }

        CurrentAccountSnapshot requester = authorization.requireRoleSnapshot(AccountRole.REQUESTER);
        Instant deadline = deadline(clock.instant(), timeoutSeconds);
        if (!clock.instant().isBefore(deadline)) {
            return completedFailure(SmartReportFailureCode.AI_TIMEOUT);
        }
        try {
            if (!lifecycle.enabled()) {
                return completedFailure(SmartReportFailureCode.AI_UNAVAILABLE);
            }
        } catch (RuntimeException disabled) {
            return completedFailure(SmartReportFailureCode.AI_UNAVAILABLE);
        }

        Optional<Eligibility> eligibility;
        try {
            eligibility = displayId == null
                    ? repository.probeCreate(requester)
                    : repository.probeEdit(displayId, requester);
        } catch (RuntimeException probeFailure) {
            return completedFailure(SmartReportFailureCode.AI_UNAVAILABLE);
        }
        if (eligibility.isEmpty()) {
            return completedFailure(SmartReportFailureCode.AI_UNAVAILABLE);
        }

        Optional<AiInvocationCoordinator.Permit> permit;
        try {
            permit = coordinator.tryAcquire(requester.id());
        } catch (RuntimeException admissionFailure) {
            return completedFailure(SmartReportFailureCode.AI_UNAVAILABLE);
        }
        if (permit.isEmpty()) {
            return completedFailure(SmartReportFailureCode.AI_LIMITED);
        }

        AiInvocationTelemetry.Invocation invocation;
        try {
            invocation = telemetry.start(new AiInvocationTelemetry.StartRequest(
                    requester.id(), UUID.randomUUID(), AiInvocationTelemetry.Feature.SMART_REPORT,
                    model, TEMPLATE_VERSION));
        } catch (RuntimeException telemetryFailure) {
            permit.get().release();
            return completedFailure(SmartReportFailureCode.AI_UNAVAILABLE);
        }

        ContextSnapshot context;
        try {
            context = repository.loadContext(eligibility.get(), input);
        } catch (IllegalArgumentException invalidContext) {
            return finish(permit.get(), invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_INVALID_REQUEST));
        } catch (RuntimeException contextFailure) {
            return finish(permit.get(), invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE));
        }
        if (!clock.instant().isBefore(deadline)) {
            return finish(permit.get(), invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_TIMEOUT));
        }

        CompletionStage<AiProviderLifecycle.State> readiness;
        try {
            readiness = lifecycle.readiness();
        } catch (RuntimeException readinessFailure) {
            return finish(permit.get(), invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE));
        }
        if (readiness == null) {
            return finish(permit.get(), invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE));
        }

        return readinessDeadline.await(readiness, deadline, clock)
                .thenCompose(outcome -> outcome == AiReadinessDeadline.Outcome.TIMED_OUT
                        ? finish(permit.get(), invocation,
                                SmartReportResponse.failure(SmartReportFailureCode.AI_TIMEOUT))
                        : invokeIfReady(outcome == AiReadinessDeadline.Outcome.READY,
                                deadline, requester, permit.get(), invocation, context));
    }

    private CompletionStage<SmartReportResponse> invokeIfReady(
            boolean ready,
            Instant deadline,
            CurrentAccountSnapshot requester,
            AiInvocationCoordinator.Permit permit,
            AiInvocationTelemetry.Invocation invocation,
            ContextSnapshot context) {
        if (!clock.instant().isBefore(deadline)) {
            return finish(permit, invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_TIMEOUT));
        }
        if (!ready) {
            return finish(permit, invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE));
        }

        try {
            CompletionStage<SmartReportResponse> response;
            SmartReportContext assistantContext = context.assistantContext();
            try (AiInvocationTelemetry.Scope ignored = AiInvocationTelemetry.bind(invocation)) {
                response = accessGuard.forRequester(
                        requester,
                        context.eligibility().requesterId(),
                        () -> assistantContext,
                        value -> assistant.request(value, deadline));
            }
            return response.handle((value, failure) -> postProcess(
                            value, failure, deadline, requester, permit, context))
                    .thenCompose(value -> finish(permit, invocation, value));
        } catch (RuntimeException dispatchFailure) {
            return finish(permit, invocation,
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE));
        }
    }

    private SmartReportResponse postProcess(
            SmartReportResponse response,
            Throwable failure,
            Instant deadline,
            CurrentAccountSnapshot requester,
            AiInvocationCoordinator.Permit permit,
            ContextSnapshot context) {
        if (failure != null || response == null) {
            return SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE);
        }
        if (!clock.instant().isBefore(deadline)) {
            return SmartReportResponse.failure(SmartReportFailureCode.AI_TIMEOUT);
        }
        if (!permit.valid()) {
            return SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE);
        }
        if (response instanceof SmartReportResponse.Success success) {
            try {
                if (!repository.isStillEligible(context, requester, success.result())) {
                    return SmartReportResponse.failure(SmartReportFailureCode.DISCARDED_ACCESS_CHANGED);
                }
            } catch (RuntimeException recheckFailure) {
                return SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE);
            }
        }
        return response;
    }

    private CompletionStage<SmartReportResponse> finish(
            AiInvocationCoordinator.Permit permit,
            AiInvocationTelemetry.Invocation invocation,
            SmartReportResponse response) {
        SmartReportResponse terminalResponse = response;
        try {
            long latencyMs = Math.max(0,
                    Duration.between(invocation.startedAt(), clock.instant()).toMillis());
            telemetry.finish(invocation, new AiInvocationTelemetry.Terminal(
                    status(response), latencyMs, invocation.retryCount(), invocation.inputTokens(),
                    invocation.outputTokens(), invocation.totalTokens()));
        } catch (RuntimeException telemetryFailure) {
            LOGGER.warn("AI terminal telemetry persistence failed for invocation {}", invocation.id());
            terminalResponse = SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE);
        } finally {
            permit.release();
        }
        return CompletableFuture.completedFuture(terminalResponse).minimalCompletionStage();
    }

    private static Status status(SmartReportResponse response) {
        if (response instanceof SmartReportResponse.Success) {
            return Status.SUCCEEDED;
        }
        return switch (((SmartReportResponse.Failure) response).code()) {
            case AI_UNAVAILABLE -> Status.AI_UNAVAILABLE;
            case AI_LIMITED -> Status.AI_LIMITED;
            case AI_INVALID_REQUEST -> Status.AI_INVALID_REQUEST;
            case AI_CONFIGURATION_ERROR -> Status.AI_CONFIGURATION_ERROR;
            case AI_TIMEOUT -> Status.AI_TIMEOUT;
            case AI_INVALID_OUTPUT -> Status.AI_INVALID_OUTPUT;
            case DISCARDED_ACCESS_CHANGED -> Status.DISCARDED_ACCESS_CHANGED;
        };
    }

    private static CompletionStage<SmartReportResponse> completedFailure(
            SmartReportFailureCode code) {
        return CompletableFuture.<SmartReportResponse>completedFuture(
                SmartReportResponse.failure(code)).minimalCompletionStage();
    }

    private static Instant deadline(Instant start, int timeoutSeconds) {
        try {
            return start.plusSeconds(timeoutSeconds);
        } catch (RuntimeException overflow) {
            return Instant.MAX;
        }
    }
}
