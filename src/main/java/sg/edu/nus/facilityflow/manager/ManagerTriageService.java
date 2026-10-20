package sg.edu.nus.facilityflow.manager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.AiReadinessDeadline;
import sg.edu.nus.facilityflow.ai.AiInvocationCoordinator;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.Status;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.manager.TriageAssistant;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

/** Authorization-first, non-mutating Manager Triage orchestration. */
@Service
public final class ManagerTriageService {
    private static final String TEMPLATE_VERSION = "triage-v2";

    private final IdentityAuthorization authorization;
    private final AiAccessGuard accessGuard;
    private final AiInvocationCoordinator coordinator;
    private final AiProviderLifecycle lifecycle;
    private final ManagerTriageRepository repository;
    private final TriageAssistant assistant;
    private final TriageCandidateRanker ranker;
    private final AiInvocationTelemetry telemetry;
    private final Clock clock;
    private final AiReadinessDeadline readinessDeadline;
    private final String model;
    private final int timeoutSeconds;

    @Autowired
    public ManagerTriageService(
            IdentityAuthorization authorization,
            AiAccessGuard accessGuard,
            AiInvocationCoordinator coordinator,
            AiProviderLifecycle lifecycle,
            ManagerTriageRepository repository,
            TriageAssistant assistant,
            TriageCandidateRanker ranker,
            AiInvocationTelemetry telemetry,
            AiConfiguration configuration) {
        this(authorization, accessGuard, coordinator, lifecycle, repository, assistant, ranker,
                telemetry, configuration, Clock.systemUTC());
    }

    public ManagerTriageService(
            IdentityAuthorization authorization,
            AiAccessGuard accessGuard,
            AiInvocationCoordinator coordinator,
            AiProviderLifecycle lifecycle,
            ManagerTriageRepository repository,
            TriageAssistant assistant,
            TriageCandidateRanker ranker,
            AiInvocationTelemetry telemetry,
            AiConfiguration configuration,
            Clock clock) {
        this(authorization, accessGuard, coordinator, lifecycle, repository, assistant, ranker,
                telemetry, configuration, clock, AiReadinessDeadline.system());
    }

    /** Injectable readiness scheduler for deterministic deadline verification. */
    public ManagerTriageService(
            IdentityAuthorization authorization,
            AiAccessGuard accessGuard,
            AiInvocationCoordinator coordinator,
            AiProviderLifecycle lifecycle,
            ManagerTriageRepository repository,
            TriageAssistant assistant,
            TriageCandidateRanker ranker,
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
        this.ranker = ranker;
        this.telemetry = telemetry;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.readinessDeadline = java.util.Objects.requireNonNull(readinessDeadline, "readinessDeadline");
        this.model = configuration instanceof AiConfiguration.Available available
                ? available.model() : null;
        this.timeoutSeconds = configuration instanceof AiConfiguration.Available available
                ? available.timeoutSeconds() : 30;
    }

