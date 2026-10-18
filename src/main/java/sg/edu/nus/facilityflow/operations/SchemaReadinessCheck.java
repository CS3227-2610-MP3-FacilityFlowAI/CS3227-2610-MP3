package sg.edu.nus.facilityflow.operations;

import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TECHNICAL-SPEC: web startup checks the migrated schema without performing DDL. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SchemaReadinessCheck implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public SchemaReadinessCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            var versions = jdbc.query("SELECT version, success FROM facilityflow.schema_version",
                    (row, index) -> row.getBoolean("success") ? row.getString("version") : "failed");
            if (versions.size() != 6 || !Set.copyOf(versions).equals(Set.of("1", "2", "3", "4", "5", "6"))) {
                throw new IllegalStateException("Database schema is not supported. Run the approved migration process before startup.");
            }
        } catch (DataAccessException exception) {
            throw new IllegalStateException("Database schema is not ready. Check connectivity and apply the approved migrations.");
        }
    }
}
