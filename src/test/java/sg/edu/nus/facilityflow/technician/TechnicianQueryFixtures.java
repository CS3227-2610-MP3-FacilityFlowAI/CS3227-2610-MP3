package sg.edu.nus.facilityflow.technician;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Isolated, disposable PostgreSQL fixture for REQ-TECH-001–003/009. */
final class TechnicianQueryFixtures implements AutoCloseable {
    static final UUID REQUESTER = UUID.fromString("74000000-0000-0000-0000-000000000001");
    static final UUID TECHNICIAN = UUID.fromString("74000000-0000-0000-0000-000000000002");
    static final UUID OTHER_TECHNICIAN = UUID.fromString("74000000-0000-0000-0000-000000000003");
    static final UUID PRIOR_TECHNICIAN = UUID.fromString("74000000-0000-0000-0000-000000000004");
    static final UUID ELECTRICAL = UUID.fromString("74000000-0000-0000-0000-000000000005");
    static final UUID PLUMBING = UUID.fromString("74000000-0000-0000-0000-000000000006");

    final EmbeddedPostgres postgres;
    final JdbcTemplate admin;
    final JdbcTemplate app;
    final UUID legacyRequest;
    final UUID criticalLegacyRequest;

    private TechnicianQueryFixtures(
            EmbeddedPostgres postgres, JdbcTemplate admin, JdbcTemplate app,
            UUID legacyRequest, UUID criticalLegacyRequest) {
        this.postgres = postgres;
        this.admin = admin;
        this.app = app;
        this.legacyRequest = legacyRequest;
        this.criticalLegacyRequest = criticalLegacyRequest;
    }

    static TechnicianQueryFixtures start() throws Exception {
        EmbeddedPostgres postgres = EmbeddedPostgres.start();
        JdbcTemplate admin = new JdbcTemplate(postgres.getPostgresDatabase());
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        var migrator = dataSource(postgres, "facilityflow_migrator");
        Flyway flyway = Flyway.configure().dataSource(migrator).schemas("facilityflow")
                .defaultSchema("facilityflow").cleanDisabled(true)
                .ignoreMigrationPatterns(new String[0]).target("3").load();
        flyway.migrate();

        JdbcTemplate preUpgrade = new JdbcTemplate(dataSource(postgres, "facilityflow_migrator"));
        seedAccounts(preUpgrade);
        UUID legacyRequest = preUpgrade.queryForObject("""
                INSERT INTO facilityflow.maintenance_request
                    (requester_id, recorded_by, title, description, location, category_id,
                     reported_urgency, status, manager_priority, assigned_technician_id)
                VALUES (?, ?, 'Legacy null-time request', 'Imported assignment with unknown time.',
                        'Block A', ?, 'NORMAL', 'ASSIGNED', 'LOW', ?)
                RETURNING id
                """, UUID.class, REQUESTER, REQUESTER, ELECTRICAL, TECHNICIAN);
        UUID criticalLegacyRequest = preUpgrade.queryForObject("""
                INSERT INTO facilityflow.maintenance_request
                    (requester_id, recorded_by, title, description, location, category_id,
                     reported_urgency, status, manager_priority, assigned_technician_id)
                VALUES (?, ?, 'Legacy critical request', 'Imported critical assignment with unknown time.',
                        'Block B', ?, 'EMERGENCY', 'ASSIGNED', 'CRITICAL', ?)
                RETURNING id
                """, UUID.class, REQUESTER, REQUESTER, ELECTRICAL, TECHNICIAN);

        Flyway.configure().dataSource(migrator).schemas("facilityflow").defaultSchema("facilityflow")
                .cleanDisabled(true).ignoreMigrationPatterns(new String[0]).target("5").load().migrate();
        return new TechnicianQueryFixtures(postgres, admin,
                new JdbcTemplate(dataSource(postgres, "facilityflow_app")), legacyRequest, criticalLegacyRequest);
    }

    private static void seedAccounts(JdbcTemplate jdbc) {
        insertAccount(jdbc, REQUESTER, "requester", "Requester One", "REQUESTER", true, 0);
        insertAccount(jdbc, TECHNICIAN, "tech-one", "Technician One", "TECHNICIAN", true, 7);
        insertAccount(jdbc, OTHER_TECHNICIAN, "tech-two", "Technician Two", "TECHNICIAN", true, 3);
        insertAccount(jdbc, PRIOR_TECHNICIAN, "tech-prior", "Prior Technician", "TECHNICIAN", true, 2);
        jdbc.update("INSERT INTO facilityflow.category (id, name, active) VALUES (?, 'Test Electrical', true)", ELECTRICAL);
        jdbc.update("INSERT INTO facilityflow.category (id, name, active) VALUES (?, 'Test Plumbing', false)", PLUMBING);
    }

    private static void insertAccount(JdbcTemplate jdbc, UUID id, String username, String displayName,
                                      String role, boolean active, long sessionVersion) {
        jdbc.update("""
                INSERT INTO facilityflow.user_account
                    (id, username, display_name, password_hash, role, active, session_version)
                VALUES (?, ?, ?, 'synthetic-test-hash', ?, ?, ?)
                """, id, username, displayName, role, active, sessionVersion);
    }

    static PGSimpleDataSource dataSource(EmbeddedPostgres postgres, String role) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser(role);
        return source;
    }

    String assignedRequest(UUID assignee, String title, String location, String categoryName,
                           String status, String priority, String urgency, Instant assignedAt) {
        UUID category = app.queryForObject("SELECT id FROM facilityflow.category WHERE name = ?", UUID.class, categoryName);
        boolean completed = status.equals("COMPLETED") || status.equals("CLOSED");
        return admin.queryForObject("""
                INSERT INTO facilityflow.maintenance_request
                    (requester_id, recorded_by, title, description, location, category_id,
                     reported_urgency, status, manager_priority, assigned_technician_id, assigned_at,
                     resolution_summary, completed_by, completed_at)
                VALUES (?, ?, ?, 'Synthetic request description for technician read tests.', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING display_id
                """, String.class, REQUESTER, REQUESTER, title, location, category, urgency, status,
                priority, assignee, assignedAt == null ? null : OffsetDateTime.ofInstant(assignedAt, ZoneOffset.UTC),
                completed ? "Synthetic completion summary" : null, completed ? assignee : null,
                completed ? OffsetDateTime.ofInstant(assignedAt, ZoneOffset.UTC) : null);
    }

    UUID requestId(String displayId) {
        return app.queryForObject("SELECT id FROM facilityflow.maintenance_request WHERE display_id = ?", UUID.class, displayId);
    }

    void addUpdate(UUID requestId, UUID authorId, String note, Instant createdAt) {
        addUpdate(UUID.randomUUID(), requestId, authorId, note, createdAt);
    }

    void addUpdate(UUID id, UUID requestId, UUID authorId, String note, Instant createdAt) {
        admin.update("INSERT INTO facilityflow.requester_update (id, request_id, author_id, note, created_at) VALUES (?, ?, ?, ?, ?)",
                id, requestId, authorId, note, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    void addWorkLog(UUID requestId, UUID authorId, String note, int minutes, Instant createdAt) {
        addWorkLog(UUID.randomUUID(), requestId, authorId, note, minutes, createdAt);
    }

    void addWorkLog(UUID id, UUID requestId, UUID authorId, String note, int minutes, Instant createdAt) {
        admin.update("INSERT INTO facilityflow.work_log (id, request_id, author_id, note, minutes, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, requestId, authorId, note, minutes, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    @Override
    public void close() throws IOException {
        postgres.close();
    }
}
