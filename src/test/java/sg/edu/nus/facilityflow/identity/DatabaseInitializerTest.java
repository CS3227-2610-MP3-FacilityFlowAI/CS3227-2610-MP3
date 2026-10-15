package sg.edu.nus.facilityflow.identity;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import sg.edu.nus.facilityflow.FacilityFlowApplication;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseInitializerTest {
    private EmbeddedPostgres postgres;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transaction;

    @BeforeEach
    void createDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
            connection.createStatement().execute("ALTER ROLE facilityflow_migrator LOGIN");
            connection.createStatement().execute("ALTER ROLE facilityflow_app LOGIN");
        }
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser("facilityflow_migrator");
        Flyway.configure().dataSource(source).schemas("facilityflow").defaultSchema("facilityflow").load().migrate();
        source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser("facilityflow_app");
        jdbc = new JdbcTemplate(source);
        transaction = new DataSourceTransactionManager(source);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        if (postgres != null) postgres.close();
    }

    private MockEnvironment bootstrap(String password) {
        return new MockEnvironment().withProperty("BOOTSTRAP_MANAGER_USERNAME", "first-manager")
                .withProperty("BOOTSTRAP_MANAGER_DISPLAY_NAME", "First Manager")
                .withProperty("BOOTSTRAP_MANAGER_PASSWORD", password);
    }

    private MockEnvironment demoEnvironment() {
        var environment = new MockEnvironment().withProperty("DEMO_DATA_ENABLED", "true");
        environment.setActiveProfiles("development");
        return environment;
    }

    @Test
    @DisplayName("REQ-SH-015/016: known demo credentials require explicit non-production profile")
    void rejectsDemoOutsideDevelopment() {
        for (var profiles : new String[][]{{}, {"production"}, {"prod"}, {"development", "production"}, {"development", "prod"}}) {
            var environment = demoEnvironment();
            environment.setActiveProfiles(profiles);
            assertThrows(IllegalStateException.class,
                    () -> new DatabaseInitializer(jdbc, transaction, environment).initialize());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
    }

    @Test
    @DisplayName("REQ-SH-014/016: contended initialization times out and rolls back safely")
    void boundsInitializationLockWait() throws Exception {
        var initializer = new DatabaseInitializer(jdbc, transaction, bootstrap("TestPassword123"));
        try (var blocker = postgres.getPostgresDatabase().getConnection(); var statement = blocker.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(322726100003)");
            var failure = assertThrows(org.springframework.dao.DataAccessException.class, initializer::initialize);
            var cause = assertInstanceOf(java.sql.SQLException.class, failure.getMostSpecificCause());
            assertEquals("55P03", cause.getSQLState());
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
        }
        assertTrue(initializer.initialize());
    }

    @Test
    @DisplayName("REQ-SH-016 / NFR-SEC-002: default initialization creates only a salted-hash Manager")
    void bootstrapsManagerWithoutDemo() {
        var password = "😀".repeat(24);
        var initializer = new DatabaseInitializer(jdbc, transaction, bootstrap(password));
        assertTrue(initializer.initialize());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals("FACILITIES_MANAGER", jdbc.queryForObject("SELECT role FROM facilityflow.user_account", String.class));
        var hash = jdbc.queryForObject("SELECT password_hash FROM facilityflow.user_account", String.class);
        assertNotEquals(password, hash);
        assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(password, hash));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
        assertFalse(new DatabaseInitializer(jdbc, transaction, new MockEnvironment()).initialize());
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM facilityflow.user_account", String.class));
    }

    @Test
    @DisplayName("REQ-SH-015: explicit demo initialization supplies six Accounts and all lifecycle states")
    void seedsDemoAtomicallyAndOnlyOnce() {
        var initializer = new DatabaseInitializer(jdbc, transaction, demoEnvironment());
        assertTrue(initializer.initialize());
        assertEquals(6L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals(6L, jdbc.queryForObject("SELECT count(DISTINCT status) FROM facilityflow.maintenance_request", Long.class));
        assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.work_log", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.request_completion_history", Long.class));
        assertEquals(6L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = 'ACCOUNT_CREATED'", Long.class));
        var hashes = jdbc.queryForList("SELECT password_hash FROM facilityflow.user_account", String.class);
        assertEquals(6, hashes.stream().distinct().count());
        for (var hash : hashes) assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("Welcome123", hash));
        var events = jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class);
        assertFalse(initializer.initialize());
        assertEquals(events, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
    }

    @Test
    @DisplayName("REQ-SH-014–016: invalid bootstrap configuration rolls back without partial data")
    void rejectsMissingOrInvalidBootstrap() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseInitializer(jdbc, transaction, new MockEnvironment()).initialize());
        assertThrows(IllegalArgumentException.class, () -> new DatabaseInitializer(jdbc, transaction, bootstrap("short")).initialize());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
    }

    @Test
    @DisplayName("REQ-SH-014–015: seed failure after Account creation rolls back every business record")
    void rollsBackDemoWhenRequestCreationFails() {
        try (var admin = postgres.getPostgresDatabase().getConnection(); var statement = admin.createStatement()) {
            statement.execute("REVOKE INSERT ON facilityflow.maintenance_request FROM facilityflow_app");
            statement.execute("REVOKE INSERT (requester_id, recorded_by, title, description, location, category_id, reported_urgency) ON facilityflow.maintenance_request FROM facilityflow_app");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
        var initializer = new DatabaseInitializer(jdbc, transaction, demoEnvironment());
        assertThrows(org.springframework.dao.DataAccessException.class, initializer::initialize);
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Long.class));
    }

    @Test
    @DisplayName("REQ-SH-016: concurrent bootstrap attempts create exactly one Manager")
    void serializesConcurrentInitialization() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> new DatabaseInitializer(jdbc, transaction, bootstrap("TestPassword123")).initialize();
            var results = executor.invokeAll(java.util.List.of(first, first));
            assertNotEquals(results.get(0).get(), results.get(1).get());
        }
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
    }

    @Test
    @DisplayName("REQ-SH-016: actual initialization profile wires JDBC transactions and bootstrap settings")
    void bootsInitializationProfile() {
        var application = new SpringApplication(FacilityFlowApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(
                "--spring.profiles.active=database-initialize",
                "--spring.flyway.enabled=false",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres?sslmode=disable",
                "--spring.datasource.username=facilityflow_app",
                "--spring.datasource.password=synthetic",
                "--spring.datasource.hikari.data-source-properties.sslrootcert=synthetic-test.crt",
                "--DEMO_DATA_ENABLED=false",
                "--BOOTSTRAP_MANAGER_USERNAME=command-manager",
                "--BOOTSTRAP_MANAGER_DISPLAY_NAME=Command Manager",
                "--BOOTSTRAP_MANAGER_PASSWORD=CommandTest123")) {
            assertNotNull(context.getBean(DatabaseInitializer.class));
            assertEquals("command-manager", jdbc.queryForObject("SELECT username FROM facilityflow.user_account", String.class));
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Long.class));
        }
    }

    @Test
    @DisplayName("REQ-SH-015–016: existing catalogue work is preserved without seed changes")
    void rejectsExistingCatalogueWork() {
        jdbc.update("INSERT INTO facilityflow.category (name) VALUES ('Existing Team Category')");
        var initializer = new DatabaseInitializer(jdbc, transaction, demoEnvironment());
        assertThrows(IllegalStateException.class, initializer::initialize);
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
        assertEquals(8L, jdbc.queryForObject("SELECT count(*) FROM facilityflow.category", Long.class));
    }
}
