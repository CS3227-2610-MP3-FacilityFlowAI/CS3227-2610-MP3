package sg.edu.nus.facilityflow.technician;

import java.util.Optional;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Server-side context seam; callers never provide the Request data sent to the model. */
public interface TechnicianWorkPlanRepository {
    Optional<WorkPlanContext> loadEligible(String displayId, CurrentAccountSnapshot account);

    /** Rechecks the exact Request state plus the captured authenticated session snapshot. */
    boolean isStillEligible(WorkPlanContext context, CurrentAccountSnapshot account);
}
