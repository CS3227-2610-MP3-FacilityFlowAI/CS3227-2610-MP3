package sg.edu.nus.facilityflow.ai.technician;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.ai.AiAccessGuard;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

/** Requirement test for the role-owned authorization boundary around the assistant. */
class WorkPlanAuthorizationBoundaryTest {
    private static final UUID CURRENT_TECHNICIAN = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TECHNICIAN = UUID.fromString("31000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("AI-SEC-001/002/REQ-TECH-012 denied technician cannot build context or call assistant")
    void deniedTechnicianCannotBuildContextOrCallAssistant() {
        AtomicBoolean contextBuilt = new AtomicBoolean();
        AtomicInteger assistantCalls = new AtomicInteger();
        AiAccessGuard guard = new AiAccessGuard(new IdentityAuthorization(() -> new CurrentAccount(
                CURRENT_TECHNICIAN, "technician", "Technician", AccountRole.TECHNICIAN, 0)));
        WorkPlanAssistant assistant = context -> {
            assistantCalls.incrementAndGet();
            return CompletableFuture.completedFuture(WorkPlanResponse.failure(
                    WorkPlanFailureCode.AI_UNAVAILABLE));
        };

        assertThrows(AccessDeniedException.class, () -> guard.forTechnician(
                OTHER_TECHNICIAN,
                () -> {
                    contextBuilt.set(true);
                    return minimalContext();
                },
                context -> assistant.request(context)));

        assertFalse(contextBuilt.get());
        assertFalse(assistantCalls.get() > 0);
    }

    private static WorkPlanContext minimalContext() {
        return new WorkPlanContext(
                UUID.fromString("31000000-0000-0000-0000-000000000003"),
                "REQ-1",
                CURRENT_TECHNICIAN,
                "Title",
                "Description",
                "Location",
                "Mechanical",
                "High",
                "Normal",
                "ASSIGNED",
                1,
                java.util.List.of());
    }
}
