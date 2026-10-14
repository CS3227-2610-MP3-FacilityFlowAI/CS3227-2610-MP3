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
        flyway = Flyway.configure().dataSource(dataSource("facilityflow_migrator"))
                .schemas("facilityflow").defaultSchema("facilityflow")
                .cleanDisabled(true).ignoreMigrationPatterns(new String[0]).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
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
}
