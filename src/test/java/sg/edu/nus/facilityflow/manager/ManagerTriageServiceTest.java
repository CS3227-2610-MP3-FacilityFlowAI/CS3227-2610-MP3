package sg.edu.nus.facilityflow.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import sg.edu.nus.facilityflow.ai.manager.TriageAssistant;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

class ManagerTriageServiceTest {
    private static final UUID MANAGER_ID = UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("43000000-0000-0000-0000-000000000002");
    private static final UUID TECHNICIAN_ID = UUID.fromString("43000000-0000-0000-0000-000000000003");
    private static final UUID CATEGORY_ID = UUID.fromString("43000000-0000-0000-0000-000000000004");
    private static final UUID SKILL_ID = UUID.fromString("43000000-0000-0000-0000-000000000005");
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    @Test
    @DisplayName("MGR-A08/AI-SEC-001 valid triage returns typed facts and deterministic candidate ranking")
    void authorizedTriageRanksCandidatesWithoutMutation() {
        FakeRepository repository = new FakeRepository();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        AtomicInteger calls = new AtomicInteger();
        TriageAssistant assistant = context -> {
            calls.incrementAndGet();
            assertEquals(List.of("Electrical"), context.activeSkillLabels());
            return CompletableFuture.completedFuture(new TriageResponse.Success(result()));
        };

        ManagerTriageResponse response = await(service(
                AccountRole.FACILITIES_MANAGER, repository, assistant, telemetry, coordinator(5))
                .request("FF-000051"));

        ManagerTriageResponse.Success success = assertInstanceOf(ManagerTriageResponse.Success.class, response);
        assertEquals("HVAC", success.result().suggestedCategory());
        assertEquals(List.of(TECHNICIAN_ID),
                success.ranking().rankedCandidates().stream().map(TechnicianCandidate::accountId).toList());
        assertEquals(1, repository.rechecks.get());
        assertEquals(1, calls.get());
        assertEquals(AiInvocationTelemetry.Feature.TRIAGE, telemetry.feature);
        assertEquals(AiInvocationTelemetry.Status.SUCCEEDED, telemetry.status);
    }

    @Test
    @DisplayName("MGR-A09 wrong role is denied before triage context or provider use")
    void wrongRoleIsDeniedBeforeProtectedWork() {
        FakeRepository repository = new FakeRepository();
        AtomicInteger calls = new AtomicInteger();
        ManagerTriageService service = service(AccountRole.REQUESTER, repository,
                context -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(new TriageResponse.Success(result()));
                }, AiInvocationTelemetry.noop(), coordinator(5));

        assertThrows(AccessDeniedException.class, () -> service.request("FF-000051"));
        assertEquals(0, repository.probes.get());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("MGR-A10 invalid or ineligible triage fails before context/provider use")
    void invalidAndIneligibleRequestsFailClosed() {
        FakeRepository repository = new FakeRepository();
        repository.eligible = false;
        AtomicInteger calls = new AtomicInteger();
        ManagerTriageService service = service(AccountRole.FACILITIES_MANAGER, repository,
                context -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(new TriageResponse.Success(result()));
                }, AiInvocationTelemetry.noop(), coordinator(5));

        assertEquals(TriageFailureCode.AI_INVALID_REQUEST, failure(await(service.request("not-a-request"))));
        assertEquals(TriageFailureCode.AI_UNAVAILABLE, failure(await(service.request("FF-000051"))));
        assertEquals(1, repository.probes.get());
        assertEquals(0, repository.contextLoads.get());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("MGR-A11 stale Request or catalogue state discards the complete triage result")
    void staleAsyncSuccessIsDiscarded() {
        FakeRepository repository = new FakeRepository();
        repository.stillEligible = false;
        CompletableFuture<TriageResponse> provider = new CompletableFuture<>();
        ManagerTriageService service = service(AccountRole.FACILITIES_MANAGER, repository,
                context -> provider, AiInvocationTelemetry.noop(), coordinator(5));

        CompletionStage<ManagerTriageResponse> stage = service.request("FF-000051");
        provider.complete(new TriageResponse.Success(result()));

        assertEquals(TriageFailureCode.DISCARDED_ACCESS_CHANGED, failure(await(stage)));
        assertEquals(7, repository.observedSessionVersion);
    }

