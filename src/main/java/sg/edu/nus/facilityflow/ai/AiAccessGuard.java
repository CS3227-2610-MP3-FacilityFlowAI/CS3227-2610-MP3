package sg.edu.nus.facilityflow.ai;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

/**
 * Enforces authorization before protected context construction or any model call.
 * AI endpoints must enter through the matching method (AI-SEC-001, REQ-SH-018).
 */
@Component
public class AiAccessGuard {

    private final IdentityAuthorization authorization;

    public AiAccessGuard(IdentityAuthorization authorization) {
        this.authorization = authorization;
    }

    public <C, R> R forRequester(
            UUID requesterOwnerId,
            Supplier<C> authorizedContextFactory,
            Function<C, R> modelCall) {
        authorization.requireRequesterOwner(requesterOwnerId);
        return modelCall.apply(authorizedContextFactory.get());
    }

    public <C, R> R forTechnician(
            UUID assignedTechnicianId,
            Supplier<C> authorizedContextFactory,
            Function<C, R> modelCall) {
        authorization.requireAssignedTechnician(assignedTechnicianId);
        return modelCall.apply(authorizedContextFactory.get());
    }

    /** Uses the role/session snapshot captured before an asynchronous operation began. */
    public <C, R> R forTechnician(
            CurrentAccountSnapshot accountSnapshot,
            UUID assignedTechnicianId,
            Supplier<C> authorizedContextFactory,
            Function<C, R> modelCall) {
        if (accountSnapshot.role() != AccountRole.TECHNICIAN
                || !accountSnapshot.id().equals(assignedTechnicianId)) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return modelCall.apply(authorizedContextFactory.get());
    }

    public <C, R> R forManager(
            Supplier<C> authorizedContextFactory,
            Function<C, R> modelCall) {
        authorization.requireRole(AccountRole.FACILITIES_MANAGER);
        return modelCall.apply(authorizedContextFactory.get());
    }
}
