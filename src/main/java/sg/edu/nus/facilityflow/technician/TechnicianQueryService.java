package sg.edu.nus.facilityflow.technician;

import java.util.Objects;
import org.springframework.stereotype.Service;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

/** Assigned-only use cases; principal and persisted account checks precede Request reads. */
@Service
public class TechnicianQueryService {
    private final IdentityAuthorization authorization;
    private final TechnicianQueryRepository repository;

    public TechnicianQueryService(IdentityAuthorization authorization, TechnicianQueryRepository repository) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public QueueView queue(QueueFilter filter) {
        CurrentAccountSnapshot actor = authorization.requireRoleSnapshot(AccountRole.TECHNICIAN);
        if (filter == null) {
            throw new TechnicianQueryException(TechnicianQueryException.Code.INVALID_FILTER);
        }
        return repository.readSnapshot(session -> {
            session.requireCurrentTechnician(actor);
            return new QueueView(filter, session.counts(actor.id()), session.categories(actor.id()),
                    session.assignedTotal(actor.id()), session.queue(actor.id(), filter));
        });
    }

    public RequestDetail detail(String displayId) {
        CurrentAccountSnapshot actor = authorization.requireRoleSnapshot(AccountRole.TECHNICIAN);
        return repository.readSnapshot(session -> {
            session.requireCurrentTechnician(actor);
            if (displayId == null || !displayId.matches("FF-[0-9]{6}") || displayId.equals("FF-000000")) {
                throw new TechnicianQueryException(TechnicianQueryException.Code.NOT_AVAILABLE);
            }
            return session.detail(actor.id(), displayId).orElseThrow(
                    () -> new TechnicianQueryException(TechnicianQueryException.Code.NOT_AVAILABLE));
        });
    }
}
