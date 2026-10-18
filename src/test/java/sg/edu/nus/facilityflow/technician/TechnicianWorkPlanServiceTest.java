package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.AiInvocationCoordinator;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.Feature;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.Status;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

/** Requirement tests for the authorization-first Technician role adapter. */
class TechnicianWorkPlanServiceTest {
    private static final UUID TECHNICIAN_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("32000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("AI-SEC-001/002/REQ-TECH-012 wrong role is denied before context or assistant access")
    void wrongRoleIsDeniedBeforeRepositoryAndAssistant() {
        CountingRepository repository = new CountingRepository();
        CountingAssistant assistant = new CountingAssistant();
        TechnicianWorkPlanService service = serviceFor(
                new CurrentAccount(TECHNICIAN_ID, "requester", "Requester", AccountRole.REQUESTER, 0),
                repository,
                assistant);

        assertThrows(AccessDeniedException.class, () -> service.request("FF-000001"));
        assertEquals(0, repository.loadCalls.get());
        assertEquals(0, assistant.calls.get());
    }

    @Test
    @DisplayName("REQ-TECH-012 invalid display ID is rejected before protected context loading")
    void invalidDisplayIdMakesNoRepositoryOrAssistantCall() {
        CountingRepository repository = new CountingRepository();
        CountingAssistant assistant = new CountingAssistant();
        TechnicianWorkPlanService service = serviceFor(
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0),
                repository,
                assistant);

        WorkPlanResponse response = await(service.request("not-a-display-id"));
        assertEquals(WorkPlanFailureCode.AI_INVALID_REQUEST, failureCode(response));
        assertEquals(0, repository.loadCalls.get());
        assertEquals(0, assistant.calls.get());
    }

    @Test
    @DisplayName("REQ-TECH-017 changed assignment or version discards a completed result")
    void staleEligibilityDiscardsSuccess() {
        CountingRepository repository = new CountingRepository();
        repository.stillEligible = false;
        CountingAssistant assistant = new CountingAssistant();
        TechnicianWorkPlanService service = serviceFor(
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0),
                repository,
                assistant);

