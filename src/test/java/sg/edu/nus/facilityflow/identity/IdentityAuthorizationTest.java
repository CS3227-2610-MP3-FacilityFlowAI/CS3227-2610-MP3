package sg.edu.nus.facilityflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class IdentityAuthorizationTest {

    private static final UUID CURRENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void reqSh002RequesterOwnershipCannotBeBypassed() {
        IdentityAuthorization authorization = authorizationFor(AccountRole.REQUESTER);

        assertEquals(CURRENT_ID, authorization.requireRequesterOwner(CURRENT_ID).id());
        assertThrows(AccessDeniedException.class,
                () -> authorization.requireRequesterOwner(OTHER_ID));
    }

    @Test
    void reqSh002TechnicianAssignmentCannotBeBypassed() {
        IdentityAuthorization authorization = authorizationFor(AccountRole.TECHNICIAN);

        assertEquals(CURRENT_ID, authorization.requireAssignedTechnician(CURRENT_ID).id());
        assertThrows(AccessDeniedException.class,
                () -> authorization.requireAssignedTechnician(OTHER_ID));
    }

    @Test
    void reqSh002RoleCheckIsIndependentOfUiVisibility() {
        IdentityAuthorization authorization = authorizationFor(AccountRole.REQUESTER);

        assertThrows(AccessDeniedException.class,
                () -> authorization.requireRole(AccountRole.FACILITIES_MANAGER));
    }

    private static IdentityAuthorization authorizationFor(AccountRole role) {
        CurrentAccount account = new CurrentAccount(CURRENT_ID, "current", "Current User", role, 0);
        return new IdentityAuthorization(() -> account);
    }
}
