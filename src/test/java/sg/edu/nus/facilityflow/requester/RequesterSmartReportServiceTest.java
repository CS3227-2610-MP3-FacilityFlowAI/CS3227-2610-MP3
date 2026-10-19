package sg.edu.nus.facilityflow.requester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.AiInvocationCoordinator;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.requester.SmartReportAssistant;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

class RequesterSmartReportServiceTest {
    private static final UUID REQUESTER_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID CATEGORY_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    @Test
    @DisplayName("RQ-AI01 create-form suggestion is authorized, typed and recorded without persistence")
    void createSuggestionUsesAuthorizedContextAndTelemetry() {
        FakeRepository repository = new FakeRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        AtomicInteger calls = new AtomicInteger();
        SmartReportAssistant assistant = context -> {
            calls.incrementAndGet();
            assertEquals(List.of("Electrical"), context.activeCategoryLabels());
            return CompletableFuture.completedFuture(success());
        };

        SmartReportResponse response = await(service(
                AccountRole.REQUESTER, repository, assistant, telemetry, coordinator(5))
                .requestCreate(input()));

        assertInstanceOf(SmartReportResponse.Success.class, response);
        assertEquals(1, repository.createProbes.get());
        assertEquals(1, repository.contextLoads.get());
        assertEquals(1, repository.rechecks.get());
        assertEquals(1, calls.get());
        assertEquals(AiInvocationTelemetry.Feature.SMART_REPORT, telemetry.feature);
        assertEquals(AiInvocationTelemetry.Status.SUCCEEDED, telemetry.status);
    }

    @Test
    @DisplayName("AI-SEC-029 invalid browser fields reject before authorization, context or provider")
    void invalidInputRejectsBeforeProtectedWork() {
        FakeRepository repository = new FakeRepository();
        AtomicInteger calls = new AtomicInteger();
        SmartReportAssistant assistant = context -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(success());
        };

        SmartReportResponse response = await(service(
                AccountRole.REQUESTER, repository, assistant,
                AiInvocationTelemetry.noop(), coordinator(5))
                .requestCreate(new RequesterSmartReportInput(null, null, null, null, null)));

        assertEquals(SmartReportFailureCode.AI_INVALID_REQUEST, failure(response));
        assertEquals(0, repository.createProbes.get());
        assertEquals(0, repository.contextLoads.get());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("REQ-RQ-012 wrong role is denied before repository or provider access")
    void wrongRoleIsDeniedBeforeProtectedWork() {
        FakeRepository repository = new FakeRepository();
        AtomicInteger calls = new AtomicInteger();
        RequesterSmartReportService service = service(
                AccountRole.TECHNICIAN, repository,
                context -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(success());
                }, AiInvocationTelemetry.noop(), coordinator(5));

        assertThrows(AccessDeniedException.class, () -> service.requestCreate(input()));
        assertEquals(0, repository.createProbes.get());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("RQ-AI04 inaccessible persisted edit denies before catalogue context and provider")
    void inaccessibleEditDoesNotLoadContextOrCallProvider() {
        FakeRepository repository = new FakeRepository();
        repository.editEligible = false;
        AtomicInteger calls = new AtomicInteger();
        SmartReportAssistant assistant = context -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(success());
        };

        SmartReportResponse response = await(service(
                AccountRole.REQUESTER, repository, assistant,
                AiInvocationTelemetry.noop(), coordinator(5))
                .requestEdit("FF-000041", input()));

        assertEquals(SmartReportFailureCode.AI_UNAVAILABLE, failure(response));
        assertEquals(1, repository.editProbes.get());
        assertEquals(0, repository.contextLoads.get());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("AI-SEC-008 local admission rejects before catalogue context and provider")
    void admissionRejectsBeforeContextLoading() {
        FakeRepository repository = new FakeRepository();
        AtomicInteger calls = new AtomicInteger();

        SmartReportResponse response = await(service(
                AccountRole.REQUESTER, repository,
                context -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(success());
                }, AiInvocationTelemetry.noop(), coordinator(0))
                .requestCreate(input()));

        assertEquals(SmartReportFailureCode.AI_LIMITED, failure(response));
        assertEquals(1, repository.createProbes.get());
        assertEquals(0, repository.contextLoads.get());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("RQ-AI04 and AI-SEC-028 stale edit/session/category state discards async success")
    void staleAsyncSuccessIsDiscarded() {
        FakeRepository repository = new FakeRepository();
        repository.stillEligible = false;
        CompletableFuture<SmartReportResponse> provider = new CompletableFuture<>();
        SmartReportAssistant assistant = context -> provider;
        RequesterSmartReportService service = service(
                AccountRole.REQUESTER, repository, assistant,
                AiInvocationTelemetry.noop(), coordinator(5));

        CompletionStage<SmartReportResponse> stage = service.requestEdit("FF-000041", input());
        provider.complete(success());

        assertEquals(SmartReportFailureCode.DISCARDED_ACCESS_CHANGED, failure(await(stage)));
        assertEquals(7, repository.observedSessionVersion);
        assertEquals(1, repository.rechecks.get());
    }