    public CompletionStage<ManagerTriageResponse> request(String displayId) {
        if (displayId == null || !displayId.matches("FF-[0-9]{6}")) {
            return completedFailure(TriageFailureCode.AI_INVALID_REQUEST);
        }
        CurrentAccountSnapshot manager = authorization.requireRoleSnapshot(AccountRole.FACILITIES_MANAGER);
        Instant deadline = deadline(clock.instant(), timeoutSeconds);
        if (!clock.instant().isBefore(deadline)) {
            return completedFailure(TriageFailureCode.AI_TIMEOUT);
        }
        try {
            if (!lifecycle.enabled()) {
                return completedFailure(TriageFailureCode.AI_UNAVAILABLE);
            }
        } catch (RuntimeException disabled) {
            return completedFailure(TriageFailureCode.AI_UNAVAILABLE);
        }

        Optional<ManagerTriageRepository.Eligibility> eligibility;
        try {
            eligibility = repository.probe(displayId, manager);
        } catch (RuntimeException probeFailure) {
            return completedFailure(TriageFailureCode.AI_UNAVAILABLE);
        }
        if (eligibility.isEmpty()) {
            return completedFailure(TriageFailureCode.AI_UNAVAILABLE);
        }

        Optional<AiInvocationCoordinator.Permit> permit;
        try {
            permit = coordinator.tryAcquire(manager.id());
        } catch (RuntimeException admissionFailure) {
            return completedFailure(TriageFailureCode.AI_UNAVAILABLE);
        }
        if (permit.isEmpty()) {
            return completedFailure(TriageFailureCode.AI_LIMITED);
        }

        AiInvocationTelemetry.Invocation invocation;
        try {
            invocation = telemetry.start(new AiInvocationTelemetry.StartRequest(
                    manager.id(), sg.edu.nus.facilityflow.operations.RequestCorrelationFilter.currentId(), AiInvocationTelemetry.Feature.TRIAGE,
                    model, TEMPLATE_VERSION));
        } catch (RuntimeException telemetryFailure) {
            permit.get().release();
            return completedFailure(TriageFailureCode.AI_UNAVAILABLE);
        }

        ManagerTriageRepository.ContextSnapshot context;
        try {
            context = repository.loadContext(eligibility.get());
        } catch (RuntimeException contextFailure) {
            return finish(permit.get(), invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE));
        }
        if (!clock.instant().isBefore(deadline)) {
            return finish(permit.get(), invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_TIMEOUT));
        }

        CompletionStage<AiProviderLifecycle.State> readiness;
        try {
            readiness = lifecycle.readiness();
        } catch (RuntimeException readinessFailure) {
            return finish(permit.get(), invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE));
        }
        if (readiness == null) {
            return finish(permit.get(), invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE));
        }
        return readinessDeadline.await(readiness, deadline, clock)
                .thenCompose(outcome -> outcome == AiReadinessDeadline.Outcome.TIMED_OUT
                        ? finish(permit.get(), invocation,
                                ManagerTriageResponse.failure(TriageFailureCode.AI_TIMEOUT))
                        : invokeIfReady(outcome == AiReadinessDeadline.Outcome.READY,
                                deadline, manager, permit.get(), invocation, context));
    }

    private CompletionStage<ManagerTriageResponse> invokeIfReady(
            boolean ready,
            Instant deadline,
            CurrentAccountSnapshot manager,
            AiInvocationCoordinator.Permit permit,
            AiInvocationTelemetry.Invocation invocation,
            ManagerTriageRepository.ContextSnapshot context) {
        if (!clock.instant().isBefore(deadline)) {
            return finish(permit, invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_TIMEOUT));
        }
        if (!ready) {
            return finish(permit, invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE));
        }
        try {
            CompletionStage<TriageResponse> response;
            try (AiInvocationTelemetry.Scope ignored = AiInvocationTelemetry.bind(invocation)) {
                response = accessGuard.forManager(
                        manager, () -> context.assistantContext(),
                        value -> assistant.request(value, deadline));
            }
            return response.handle((value, failure) -> postProcess(
                            value, failure, deadline, manager, permit, context))
                    .thenCompose(value -> finish(permit, invocation, value));
        } catch (RuntimeException dispatchFailure) {
            return finish(permit, invocation,
                    ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE));
        }
    }

    private ManagerTriageResponse postProcess(
            TriageResponse response,
            Throwable failure,
            Instant deadline,
            CurrentAccountSnapshot manager,
            AiInvocationCoordinator.Permit permit,
            ManagerTriageRepository.ContextSnapshot context) {
        if (failure != null || response == null) {
            return ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE);
        }
        if (!clock.instant().isBefore(deadline)) {
            return ManagerTriageResponse.failure(TriageFailureCode.AI_TIMEOUT);
        }
        if (!permit.valid()) {
            return ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE);
        }
        if (response instanceof TriageResponse.Failure failed) {
            return ManagerTriageResponse.failure(failed.code());
        }
        TriageResult result = ((TriageResponse.Success) response).result();
        try {
            if (!repository.isStillEligible(context, manager, result)) {
                return ManagerTriageResponse.failure(TriageFailureCode.DISCARDED_ACCESS_CHANGED);
            }
            var review = new ManagerTriageResponse.ReviewContext(
                    context.eligibility().requestVersion(), context.assistantContext().status(),
                    TriageSnapshotFingerprint.create(context.activeCategories(), context.activeSkills(),
                            context.candidates()),
                    context.activeCategories().stream()
                            .map(value -> new ManagerTriageResponse.ReviewOption(value.id(), value.name())).toList(),
                    context.candidates().stream().filter(TechnicianCandidate::active)
                            .map(value -> new ManagerTriageResponse.ReviewOption(value.accountId(), value.username())).toList());
            return new ManagerTriageResponse.Success(result,
                    ranker.rank(result, context.candidates()), review);
        } catch (RuntimeException staleOrRankingFailure) {
            return ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE);
        }
    }

    private CompletionStage<ManagerTriageResponse> finish(
            AiInvocationCoordinator.Permit permit,
            AiInvocationTelemetry.Invocation invocation,
            ManagerTriageResponse response) {
        ManagerTriageResponse terminalResponse = response;
        try {
            long latencyMs = Math.max(0,
                    Duration.between(invocation.startedAt(), clock.instant()).toMillis());
            telemetry.finish(invocation, new AiInvocationTelemetry.Terminal(
                    status(response), latencyMs, invocation.retryCount(), invocation.inputTokens(),
                    invocation.outputTokens(), invocation.totalTokens()));
        } catch (RuntimeException telemetryFailure) {
            sg.edu.nus.facilityflow.ai.AiFailureDiagnostics.persistenceFailed(invocation);
            terminalResponse = ManagerTriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE);
        } finally {
            permit.release();
        }
        return CompletableFuture.completedFuture(terminalResponse).minimalCompletionStage();
    }

    private static Status status(ManagerTriageResponse response) {
        if (response instanceof ManagerTriageResponse.Success) {
            return Status.SUCCEEDED;
        }
        return switch (((ManagerTriageResponse.Failure) response).code()) {
            case AI_UNAVAILABLE -> Status.AI_UNAVAILABLE;
            case AI_LIMITED -> Status.AI_LIMITED;
            case AI_INVALID_REQUEST -> Status.AI_INVALID_REQUEST;
            case AI_CONFIGURATION_ERROR -> Status.AI_CONFIGURATION_ERROR;
            case AI_TIMEOUT -> Status.AI_TIMEOUT;
            case AI_INVALID_OUTPUT -> Status.AI_INVALID_OUTPUT;
            case DISCARDED_ACCESS_CHANGED -> Status.DISCARDED_ACCESS_CHANGED;
        };
    }

    private static CompletionStage<ManagerTriageResponse> completedFailure(TriageFailureCode code) {
        return CompletableFuture.<ManagerTriageResponse>completedFuture(
                ManagerTriageResponse.failure(code)).minimalCompletionStage();
    }

    private static Instant deadline(Instant start, int timeoutSeconds) {
        try {
            return start.plusSeconds(timeoutSeconds);
        } catch (RuntimeException overflow) {
            return Instant.MAX;
        }
    }
}
