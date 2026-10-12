package sg.edu.nus.facilityflow.identity;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findByNormalizedUsername(String normalizedUsername);

    Optional<Account> findById(UUID id);
}
