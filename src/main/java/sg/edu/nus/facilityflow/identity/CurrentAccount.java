package sg.edu.nus.facilityflow.identity;

import java.util.UUID;

public record CurrentAccount(UUID id, String username, String displayName, AccountRole role, long sessionVersion) {
}
