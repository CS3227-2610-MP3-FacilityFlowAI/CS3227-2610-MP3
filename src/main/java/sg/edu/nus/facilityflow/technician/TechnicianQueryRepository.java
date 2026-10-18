package sg.edu.nus.facilityflow.technician;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

/** Transaction-scoped read capability for REQ-SH-002 and REQ-TECH-001–003/009. */
public interface TechnicianQueryRepository {
    <T> T readSnapshot(Function<ReadSession, T> reader);

    /** Valid only on the callback thread and until the callback returns or fails. */
    interface ReadSession {
        void requireCurrentTechnician(CurrentAccountSnapshot actor);
        DashboardCounts counts(UUID actorId);
        long assignedTotal(UUID actorId);
        List<CategoryOption> categories(UUID actorId);
        List<QueueRow> queue(UUID actorId, QueueFilter filter);
        Optional<RequestDetail> detail(UUID actorId, String displayId);
    }
}
