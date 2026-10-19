package sg.edu.nus.facilityflow.ai;

import java.util.UUID;

/** Persistence seam for shared admission and redacted invocation evidence. */
public interface AiOperations {
    record Permit(UUID slotId, long version) { }
    Permit acquire(UUID actor, int timeoutSeconds);
    void release(Permit permit);
}
