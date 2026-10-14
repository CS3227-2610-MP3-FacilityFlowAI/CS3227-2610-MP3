package sg.edu.nus.facilityflow.database;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.util.UUID;
import java.sql.SQLException;
import sg.edu.nus.facilityflow.FacilityFlowApplication;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationTest {
    private static EmbeddedPostgres postgres;
    private static JdbcTemplate admin;
    private static JdbcTemplate app;
    private static Flyway flyway;

    @BeforeAll
    static void createIsolatedDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        var adminDataSource = postgres.getPostgresDatabase();
        admin = new JdbcTemplate(adminDataSource);
        try (var connection = adminDataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        // Trust authentication is confined to this disposable loopback test instance.
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        var configuration = Flyway.configure().dataSource(dataSource("facilityflow_migrator"))
                .schemas("facilityflow").defaultSchema("facilityflow")
                .cleanDisabled(true).ignoreMigrationPatterns(new String[0]);
        assertEquals(1, configuration.target("1").load().migrate().migrationsExecuted);
        flyway = configuration.target("3").load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        app = new JdbcTemplate(dataSource("facilityflow_app"));
    }

    private static DataSource dataSource(String role) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser(role);
        return source;
    }

    @AfterAll
    static void closeDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    @DisplayName("TECHNICAL-SPEC: migrations rerun without duplicates and reject newer schema")
    void validatesMigrationHistory() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.user_account WHERE username LIKE 'requester%'", Integer.class));
        admin.update("INSERT INTO facilityflow.flyway_schema_history "
                + "(installed_rank, version, description, type, script, installed_by, execution_time, success) "
                + "VALUES (99, '99', 'synthetic newer schema', 'SQL', 'V99__test.sql', 'postgres', 0, true)");
        try {
            assertFalse(flyway.validateWithResult().validationSuccessful);
        } finally {
            admin.update("DELETE FROM facilityflow.flyway_schema_history WHERE installed_rank = 99");
        }
    }

    @Test
    @DisplayName("REQ-SH-001 / TECHNICAL-SPEC: account uniqueness, Unicode and role constraints")
    void enforcesAccountConstraints() {
        insertAccount("CaseCheck", "😀😀", "REQUESTER");
        assertSqlState("23505", () -> insertAccount("casecheck", "Name", "REQUESTER"));
        assertSqlState("23514", () -> insertAccount("bad name", "Name", "REQUESTER"));
        assertSqlState("23514", () -> insertAccount("BadRole", "Name", "ADMIN"));
        assertSqlState("23514", () -> insertAccount("BlankName", "  ", "REQUESTER"));
        assertSqlState("23514", () -> insertAccount("LongName", "😀".repeat(81), "REQUESTER"));
    }

    private static void assertSqlState(String state, Executable command) {
        var failure = assertThrows(DataAccessException.class, command);
        assertEquals(state, assertInstanceOf(SQLException.class, failure.getMostSpecificCause()).getSQLState());
    }

    private UUID insertAccount(String username, String displayName, String role) {
        return app.queryForObject("INSERT INTO facilityflow.user_account "
                        + "(username, display_name, password_hash, role) VALUES (?, ?, ?, ?) RETURNING id",
                UUID.class, username, displayName, "synthetic-hash-not-a-real-password", role);
    }

    @Test
    @DisplayName("REQ-SH-006–007 / NFR-SEC-001: audit append-only and runtime schema restrictions")
    void restrictsRuntimePermissions() {
        var actor = insertAccount("AuditActor", "Audit Actor", "FACILITIES_MANAGER");
        var event = app.queryForObject("INSERT INTO facilityflow.audit_event "
                        + "(actor_id, action, target_type, target_id) VALUES (?, 'TEST', 'ACCOUNT', ?) RETURNING id",
                UUID.class, actor, actor);
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.audit_event SET action = 'CHANGED' WHERE id = ?", event));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.audit_event WHERE id = ?", event));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.user_account WHERE id = ?", actor));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.user_account SET id = ? WHERE id = ?", UUID.randomUUID(), actor));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.user_account SET created_at = CURRENT_TIMESTAMP WHERE id = ?", actor));
        assertSqlState("42501", () -> app.execute("CREATE TABLE facilityflow.forbidden (id integer)"));
        assertSqlState("42501", () -> app.queryForObject("SELECT count(*) FROM facilityflow.flyway_schema_history", Integer.class));
        assertSqlState("23514", () -> app.update("INSERT INTO facilityflow.audit_event "
                + "(actor_id, action, target_type, target_id, details) VALUES (?, 'TEST', 'ACCOUNT', ?, '[]')", actor, actor));
        assertEquals("TEST", app.queryForObject("SELECT action FROM facilityflow.audit_event WHERE id = ?", String.class, event));
    }

    @Test
    @DisplayName("REQ-SH-014: account and audit writes roll back together on audit failure")
    void rollsBackBusinessAndAuditWrites() throws Exception {
        var id = UUID.randomUUID();
        try (var connection = dataSource("facilityflow_app").getConnection()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO facilityflow.user_account "
                    + "(id, username, display_name, password_hash, role) VALUES (?, 'RollbackAccount', 'Rollback', 'test-hash', 'REQUESTER')")) {
                insert.setObject(1, id);
                insert.executeUpdate();
            }
            assertThrows(java.sql.SQLException.class, () -> {
                try (var audit = connection.prepareStatement("INSERT INTO facilityflow.audit_event "
                        + "(actor_id, action, target_type, target_id) VALUES (?, '', 'ACCOUNT', ?)")) {
                    audit.setObject(1, id);
                    audit.setObject(2, id);
                    audit.executeUpdate();
                }
            });
            connection.rollback();
        }
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.user_account WHERE id = ?", Integer.class, id));
    }

    @Test
    @DisplayName("REQ-SH-004 / TECHNICAL-SPEC: session deletion cascades to attributes")
    void deletesSessionAttributes() {
        var id = UUID.randomUUID().toString();
        app.update("INSERT INTO facilityflow.spring_session "
                + "(primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time) "
                + "VALUES (?, ?, 1, 1, -1, 9223372036854775807)", id, UUID.randomUUID().toString());
        app.update("INSERT INTO facilityflow.spring_session_attributes "
                + "(session_primary_id, attribute_name, attribute_bytes) VALUES (?, 'test', ?)", id, new byte[]{1, 2});
        app.update("DELETE FROM facilityflow.spring_session WHERE primary_id = ?", id);
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.spring_session_attributes WHERE session_primary_id = ?", Integer.class, id));
    }

    @Test
    @DisplayName("NFR-SEC-001: unrelated database role cannot access private data or bypass RLS")
    void deniesUnrelatedRole() {
        insertAccount("RlsProtected", "Private Account", "REQUESTER");
        admin.execute("CREATE ROLE unrelated_test LOGIN");
        var unrelated = new JdbcTemplate(dataSource("unrelated_test"));
        assertSqlState("42501", () -> unrelated.queryForObject("SELECT count(*) FROM facilityflow.user_account", Integer.class));
        admin.execute("GRANT USAGE ON SCHEMA facilityflow TO unrelated_test");
        admin.execute("GRANT SELECT ON facilityflow.user_account TO unrelated_test");
        assertEquals(0, unrelated.queryForObject("SELECT count(*) FROM facilityflow.user_account", Integer.class));
    }

    @Test
    @DisplayName("TECHNICAL-SPEC: explicit Spring migration profile validates the isolated schema")
    void bootsMigrationProfile() {
        var application = new SpringApplication(FacilityFlowApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        var url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres?sslmode=disable";
        try (var context = application.run(
                "--spring.profiles.active=database-migrate",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=facilityflow_migrator",
                "--spring.datasource.password=synthetic",
                "--spring.datasource.hikari.data-source-properties.sslrootcert=synthetic-test.crt",
                "--spring.flyway.url=" + url,
                "--spring.flyway.user=facilityflow_migrator",
                "--spring.flyway.password=synthetic")) {
            assertTrue(context.getBean(Flyway.class).validateWithResult().validationSuccessful);
        }
    }

    private UUID newAccount(String role) {
        return insertAccount("fixture" + UUID.randomUUID().toString().substring(0, 12), "Synthetic Account", role);
    }

    private UUID createRequest(UUID owner, UUID recorder) {
        return app.queryForObject("INSERT INTO facilityflow.maintenance_request "
                        + "(requester_id, recorded_by, title, description, location, category_id, reported_urgency) "
                        + "VALUES (?, ?, ?, ?, 'Block A', (SELECT id FROM facilityflow.category WHERE name = 'Electrical'), 'NORMAL') RETURNING id",
                UUID.class, owner, recorder, "😀".repeat(5), "First line\nSecond line");
    }

    @Test
    @DisplayName("REQ-RQ-002 / REQ-MGR-003: database-generated OPEN Request preserves owner and Recording Actor")
    void generatesRequestIdentity() {
        var owner = newAccount("REQUESTER");
        var manager = newAccount("FACILITIES_MANAGER");
        var request = createRequest(owner, manager);
        var row = app.queryForMap("SELECT * FROM facilityflow.maintenance_request WHERE id = ?", request);
        assertEquals(owner, row.get("requester_id"));
        assertEquals(manager, row.get("recorded_by"));
        assertEquals("OPEN", row.get("status"));
        assertNull(row.get("assigned_technician_id"));
        assertNull(row.get("manager_priority"));
        assertTrue(row.get("display_id").toString().matches("FF-[0-9]{6}"));
        assertEquals("First line\nSecond line", row.get("description"));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.maintenance_request SET requester_id = ? WHERE id = ?", manager, request));
        assertSqlState("428C9", () -> app.update("UPDATE facilityflow.maintenance_request SET display_number = 7 WHERE id = ?", request));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.maintenance_request SET created_at = CURRENT_TIMESTAMP WHERE id = ?", request));
        assertSqlState("23514", () -> app.update("UPDATE facilityflow.maintenance_request SET title = 'abcd' WHERE id = ?", request));
        assertSqlState("23514", () -> app.update("UPDATE facilityflow.maintenance_request SET title = ? WHERE id = ?", "😀".repeat(101), request));
        assertSqlState("23514", () -> app.update("UPDATE facilityflow.maintenance_request SET reported_urgency = 'CRITICAL' WHERE id = ?", request));
        assertSqlState("23503", () -> app.update("UPDATE facilityflow.maintenance_request SET category_id = ? WHERE id = ?", UUID.randomUUID(), request));
    }

    @Test
    @DisplayName("TECHNICAL-SPEC / PRODUCT-SPEC: display ID overflow fails without wrapping")
    void rejectsDisplayNumberOverflow() {
        var owner = newAccount("REQUESTER");
        var sequence = admin.queryForMap("SELECT last_value, is_called FROM facilityflow.request_display_number_seq");
        try {
            admin.queryForObject("SELECT setval('facilityflow.request_display_number_seq', 999998, true)", Long.class);
            var last = createRequest(owner, owner);
            assertEquals("FF-999999", app.queryForObject("SELECT display_id FROM facilityflow.maintenance_request WHERE id = ?", String.class, last));
            var count = app.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Long.class);
            assertSqlState("2200H", () -> createRequest(owner, owner));
            assertEquals(count, app.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Long.class));
        } finally {
            admin.queryForObject("SELECT setval('facilityflow.request_display_number_seq', ?, ?)", Long.class,
                    sequence.get("last_value"), sequence.get("is_called"));
        }
    }

    @Test
    @DisplayName("REQ-MGR-004 / REQ-TECH-007: assignment/completion shapes and history survive reopening")
    void preservesWorkAndCompletionRecords() {
        var owner = newAccount("REQUESTER");
        var technician = newAccount("TECHNICIAN");
        var request = createRequest(owner, owner);
        assertSqlState("23514", () -> app.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED' WHERE id = ?", request));
        app.update("UPDATE facilityflow.maintenance_request SET status = 'IN_PROGRESS', "
                + "manager_priority = 'HIGH', assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP WHERE id = ?", technician, request);
        var log = app.queryForObject("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Checked safely', 15) RETURNING id", UUID.class, request, technician);
        assertSqlState("23514", () -> app.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Invalid duration', 0)", request, technician));
        assertSqlState("23514", () -> app.update("UPDATE facilityflow.maintenance_request SET status = 'COMPLETED' WHERE id = ?", request));
        app.update("INSERT INTO facilityflow.request_completion_history (request_id, technician_id, resolution_summary) VALUES (?, ?, 'Resolved and checked safely')", request, technician);
        app.update("UPDATE facilityflow.maintenance_request SET status = 'COMPLETED', resolution_summary = 'Resolved and checked safely', "
                + "completed_by = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?", technician, request);
        app.update("UPDATE facilityflow.maintenance_request SET status = 'CLOSED' WHERE id = ?", request);
        app.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED' WHERE id = ?", request);
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.request_completion_history WHERE request_id = ?", Integer.class, request));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.work_log WHERE id = ?", Integer.class, log));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.work_log SET minutes = 20 WHERE id = ?", log));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.request_completion_history WHERE request_id = ?", request));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.maintenance_request WHERE id = ?", request));
    }

    @Test
    @DisplayName("REQ-RQ-008 / REQ-SH-007: Requester Updates are bounded append-only records")
    void preservesRequesterUpdates() {
        var owner = newAccount("REQUESTER");
        var request = createRequest(owner, owner);
        var update = app.queryForObject("INSERT INTO facilityflow.requester_update (request_id, author_id, note) VALUES (?, ?, ?) RETURNING id", UUID.class, request, owner, "😀".repeat(1000));
        assertSqlState("23514", () -> app.update("INSERT INTO facilityflow.requester_update (request_id, author_id, note) VALUES (?, ?, ?)", request, owner, "😀".repeat(1001)));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.requester_update SET note = 'Changed' WHERE id = ?", update));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.requester_update WHERE id = ?", update));
    }

    @Test
    @DisplayName("REQ-MGR-016–019: catalogue uniqueness, skill links and historical tag identity")
    void maintainsCataloguesAndSkills() {
        assertEquals(7, app.queryForObject("SELECT count(*) FROM facilityflow.category", Integer.class));
        assertSqlState("23505", () -> app.update("INSERT INTO facilityflow.category (name) VALUES ('electrical')"));
        var tag = app.queryForObject("INSERT INTO facilityflow.skill_tag (name) VALUES ('Electrical Safety') RETURNING id", UUID.class);
        var technician = newAccount("TECHNICIAN");
        app.update("INSERT INTO facilityflow.technician_skill (technician_id, skill_tag_id) VALUES (?, ?)", technician, tag);
        assertSqlState("23505", () -> app.update("INSERT INTO facilityflow.technician_skill (technician_id, skill_tag_id) VALUES (?, ?)", technician, tag));
        assertSqlState("23505", () -> app.update("INSERT INTO facilityflow.skill_tag (name) VALUES ('electrical safety')"));
        app.update("UPDATE facilityflow.skill_tag SET active = false WHERE id = ?", tag);
        assertEquals(tag, app.queryForObject("SELECT skill_tag_id FROM facilityflow.technician_skill WHERE technician_id = ?", UUID.class, technician));
        app.update("DELETE FROM facilityflow.technician_skill WHERE technician_id = ? AND skill_tag_id = ?", technician, tag);
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.skill_tag WHERE id = ?", tag));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.category WHERE name = 'Other'"));
    }

    @Test
    @DisplayName("REQ-AI-007–010 / AI-SEC-017: typed AI metadata without raw output or invented usage")
    void storesOnlyAiMetadata() {
        var actor = newAccount("REQUESTER");
        var invocation = app.queryForObject("INSERT INTO facilityflow.ai_invocation "
                        + "(actor_id, correlation_id, feature, model, template_version, status, latency_ms, retry_count) "
                        + "VALUES (?, ?, 'SMART_REPORT', 'synthetic-model', 'v1', 'AI_TIMEOUT', 30000, 0) RETURNING id",
                UUID.class, actor, UUID.randomUUID());
        assertNull(app.queryForObject("SELECT total_tokens FROM facilityflow.ai_invocation WHERE id = ?", Long.class, invocation));
        assertSqlState("23514", () -> app.update("INSERT INTO facilityflow.ai_invocation "
                + "(actor_id, correlation_id, feature, template_version, status, latency_ms, retry_count) "
                + "VALUES (?, ?, 'SMART_REPORT', 'v1', 'AI_TIMEOUT', 1, 2)", actor, UUID.randomUUID()));
        assertSqlState("42501", () -> app.update("DELETE FROM facilityflow.ai_invocation WHERE id = ?", invocation));
        assertSqlState("42501", () -> app.update("UPDATE facilityflow.ai_invocation SET status = 'SUCCEEDED' WHERE id = ?", invocation));
        var columns = admin.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'facilityflow' AND table_name = 'ai_invocation'", String.class);
        assertFalse(columns.contains("prompt"));
        assertFalse(columns.contains("output"));
        assertFalse(columns.contains("metadata"));
    }

    @Test
    @DisplayName("AI-SEC-008/021: rolling-window storage and optimistic global-slot claims")
    void supportsAtomicLimitStorage() {
        var actor = newAccount("REQUESTER");
        app.update("INSERT INTO facilityflow.rate_limit_bucket (scope, account_id) VALUES ('ACCOUNT_RATE', ?)", actor);
        app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = ARRAY[CURRENT_TIMESTAMP] WHERE account_id = ?", actor);
        assertEquals(1, app.queryForObject("SELECT cardinality(invocation_times) FROM facilityflow.rate_limit_bucket WHERE account_id = ?", Integer.class, actor));
        assertSqlState("23514", () -> app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = array_fill(CURRENT_TIMESTAMP, ARRAY[31]) WHERE account_id = ?", actor));
        assertSqlState("23505", () -> app.update("INSERT INTO facilityflow.rate_limit_bucket (scope, account_id) VALUES ('ACCOUNT_RATE', ?)", actor));
        assertEquals(5, app.queryForObject("SELECT count(*) FROM facilityflow.rate_limit_bucket WHERE scope = 'GLOBAL_SLOT'", Integer.class));
        var version = app.queryForObject("SELECT version FROM facilityflow.rate_limit_bucket WHERE slot = 1", Long.class);
        var claim = "UPDATE facilityflow.rate_limit_bucket SET lease_expires_at = CURRENT_TIMESTAMP + interval '30 seconds', version = version + 1 WHERE slot = 1 AND version = ?";
        assertEquals(1, app.update(claim, version));
        assertEquals(0, app.update(claim, version));
    }

    @Test
    @DisplayName("NFR-SEC-001: every application table has RLS and all schema objects are migration-owned")
    void verifiesPrivateSchemaCoverage() {
        assertEquals(0, admin.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname = 'facilityflow' AND tablename <> 'flyway_schema_history' AND NOT rowsecurity", Integer.class));
        assertEquals(0, admin.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname = 'facilityflow' AND tableowner <> 'facilityflow_migrator'", Integer.class));
        assertEquals(14, admin.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname = 'facilityflow'", Integer.class));
    }
}
