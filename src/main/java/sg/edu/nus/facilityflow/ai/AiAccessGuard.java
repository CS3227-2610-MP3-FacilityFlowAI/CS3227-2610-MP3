package sg.edu.nus.facilityflow.ai;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.identity.AccountRole;
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

    public <C, R> R forManager(
            Supplier<C> authorizedContextFactory,
            Function<C, R> modelCall) {
        authorization.requireRole(AccountRole.FACILITIES_MANAGER);
        return modelCall.apply(authorizedContextFactory.get());
    }
}
