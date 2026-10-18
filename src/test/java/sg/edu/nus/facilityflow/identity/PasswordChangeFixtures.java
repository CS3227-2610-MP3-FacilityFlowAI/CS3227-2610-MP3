package sg.edu.nus.facilityflow.identity;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Disposable restricted-role PostgreSQL fixture for REQ-SH-002/004/006/014. */
final class PasswordChangeFixtures implements AutoCloseable {
    static final UUID ACCOUNT = UUID.fromString("75000000-0000-0000-0000-000000000001");
    static final String OLD_PASSWORD = "old-password";

    final EmbeddedPostgres postgres;
    final JdbcTemplate admin;
    final JdbcTemplate migrator;
    final JdbcTemplate app;

    private PasswordChangeFixtures(EmbeddedPostgres postgres, JdbcTemplate admin,
                                   JdbcTemplate migrator, JdbcTemplate app) {
        this.postgres = postgres;
        this.admin = admin;
        this.migrator = migrator;
        this.app = app;
    }

    static PasswordChangeFixtures start() throws Exception {
        EmbeddedPostgres postgres = EmbeddedPostgres.start();
        JdbcTemplate admin = new JdbcTemplate(postgres.getPostgresDatabase());
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        DataSource migratorSource = dataSource(postgres, "facilityflow_migrator");
        Flyway.configure().dataSource(migratorSource).schemas("facilityflow").defaultSchema("facilityflow")
                .cleanDisabled(true).ignoreMigrationPatterns(new String[0]).target("5").load().migrate();
        var fixture = new PasswordChangeFixtures(postgres, admin, new JdbcTemplate(migratorSource),
                new JdbcTemplate(dataSource(postgres, "facilityflow_app")));
        fixture.reset();
        return fixture;
    }

    void reset() {
        admin.execute("DROP TRIGGER IF EXISTS fail_password_audit ON facilityflow.audit_event");
        admin.execute("DROP FUNCTION IF EXISTS facilityflow.fail_password_audit()");
        admin.execute("TRUNCATE facilityflow.audit_event CASCADE");
        migrator.update("DELETE FROM facilityflow.user_account WHERE id = ?", ACCOUNT);
        account("REQUESTER", true, 4, "{noop}old-password");
    }

    void account(String role, boolean active, long sessionVersion, String hash) {
        migrator.update("""
                INSERT INTO facilityflow.user_account
                    (id, username, display_name, password_hash, role, active, session_version)
                VALUES (?, 'password-test', 'Password Test', ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, role = EXCLUDED.role,
                    active = EXCLUDED.active, session_version = EXCLUDED.session_version
                """, ACCOUNT, hash, role, active, sessionVersion);
    }

    static PGSimpleDataSource dataSource(EmbeddedPostgres postgres, String role) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser(role);
        return source;
    }

    @Override public void close() throws IOException { postgres.close(); }
}
