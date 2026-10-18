package sg.edu.nus.facilityflow.web;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Non-sensitive application/database readiness probe for NFR-OPS-001. */
@RestController
public final class HealthController {
    private static final Map<String, String> UP = Map.of("status", "UP");
    private static final Map<String, String> DOWN = Map.of("status", "DOWN");

    private final JdbcOperations jdbc;

    public HealthController(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    ResponseEntity<Map<String, String>> health() {
        try {
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UP);
            }
        } catch (DataAccessException unavailable) {
            // The public response deliberately omits provider and connection details.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore()).body(DOWN);
    }
}
