package sg.edu.nus.facilityflow.identity;

import java.time.Instant;
import java.util.UUID;

public record Account(
        UUID id,
        String username,
        String displayName,
        String passwordHash,
        AccountRole role,
        boolean active,
        long sessionVersion,
        Instant createdAt,
        Instant updatedAt) {
}
