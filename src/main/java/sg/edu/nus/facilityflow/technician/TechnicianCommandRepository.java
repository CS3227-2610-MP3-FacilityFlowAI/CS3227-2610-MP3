package sg.edu.nus.facilityflow.technician;

import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Atomic, assigned-only operations for REQ-TECH-004–006/010–011. */
public interface TechnicianCommandRepository {
    void startWork(CurrentAccountSnapshot actor, String displayId, long expectedVersion);

    void appendWorkLog(CurrentAccountSnapshot actor, String displayId, long expectedVersion,
            String note, int minutes);
}
