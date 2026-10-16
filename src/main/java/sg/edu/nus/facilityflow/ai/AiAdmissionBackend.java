package sg.edu.nus.facilityflow.ai;

import java.util.Optional;
import java.util.UUID;

/** Internal seam for durable multi-instance AI admission. */
interface AiAdmissionBackend {
    Optional<Lease> tryAcquire(UUID accountId);

    interface Lease extends AutoCloseable {
        void release();

        default boolean valid() {
            return true;
        }

        @Override
        default void close() {
            release();
        }
    }
}
