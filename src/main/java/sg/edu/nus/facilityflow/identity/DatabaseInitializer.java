package sg.edu.nus.facilityflow.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** REQ-SH-015–017: explicit, atomic first initialization; never ordinary account creation. */
@Component
@Profile("database-initialize")
public class DatabaseInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Environment environment;

    public DatabaseInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager, Environment environment) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean created = initialize();
        LOGGER.info(created ? "Database initialization complete." : "Database already initialized; no changes made.");
    }

    boolean initialize() {
        boolean demoEnabled = environment.getProperty("DEMO_DATA_ENABLED", Boolean.class, false);
        if (demoEnabled && !environment.matchesProfiles("development & !production & !prod")) {
            throw new IllegalStateException("Demo initialization requires the development profile and no production profile.");
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            // Bound competing initializer waits without changing pooled session settings.
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(322726100003)", Object.class);
            if (jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class) != 0) {
                return false;
            }
            if (jdbc.queryForObject("SELECT (SELECT count(*) FROM facilityflow.maintenance_request) "
                    + "+ (SELECT count(*) FROM facilityflow.audit_event) + (SELECT count(*) FROM facilityflow.skill_tag) "
                    + "+ (SELECT count(*) FROM facilityflow.ai_invocation) + (SELECT count(*) FROM facilityflow.spring_session) "
                    + "+ (SELECT abs(count(*) - 7) FROM facilityflow.category) "
                    + "+ (SELECT count(*) FROM facilityflow.category WHERE NOT active OR version <> 0 "
                    + "OR name NOT IN ('Electrical', 'Plumbing', 'HVAC', 'Structural', 'Cleaning', 'Safety', 'Other'))", Long.class) != 0) {
                throw new IllegalStateException("Existing application data prevents initialization.");
            }
            if (demoEnabled) {
                seedDemo();
            } else {
                var manager = createAccount(required("BOOTSTRAP_MANAGER_USERNAME"), required("BOOTSTRAP_MANAGER_DISPLAY_NAME"),
                        required("BOOTSTRAP_MANAGER_PASSWORD"), "FACILITIES_MANAGER");
                audit(manager, "ACCOUNT_CREATED", "ACCOUNT", manager);
            }
            return true;
        }));
    }

    private String required(String name) {
        var value = environment.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException("Required bootstrap configuration is missing: " + name);
        }
        return value;
    }

    private UUID createAccount(String username, String displayName, String password, String role) {
        username = username.strip();
        displayName = displayName.strip();
        if (!username.matches("[A-Za-z0-9._-]{3,32}") || displayName.codePointCount(0, displayName.length()) < 2
                || displayName.codePointCount(0, displayName.length()) > 80 || displayName.isBlank()) {
            throw new IllegalArgumentException("Invalid bootstrap Account name.");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 24) {
            throw new IllegalArgumentException("Bootstrap password must contain 8–24 Unicode code points.");
        }
        var hash = "{pbkdf2@SpringSecurity_v5_8}" + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(password);
        return jdbc.queryForObject("INSERT INTO facilityflow.user_account (username, display_name, password_hash, role) "
                + "VALUES (?, ?, ?, ?) RETURNING id", UUID.class, username, displayName, hash, role);
    }

    private void audit(UUID actor, String action, String targetType, UUID target) {
        jdbc.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details) "
                + "VALUES (?, ?, ?, ?, jsonb_build_object('reason', 'Explicit initial database setup'))",
                actor, action, targetType, target);
    }

    private void seedDemo() {
        var manager1 = createAccount("manager1", "Demo Manager 1", "Welcome123", "FACILITIES_MANAGER");
        var manager2 = createAccount("manager2", "Demo Manager 2", "Welcome123", "FACILITIES_MANAGER");
        var requester1 = createAccount("requester1", "Demo Requester 1", "Welcome123", "REQUESTER");
        var requester2 = createAccount("requester2", "Demo Requester 2", "Welcome123", "REQUESTER");
        var technician1 = createAccount("technician1", "Demo Technician 1", "Welcome123", "TECHNICIAN");
        var technician2 = createAccount("technician2", "Demo Technician 2", "Welcome123", "TECHNICIAN");
        for (var account : new UUID[]{manager1, manager2, requester1, requester2, technician1, technician2}) {
            audit(manager1, "ACCOUNT_CREATED", "ACCOUNT", account);
        }
        var states = new String[]{"OPEN", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED"};
        for (int i = 0; i < states.length; i++) {
            seedRequest(i % 2 == 0 ? requester1 : requester2, i % 2 == 0 ? technician1 : technician2, manager1, states[i]);
        }
    }

    private void seedRequest(UUID owner, UUID technician, UUID manager, String state) {
        var request = jdbc.queryForObject("INSERT INTO facilityflow.maintenance_request "
                + "(requester_id, recorded_by, title, description, location, category_id, reported_urgency) "
                + "VALUES (?, ?, ?, 'Synthetic maintenance issue for development testing.', 'Block A', "
                + "(SELECT id FROM facilityflow.category WHERE name = 'Electrical'), 'NORMAL') RETURNING id",
                UUID.class, owner, owner, "Demo " + state + " maintenance");
        audit(owner, "REQUEST_CREATED", "REQUEST", request);
        if (state.equals("OPEN")) return;
        if (state.equals("CANCELLED")) {
            jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", request);
            audit(owner, "REQUEST_CANCELLED", "REQUEST", request);
            return;
        }
        jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED', manager_priority = 'MEDIUM', "
                + "assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", technician, request);
        audit(manager, "REQUEST_ASSIGNED", "REQUEST", request);
        if (state.equals("ASSIGNED")) return;
        jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'IN_PROGRESS', updated_at = CURRENT_TIMESTAMP WHERE id = ?", request);
        audit(technician, "REQUEST_STARTED", "REQUEST", request);
        var log = jdbc.queryForObject("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) "
                + "VALUES (?, ?, 'Synthetic inspection completed safely', 15) RETURNING id", UUID.class, request, technician);
        audit(technician, "WORK_LOG_CREATED", "WORK_LOG", log);
        if (state.equals("IN_PROGRESS")) return;
        jdbc.update("INSERT INTO facilityflow.request_completion_history (request_id, technician_id, resolution_summary) "
                + "VALUES (?, ?, 'Synthetic repair completed and verified')", request, technician);
        jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'COMPLETED', completed_by = ?, "
                + "completed_at = CURRENT_TIMESTAMP, resolution_summary = 'Synthetic repair completed and verified', updated_at = CURRENT_TIMESTAMP WHERE id = ?", technician, request);
        audit(technician, "REQUEST_COMPLETED", "REQUEST", request);
        if (state.equals("CLOSED")) {
            jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", request);
            audit(manager, "REQUEST_CLOSED", "REQUEST", request);
        }
    }
}
