package sg.edu.nus.facilityflow.technician;

import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Server-side context seam; callers never provide the Request data sent to the model. */
public interface TechnicianWorkPlanRepository {
    Optional<WorkPlanContext> loadEligible(String displayId, UUID technicianId);

    boolean isStillEligible(WorkPlanContext context);

    /** Rechecks object access plus the captured authenticated session snapshot. */
    default boolean isStillEligible(WorkPlanContext context, CurrentAccountSnapshot accountSnapshot) {
        return isStillEligible(context);
    }
}
