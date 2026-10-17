package sg.edu.nus.facilityflow.identity;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Server-side use-case authorization for REQ-SH-002 and AI-SEC-001.
 */
@Component
public class IdentityAuthorization {

    private final CurrentAccountProvider currentAccounts;

    public IdentityAuthorization(CurrentAccountProvider currentAccounts) {
        this.currentAccounts = currentAccounts;
    }

    public CurrentAccount requireRole(AccountRole expectedRole) {
        CurrentAccount account = currentAccounts.requireCurrentAccount();
        if (account.role() != expectedRole) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return account;
    }

    /** Captures role and session identity before work may complete asynchronously. */
    public CurrentAccountSnapshot requireRoleSnapshot(AccountRole expectedRole) {
        CurrentAccountSnapshot snapshot = currentAccounts.requireCurrentAccountSnapshot();
        if (snapshot.role() != expectedRole) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return snapshot;
    }

    public CurrentAccount requireRequesterOwner(UUID requesterOwnerId) {
        CurrentAccount account = requireRole(AccountRole.REQUESTER);
        if (!account.id().equals(requesterOwnerId)) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return account;
    }

    public CurrentAccount requireAssignedTechnician(UUID assignedTechnicianId) {
        CurrentAccount account = requireRole(AccountRole.TECHNICIAN);
        if (!account.id().equals(assignedTechnicianId)) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return account;
    }
}
