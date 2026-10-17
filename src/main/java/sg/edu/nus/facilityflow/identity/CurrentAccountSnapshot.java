package sg.edu.nus.facilityflow.identity;

import java.util.Objects;
import java.util.UUID;

/** Immutable authorization snapshot retained across asynchronous AI work (AI-SEC-028). */
public record CurrentAccountSnapshot(UUID id, AccountRole role, long sessionVersion) {
    public CurrentAccountSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        if (sessionVersion < -1) {
            throw new IllegalArgumentException("sessionVersion cannot be below -1");
        }
    }
}