    private static RequesterSmartReportService service(
            AccountRole role,
            FakeRepository repository,
            SmartReportAssistant assistant,
            AiInvocationTelemetry telemetry,
            AiInvocationCoordinator coordinator) {
        CurrentAccount account = new CurrentAccount(REQUESTER_ID, "requester", "Requester", role, 0);
        CurrentAccountSnapshot snapshot = new CurrentAccountSnapshot(REQUESTER_ID, role, 7);
        CurrentAccountProvider accounts = new CurrentAccountProvider() {
            @Override
            public CurrentAccount requireCurrentAccount() {
                return account;
            }

            @Override
            public CurrentAccountSnapshot requireCurrentAccountSnapshot() {
                return snapshot;
            }
        };
        IdentityAuthorization authorization = new IdentityAuthorization(accounts);
        return new RequesterSmartReportService(
                authorization,
                new AiAccessGuard(authorization),
                coordinator,
                new ReadyLifecycle(),
                repository,
                assistant,
                telemetry,
                configuration(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiInvocationCoordinator coordinator(int maxInFlight) {
        return new AiInvocationCoordinator(maxInFlight, 5, 30, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiConfiguration.Available configuration() {
        return new AiConfiguration.Available(
                URI.create("https://soclaas-api.comp.nus.edu.sg/v1"),
                "synthetic-key", "synthetic-model", 30, 800, 5, 5, 30);
    }

    private static RequesterSmartReportInput input() {
        return new RequesterSmartReportInput(
                "Flickering light", "The ceiling light flickers intermittently.",
                "Room 4", "Electrical", "High");
    }

    private static SmartReportResponse success() {
        return new SmartReportResponse.Success(new SmartReportResult(
                "Flickering ceiling light",
                "The ceiling light flickers intermittently during normal use.",
                "Room 4",
                "Electrical",
                "High",
                List.of("Does the issue affect other lights?"),
                "The draft preserves the reported symptoms."));
    }

    private static SmartReportFailureCode failure(SmartReportResponse response) {
        return assertInstanceOf(SmartReportResponse.Failure.class, response).code();
    }

    @Test void editFormVersionMismatchStopsBeforeAdmissionTelemetryAndContext() {
        var repository = new FakeRepository();
        var telemetry = new RecordingTelemetry();
        var calls = new AtomicInteger();
        SmartReportAssistant assistant = context -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(success());
        };
        var response = await(service(AccountRole.REQUESTER, repository, assistant, telemetry, coordinator(5))
                .requestEdit("FF-000042", input(), 2L));
        assertEquals(SmartReportFailureCode.DISCARDED_ACCESS_CHANGED, failure(response));
        assertEquals(0, repository.contextLoads.get());
        assertNull(telemetry.feature);
        assertEquals(0, calls.get());
    }

    private static SmartReportResponse await(CompletionStage<SmartReportResponse> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class FakeRepository implements RequesterSmartReportRepository {
        private final AtomicInteger createProbes = new AtomicInteger();
        private final AtomicInteger editProbes = new AtomicInteger();
        private final AtomicInteger contextLoads = new AtomicInteger();
        private final AtomicInteger rechecks = new AtomicInteger();
        private boolean editEligible = true;
        private boolean stillEligible = true;
        private long observedSessionVersion = Long.MIN_VALUE;

        @Override
        public Optional<Eligibility> probeCreate(CurrentAccountSnapshot requester) {
            createProbes.incrementAndGet();
            return Optional.of(new Eligibility(requester.id(), null, null, null));
        }

        @Override
        public Optional<Eligibility> probeEdit(String displayId, CurrentAccountSnapshot requester) {
            editProbes.incrementAndGet();
            return editEligible
                    ? Optional.of(new Eligibility(requester.id(), REQUEST_ID, displayId, 3L))
                    : Optional.empty();
        }

        @Override
        public ContextSnapshot loadContext(Eligibility eligibility, RequesterSmartReportInput input) {
            contextLoads.incrementAndGet();
            List<CategorySnapshot> categories = List.of(new CategorySnapshot(CATEGORY_ID, "Electrical", 2));
            SmartReportContext context = input.toContext(categories.stream().map(CategorySnapshot::name).toList());
            return new ContextSnapshot(eligibility, context, categories);
        }

        @Override
        public boolean isStillEligible(
                ContextSnapshot context, CurrentAccountSnapshot requester, SmartReportResult result) {
            rechecks.incrementAndGet();
            observedSessionVersion = requester.sessionVersion();
            return stillEligible;
        }
    }

    private static final class RecordingTelemetry implements AiInvocationTelemetry {
        private Feature feature;
        private Status status;

        @Override
        public Invocation start(StartRequest request) {
            feature = request.feature();
            return new Invocation(UUID.randomUUID(), NOW);
        }

        @Override
        public void finish(Invocation invocation, Terminal terminal) {
            status = terminal.status();
        }
    }

    private static final class ReadyLifecycle extends AiProviderLifecycle.Inert {
        private ReadyLifecycle() {
            super(State.READY);
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }
}
