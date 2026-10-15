package sg.edu.nus.facilityflow.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TECHNICAL-SPEC connection smoke check; does not create or change data. */
@Component
@Profile("database-check")
public class DatabaseConnectionCheck implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConnectionCheck.class);
    private final JdbcTemplate jdbc;

    public DatabaseConnectionCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            if (!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class))) {
                throw new IllegalStateException("Database connection check returned an unexpected result.");
            }
        } catch (DataAccessException exception) {
            // Provider errors can contain connection details; return only safe guidance.
            throw new IllegalStateException("Database connection check failed. Check database settings, network and SSL certificate.");
        }
        LOGGER.info("Database connection verified.");
    }
}
