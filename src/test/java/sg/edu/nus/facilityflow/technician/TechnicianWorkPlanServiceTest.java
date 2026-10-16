package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
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
                new CurrentAccount(TECHNICIAN_ID, "requester", "Requester", AccountRole.REQUESTER),
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
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN),
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
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN),
                repository,
                assistant);

        WorkPlanResponse response = await(service.request("FF-000001"));
        assertEquals(WorkPlanFailureCode.DISCARDED_ACCESS_CHANGED, failureCode(response));
        assertEquals(1, repository.loadCalls.get());
        assertEquals(1, assistant.calls.get());
    }

    @Test
    @DisplayName("AI-SEC-028 principal reassignment during provider call discards the completed plan")
    void principalSwapDuringProviderCallDiscardsSuccess() {
        AtomicReference<CurrentAccount> account = new AtomicReference<>(
                new CurrentAccount(TECHNICIAN_ID, "technician", "Technician", AccountRole.TECHNICIAN));
        IdentityAuthorization authorization = new IdentityAuthorization(account::get);
        CountingRepository repository = new CountingRepository();
        CompletableFuture<WorkPlanResponse> provider = new CompletableFuture<>();
        WorkPlanAssistant assistant = ignored -> provider;
        TechnicianWorkPlanService service = new TechnicianWorkPlanService(
                authorization,
                new AiAccessGuard(authorization),
                new ReadyLifecycle(),
                repository,
                assistant);

        CompletionStage<WorkPlanResponse> response = service.request("FF-000001");
        account.set(new CurrentAccount(
                UUID.fromString("32000000-0000-0000-0000-000000000099"),
                "other-technician", "Other Technician", AccountRole.TECHNICIAN));
        provider.complete(new CountingAssistant().request(context()).toCompletableFuture().join());

        assertEquals(WorkPlanFailureCode.DISCARDED_ACCESS_CHANGED, failureCode(await(response)));
    }

    private static TechnicianWorkPlanService serviceFor(
            CurrentAccount account, CountingRepository repository, CountingAssistant assistant) {
        IdentityAuthorization authorization = new IdentityAuthorization(() -> account);
        return new TechnicianWorkPlanService(
                authorization,
                new AiAccessGuard(authorization),
                new ReadyLifecycle(),
                repository,
                assistant);
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
        private boolean stillEligible = true;

        @Override
        public Optional<WorkPlanContext> loadEligible(String displayId, UUID technicianId) {
            loadCalls.incrementAndGet();
            return Optional.of(context());
        }

        @Override
        public boolean isStillEligible(WorkPlanContext context) {
            return stillEligible;
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