        WorkPlanResponse response = await(service.request("FF-000001"));
        assertEquals(WorkPlanFailureCode.DISCARDED_ACCESS_CHANGED, failureCode(response));
        assertEquals(1, repository.loadCalls.get());
        assertEquals(1, assistant.calls.get());
    }

    @Test
    @DisplayName("AI-SEC-028 async completion uses the persisted eligibility recheck")
    void asyncCompletionUsesPersistedEligibilityRecheck() throws Exception {
        Thread requestThread = Thread.currentThread();
        CurrentAccount account = new CurrentAccount(
                TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0);
        IdentityAuthorization authorization = new IdentityAuthorization(() -> {
            if (Thread.currentThread() != requestThread) {
                throw new IllegalStateException("request-thread authentication is unavailable");
            }
            return account;
        });
        CountingRepository repository = new CountingRepository();
        CompletableFuture<WorkPlanResponse> provider = new CompletableFuture<>();
        WorkPlanAssistant assistant = ignored -> provider;
        TechnicianWorkPlanService service = new TechnicianWorkPlanService(
                authorization,
                new AiAccessGuard(authorization),
                coordinator(),
                new ReadyLifecycle(),
                repository,
                assistant);

        CompletionStage<WorkPlanResponse> response = service.request("FF-000001");
        try (var providerExecutor = Executors.newSingleThreadExecutor()) {
            providerExecutor.submit(() -> provider.complete(
                    new CountingAssistant().request(context()).toCompletableFuture().join())).get();
        }

        assertTrue(await(response) instanceof WorkPlanResponse.Success);
        assertEquals(1, repository.eligibilityChecks.get());
    }

    @Test
    @DisplayName("AI-SEC-028 changed session snapshot discards an async result")
    void changedSessionSnapshotDiscardsAsyncResult() throws Exception {
        CurrentAccount account = new CurrentAccount(
                TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0);
        CurrentAccountSnapshot snapshot = new CurrentAccountSnapshot(
                TECHNICIAN_ID, AccountRole.TECHNICIAN, 7);
        CurrentAccountProvider currentAccounts = new CurrentAccountProvider() {
            @Override
            public CurrentAccount requireCurrentAccount() {
                return account;
            }

            @Override
            public CurrentAccountSnapshot requireCurrentAccountSnapshot() {
                return snapshot;
            }
        };
        IdentityAuthorization authorization = new IdentityAuthorization(currentAccounts);
        CountingRepository repository = new CountingRepository();
        repository.snapshotStillEligible = false;
        CompletableFuture<WorkPlanResponse> provider = new CompletableFuture<>();
        WorkPlanAssistant assistant = ignored -> provider;
        TechnicianWorkPlanService service = new TechnicianWorkPlanService(
                authorization,
                new AiAccessGuard(authorization),
                coordinator(),
                new ReadyLifecycle(),
                repository,
                assistant);

        CompletionStage<WorkPlanResponse> response = service.request("FF-000001");
        provider.complete(new CountingAssistant().request(context()).toCompletableFuture().join());

        assertEquals(WorkPlanFailureCode.DISCARDED_ACCESS_CHANGED, failureCode(await(response)));
        assertEquals(7, repository.observedSessionVersion);
    }

    @Test
    @DisplayName("AI-SEC-008 shared admission limit rejects before protected context loading")
    void admissionLimitRejectsBeforeRepositoryAndReleasesAfterCompletion() {
        CurrentAccount account = new CurrentAccount(
                TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0);
        IdentityAuthorization authorization = new IdentityAuthorization(() -> account);
        CountingRepository repository = new CountingRepository();
        CompletableFuture<WorkPlanResponse> provider = new CompletableFuture<>();
        WorkPlanAssistant assistant = ignored -> provider;
        AiInvocationCoordinator coordinator = new AiInvocationCoordinator(
                1, 5, 30, java.time.Clock.systemUTC());
        TechnicianWorkPlanService service = new TechnicianWorkPlanService(
                authorization,
                new AiAccessGuard(authorization),
                coordinator,
                new ReadyLifecycle(),
                repository,
                assistant);

        CompletionStage<WorkPlanResponse> first = service.request("FF-000001");
        WorkPlanResponse limited = await(service.request("FF-000001"));
        assertEquals(WorkPlanFailureCode.AI_LIMITED, failureCode(limited));
        assertEquals(1, repository.loadCalls.get());

        provider.complete(new CountingAssistant().request(context()).toCompletableFuture().join());
        assertTrue(await(first) instanceof WorkPlanResponse.Success);
    }

    @Test
    @DisplayName("AI-SEC-017 admitted request starts telemetry before context and finishes once")
    void admittedRequestPersistsLifecycleTelemetryAroundProtectedWork() {
        CountingRepository repository = new CountingRepository();
        CountingAssistant assistant = new CountingAssistant();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        TechnicianWorkPlanService service = serviceFor(
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0),
                repository, assistant, telemetry);

        WorkPlanResponse response = await(service.request("FF-000001"));

        assertTrue(response instanceof WorkPlanResponse.Success);
        assertEquals(1, telemetry.starts.get());
        assertEquals(1, telemetry.finishes.get());
        assertEquals(AiInvocationTelemetry.Status.SUCCEEDED, telemetry.status);
        assertTrue(telemetry.startedBeforeContext);
        assertEquals(AiInvocationTelemetry.Feature.WORK_PLAN, telemetry.feature);
        assertEquals("work-plan-v1", telemetry.templateVersion);
    }

    @Test
    @DisplayName("AI-SEC-017 terminal telemetry failure withholds a successful result")
    void terminalTelemetryFailureWithholdsResult() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        telemetry.failFinish = true;
        TechnicianWorkPlanService service = serviceFor(
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0),
                new CountingRepository(), new CountingAssistant(), telemetry);

        WorkPlanResponse response = await(service.request("FF-000001"));

        assertEquals(WorkPlanFailureCode.AI_UNAVAILABLE, failureCode(response));
        assertEquals(1, telemetry.starts.get());
        assertEquals(1, telemetry.finishes.get());
    }

    @Test
    @DisplayName("AI-SEC-008 deadline is created at use-case entry and is not reset by context loading")
    void deadlineIsCreatedAtEntryAndPassedToProviderAdapter() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
        AtomicReference<Instant> observedDeadline = new AtomicReference<>();
        WorkPlanAssistant assistant = new WorkPlanAssistant() {
            @Override
            public CompletionStage<WorkPlanResponse> request(WorkPlanContext context) {
                return CompletableFuture.completedFuture(
                        WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE));
            }

            @Override
            public CompletionStage<WorkPlanResponse> request(
                    WorkPlanContext context, Instant absoluteDeadline) {
                observedDeadline.set(absoluteDeadline);
                return CompletableFuture.completedFuture(new CountingAssistant().request(context)
                        .toCompletableFuture().join());
            }
        };
        TechnicianWorkPlanRepository repository = new TechnicianWorkPlanRepository() {
            @Override
            public Optional<WorkPlanContext> loadEligible(String displayId, UUID technicianId) {
                clock.advance(Duration.ofSeconds(5));
                return Optional.of(context());
            }

            @Override
            public boolean isStillEligible(WorkPlanContext context) {
                return true;
            }
        };
        TechnicianWorkPlanService service = new TechnicianWorkPlanService(
                new IdentityAuthorization(() -> new CurrentAccount(
                        TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0)),
                new AiAccessGuard(new IdentityAuthorization(() -> new CurrentAccount(
                        TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0))),
                coordinator(), new ReadyLifecycle(), repository, assistant,
                AiInvocationTelemetry.noop(), new AiConfiguration.Available(
                        java.net.URI.create("https://soclaas-api.comp.nus.edu.sg/v1"),
                        "synthetic-key", "synthetic-model", 30, 800, 5, 5, 30), clock);

        assertTrue(await(service.request("FF-000001")) instanceof WorkPlanResponse.Success);
        assertEquals(Instant.parse("2026-10-17T00:00:30Z"), observedDeadline.get());
    }

    @Test
    @DisplayName("AI-SEC-017 start telemetry failure prevents protected context loading")
    void startTelemetryFailurePreventsProtectedWork() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        telemetry.failStart = true;
        CountingRepository repository = new CountingRepository();
        CountingAssistant assistant = new CountingAssistant();
        TechnicianWorkPlanService service = serviceFor(
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN, 0),
                repository, assistant, telemetry);

        WorkPlanResponse response = await(service.request("FF-000001"));

        assertEquals(WorkPlanFailureCode.AI_UNAVAILABLE, failureCode(response));
        assertEquals(0, repository.loadCalls.get());
        assertEquals(0, assistant.calls.get());
        assertEquals(0, telemetry.finishes.get());
    }

    private static TechnicianWorkPlanService serviceFor(
            CurrentAccount account, CountingRepository repository, CountingAssistant assistant) {
        return serviceFor(account, repository, assistant, AiInvocationTelemetry.noop());
    }

    private static TechnicianWorkPlanService serviceFor(
            CurrentAccount account, CountingRepository repository, CountingAssistant assistant,
            AiInvocationTelemetry telemetry) {
        IdentityAuthorization authorization = new IdentityAuthorization(() -> account);
        return new TechnicianWorkPlanService(
                authorization,
                new AiAccessGuard(authorization),
                coordinator(),
                new ReadyLifecycle(),
                repository,
                assistant,
                telemetry,
                new AiConfiguration.Available(
                        java.net.URI.create("https://soclaas-api.comp.nus.edu.sg/v1"),
                        "synthetic-key", "synthetic-model", 30, 800, 5, 5, 30));
    }

    private static AiInvocationCoordinator coordinator() {
        return new AiInvocationCoordinator(5, 5, 30, java.time.Clock.systemUTC());
    }

    private static WorkPlanContext context() {
        return new WorkPlanContext(
                REQUEST_ID,
                "FF-000001",
                TECHNICIAN_ID,
                "Pump issue",
                "Pump is noisy.",
                "Plant room",
                "Mechanical",
                "High",
                "Normal",
                "ASSIGNED",
                1,
                List.of());
    }

    private static WorkPlanFailureCode failureCode(WorkPlanResponse response) {
        return ((WorkPlanResponse.Failure) response).code();
    }

    private static WorkPlanResponse await(CompletionStage<WorkPlanResponse> response) {
        try {
            return response.toCompletableFuture().get();
        } catch (Exception failure) {
            throw new AssertionError("Work Plan request did not complete", failure);
        }
    }

    private static final class CountingRepository implements TechnicianWorkPlanRepository {
        private final AtomicInteger loadCalls = new AtomicInteger();
        private final AtomicInteger eligibilityChecks = new AtomicInteger();
        private boolean stillEligible = true;
        private boolean snapshotStillEligible = true;
        private long observedSessionVersion = Long.MIN_VALUE;

        @Override
        public Optional<WorkPlanContext> loadEligible(String displayId, UUID technicianId) {
            loadCalls.incrementAndGet();
            return Optional.of(context());
        }

        @Override
        public boolean isStillEligible(WorkPlanContext context) {
            eligibilityChecks.incrementAndGet();
            return stillEligible;
        }

        @Override
        public boolean isStillEligible(WorkPlanContext context, CurrentAccountSnapshot accountSnapshot) {
            eligibilityChecks.incrementAndGet();
            observedSessionVersion = accountSnapshot.sessionVersion();
            return stillEligible && snapshotStillEligible;
        }
    }

    private static final class RecordingTelemetry implements AiInvocationTelemetry {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger finishes = new AtomicInteger();
        private volatile Status status;
        private volatile Feature feature;
        private volatile String templateVersion;
        private volatile boolean startedBeforeContext;
        private volatile boolean failStart;
        private volatile boolean failFinish;
        private Invocation invocation;

        @Override
        public Invocation start(StartRequest request) {
            starts.incrementAndGet();
            if (failStart) {
                throw new IllegalStateException("synthetic telemetry start failure");
            }
            feature = request.feature();
            templateVersion = request.templateVersion();
            startedBeforeContext = true;
            invocation = new Invocation(UUID.randomUUID(), java.time.Instant.now());
            return invocation;
        }

        @Override
        public void finish(Invocation invocation, Terminal terminal) {
            finishes.incrementAndGet();
            if (failFinish) {
                throw new IllegalStateException("synthetic telemetry failure");
            }
            assertEquals(this.invocation, invocation);
            status = terminal.status();
        }
    }

    private static final class CountingAssistant implements WorkPlanAssistant {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<WorkPlanResponse> request(WorkPlanContext context) {
            calls.incrementAndGet();
            WorkPlanResult plan = new WorkPlanResult(
                    List.of("Inspect the pump."),
                    List.of("Use appropriate PPE."),
                    List.of(),
                    List.of(),
                    List.of("Record evidence."),
                    List.of("Stop and escalate if unsafe."),
                    "Advisory only.");
            return CompletableFuture.completedFuture(new WorkPlanResponse.Success(plan));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class ReadyLifecycle implements AiProviderLifecycle {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public State state() {
            return State.READY;
        }

        @Override
        public CompletionStage<State> readiness() {
            return CompletableFuture.completedFuture(State.READY).minimalCompletionStage();
        }

        @Override
        public void close() {}
    }
}