    @Test
    @DisplayName("AI-SEC-008 admission rejection occurs before catalogue context loading")
    void admissionRejectsBeforeContext() {
        FakeRepository repository = new FakeRepository();
        ManagerTriageResponse response = await(service(AccountRole.FACILITIES_MANAGER, repository,
                context -> CompletableFuture.completedFuture(new TriageResponse.Success(result())),
                AiInvocationTelemetry.noop(), coordinator(0)).request("FF-000051"));

        assertEquals(TriageFailureCode.AI_LIMITED, failure(response));
        assertEquals(1, repository.probes.get());
        assertEquals(0, repository.contextLoads.get());
    }

    private static ManagerTriageService service(
            AccountRole role,
            FakeRepository repository,
            TriageAssistant assistant,
            AiInvocationTelemetry telemetry,
            AiInvocationCoordinator coordinator) {
        CurrentAccount account = new CurrentAccount(MANAGER_ID, "manager", "Manager", role, 0);
        CurrentAccountSnapshot snapshot = new CurrentAccountSnapshot(MANAGER_ID, role, 7);
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
        return new ManagerTriageService(authorization, new AiAccessGuard(authorization), coordinator,
                new ReadyLifecycle(), repository, assistant, new TriageCandidateRanker(), telemetry,
                configuration(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiInvocationCoordinator coordinator(int maxInFlight) {
        return new AiInvocationCoordinator(maxInFlight, 5, 30, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiConfiguration.Available configuration() {
        return new AiConfiguration.Available(
                URI.create("https://soclaas-api.comp.nus.edu.sg/v1"),
                "synthetic-key", "synthetic-model", 30, 800, 5, 5, 30);
    }

    private static TriageResult result() {
        return new TriageResult("HVAC", "High", List.of("Confirm access."),
                List.of("Electrical"), List.of(), "The request suggests an HVAC inspection.");
    }

    private static TriageFailureCode failure(ManagerTriageResponse response) {
        return assertInstanceOf(ManagerTriageResponse.Failure.class, response).code();
    }

    private static ManagerTriageResponse await(CompletionStage<ManagerTriageResponse> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class FakeRepository implements ManagerTriageRepository {
        private final AtomicInteger probes = new AtomicInteger();
        private final AtomicInteger contextLoads = new AtomicInteger();
        private final AtomicInteger rechecks = new AtomicInteger();
        private boolean eligible = true;
        private boolean stillEligible = true;
        private long observedSessionVersion = Long.MIN_VALUE;

        @Override
        public Optional<Eligibility> probe(String displayId, CurrentAccountSnapshot manager) {
            probes.incrementAndGet();
            return eligible ? Optional.of(new Eligibility(REQUEST_ID, displayId, 3)) : Optional.empty();
        }

        @Override
        public ContextSnapshot loadContext(Eligibility eligibility) {
            contextLoads.incrementAndGet();
            List<CatalogueSnapshot> categories = List.of(new CatalogueSnapshot(CATEGORY_ID, "HVAC", 2));
            List<CatalogueSnapshot> skills = List.of(new CatalogueSnapshot(SKILL_ID, "Electrical", 1));
            TriageContext context = new TriageContext(
                    "Leaking air conditioner", "Water is pooling below the unit.", "Block A",
                    "HVAC", "High", null, "OPEN", List.of("The leak worsened today."),
                    List.of("HVAC"), List.of("Electrical"));
            TechnicianCandidate candidate = new TechnicianCandidate(
                    TECHNICIAN_ID, "tech-one", true, Set.of("Electrical"), 1);
            return new ContextSnapshot(eligibility, context, categories, skills,
                    List.of(new TechnicianSnapshot(candidate)));
        }

        @Override
        public boolean isStillEligible(
                ContextSnapshot context, CurrentAccountSnapshot manager, TriageResult result) {
            rechecks.incrementAndGet();
            observedSessionVersion = manager.sessionVersion();
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
