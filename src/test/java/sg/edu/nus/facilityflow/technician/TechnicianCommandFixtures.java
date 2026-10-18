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

/** Disposable PostgreSQL fixture for REQ-TECH-004–006/010–011. */
final class TechnicianCommandFixtures implements AutoCloseable {
    static final UUID REQUESTER = UUID.fromString("76000000-0000-0000-0000-000000000001");
    static final UUID TECHNICIAN = UUID.fromString("76000000-0000-0000-0000-000000000002");
    static final UUID OTHER_TECHNICIAN = UUID.fromString("76000000-0000-0000-0000-000000000003");
    static final UUID MANAGER = UUID.fromString("76000000-0000-0000-0000-000000000004");
    static final UUID ELECTRICAL = UUID.fromString("76000000-0000-0000-0000-000000000005");

    final EmbeddedPostgres postgres;
    final JdbcTemplate admin;
    final JdbcTemplate app;
    final JdbcTemplate migrator;

    private TechnicianCommandFixtures(EmbeddedPostgres postgres, JdbcTemplate admin,
                                      JdbcTemplate migrator, JdbcTemplate app) {
        this.postgres = postgres;
        this.admin = admin;
        this.migrator = migrator;
        this.app = app;
    }

    static TechnicianCommandFixtures start() throws Exception {
        EmbeddedPostgres postgres = EmbeddedPostgres.start();
        JdbcTemplate admin = new JdbcTemplate(postgres.getPostgresDatabase());
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        JdbcTemplate migrator = new JdbcTemplate(dataSource(postgres, "facilityflow_migrator"));
        Flyway.configure().dataSource(dataSource(postgres, "facilityflow_migrator"))
                .schemas("facilityflow").defaultSchema("facilityflow").cleanDisabled(true)
                .ignoreMigrationPatterns(new String[0]).target("5").load().migrate();
        TechnicianCommandFixtures fixture = new TechnicianCommandFixtures(postgres, admin, migrator,
                new JdbcTemplate(dataSource(postgres, "facilityflow_app")));
        fixture.reset();
        return fixture;
    }

    void reset() {
        admin.execute("DROP TRIGGER IF EXISTS fail_t2_audit ON facilityflow.audit_event");
        admin.execute("DROP FUNCTION IF EXISTS facilityflow.fail_t2_audit()");
        admin.execute("TRUNCATE facilityflow.audit_event, facilityflow.maintenance_request CASCADE");
        account(REQUESTER, "requester", "Requester", "REQUESTER", true, 0);
        account(TECHNICIAN, "technician", "Technician", "TECHNICIAN", true, 7);
        account(OTHER_TECHNICIAN, "other-tech", "Other Technician", "TECHNICIAN", true, 3);
        account(MANAGER, "manager", "Manager", "FACILITIES_MANAGER", true, 0);
        migrator.update("INSERT INTO facilityflow.category (id, name, active) VALUES (?, 'T2 Electrical', true) "
                + "ON CONFLICT (id) DO UPDATE SET active = true, name = 'T2 Electrical'", ELECTRICAL);
    }

    private void account(UUID id, String username, String displayName, String role, boolean active, long version) {
        migrator.update("""
                INSERT INTO facilityflow.user_account
                    (id, username, display_name, password_hash, role, active, session_version)
                VALUES (?, ?, ?, 'synthetic-test-hash', ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username, display_name = EXCLUDED.display_name,
                    password_hash = EXCLUDED.password_hash, role = EXCLUDED.role, active = EXCLUDED.active,
                    session_version = EXCLUDED.session_version
                """, id, username, displayName, role, active, version);
    }

    String request(UUID assignee, String state, long version) {
        return admin.queryForObject("""
                INSERT INTO facilityflow.maintenance_request
                    (requester_id, recorded_by, title, description, location, category_id,
                     reported_urgency, status, manager_priority, assigned_technician_id, assigned_at, version,
                     resolution_summary, completed_by, completed_at)
                VALUES (?, ?, 'Synthetic T2 request', 'Synthetic request description for command tests.',
                        'Block A', ?, 'NORMAL', ?, 'MEDIUM', ?,
                        CASE WHEN CAST(? AS uuid) IS NULL THEN NULL ELSE '2026-10-17T08:00:00Z'::timestamptz END, ?,
                        CASE WHEN CAST(? AS text) IN ('COMPLETED', 'CLOSED') THEN 'Synthetic resolution summary.' ELSE NULL END,
                        CASE WHEN CAST(? AS text) IN ('COMPLETED', 'CLOSED') THEN CAST(? AS uuid) ELSE NULL::uuid END,
                        CASE WHEN CAST(? AS text) IN ('COMPLETED', 'CLOSED') THEN '2026-10-17T09:00:00Z'::timestamptz ELSE NULL END)
                RETURNING display_id
                """, String.class, REQUESTER, REQUESTER, ELECTRICAL, state, assignee, assignee, version,
                state, state, assignee, state);
    }

    UUID requestId(String displayId) {
        return app.queryForObject("SELECT id FROM facilityflow.maintenance_request WHERE display_id = ?", UUID.class, displayId);
    }

    long requestVersion(String displayId) {
        return app.queryForObject("SELECT version FROM facilityflow.maintenance_request WHERE display_id = ?", Long.class, displayId);
    }

    String requestState(String displayId) {
        return app.queryForObject("SELECT status FROM facilityflow.maintenance_request WHERE display_id = ?", String.class, displayId);
    }

    @Override
    public void close() throws IOException {
        postgres.close();
    }

    static PGSimpleDataSource dataSource(EmbeddedPostgres postgres, String role) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser(role);
        return source;
    }

    static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
