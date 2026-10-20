package sg.edu.nus.facilityflow.database;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProductionDatabaseIsolationTest {
    @Test
    void independentDatabasesAndRolesRetainRuntimeRestrictions() throws Exception {
        // NFR-DEP-001 / NFR-SEC-001: disposable local cluster, no hosted credentials.
        try (var postgres = EmbeddedPostgres.start()) {
            var owner = source(postgres, "postgres", "postgres");
            try (var connection = owner.getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new FileSystemResource("workflow/database/bootstrap-roles.sql"));
                ScriptUtils.executeSqlScript(connection,
                        new FileSystemResource("workflow/database/bootstrap-production-database.sql"));
            }
            var admin = new JdbcTemplate(owner);
            for (var role : new String[]{"facilityflow_migrator", "facilityflow_app",
                    "facilityflow_prod_migrator", "facilityflow_prod_app"}) {
                admin.execute("ALTER ROLE " + role + " LOGIN");
            }
            migrate(source(postgres, "postgres", "facilityflow_migrator"));
            var productionMigrator = source(postgres, "facilityflow_prod", "facilityflow_prod_migrator");
            migrate(productionMigrator);
            var permissions = Files.readString(Path.of("workflow/database/production-runtime-permissions.sql"));
            var migrator = new JdbcTemplate(productionMigrator);
            migrator.execute(permissions);
            migrator.execute(permissions); // Safe to reapply after later migrations.

            var dev = new JdbcTemplate(source(postgres, "postgres", "facilityflow_app"));
            var prod = new JdbcTemplate(source(postgres, "facilityflow_prod", "facilityflow_prod_app"));
            dev.update("INSERT INTO facilityflow.user_account (username, display_name, password_hash, role) "
                    + "VALUES ('dev-only', 'Development only', 'synthetic', 'REQUESTER')");
            assertEquals(0L, prod.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
            prod.update("INSERT INTO facilityflow.user_account (username, display_name, password_hash, role) "
                    + "VALUES ('prod-only', 'Production only', 'synthetic', 'REQUESTER')");
            prod.update("UPDATE facilityflow.user_account SET display_name = 'Production changed' "
                    + "WHERE username = 'prod-only'");
            assertEquals(1L, dev.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
            assertEquals(6, prod.queryForObject("SELECT max(version::int) FROM facilityflow.schema_version", Integer.class));
            assertThrows(DataAccessException.class, () -> prod.execute("DELETE FROM facilityflow.user_account"));
            assertThrows(DataAccessException.class, () -> prod.execute("UPDATE facilityflow.audit_event SET action = 'bad'"));
            assertThrows(DataAccessException.class, () -> prod.execute("CREATE TABLE facilityflow.forbidden (id int)"));
            assertThrows(DataAccessException.class, () -> prod.queryForList("SELECT * FROM facilityflow.flyway_schema_history"));

            var productionOwner = new JdbcTemplate(source(postgres, "facilityflow_prod", "postgres"));
            var backupSetup = Files.readString(Path.of("workflow/database/production-backup-role.sql"));
            productionOwner.execute(backupSetup);
            productionOwner.execute(backupSetup);
            admin.execute("ALTER ROLE facilityflow_prod_backup LOGIN");
            var backup = new JdbcTemplate(source(postgres, "facilityflow_prod", "facilityflow_prod_backup"));
            assertEquals(1L, backup.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));
            assertEquals(6L, backup.queryForObject("SELECT count(*) FROM facilityflow.flyway_schema_history WHERE success AND version IS NOT NULL", Long.class));
            assertThrows(DataAccessException.class, () -> backup.execute("DELETE FROM facilityflow.user_account"));
            assertThrows(DataAccessException.class, () -> backup.execute("UPDATE facilityflow.audit_event SET action = 'bad'"));
            assertThrows(DataAccessException.class, () -> backup.execute("CREATE TABLE facilityflow.bad (id int)"));
            assertThrows(DataAccessException.class, () -> new JdbcTemplate(
                    source(postgres, "postgres", "facilityflow_prod_backup"))
                    .queryForList("SELECT * FROM facilityflow.user_account"));
            productionOwner.execute("GRANT UPDATE (display_name) ON facilityflow.user_account TO facilityflow_prod_backup");
            assertThrows(DataAccessException.class, () -> productionOwner.execute(backupSetup));
            productionOwner.execute("REVOKE UPDATE (display_name) ON facilityflow.user_account FROM facilityflow_prod_backup");
            productionOwner.execute("GRANT UPDATE ON SEQUENCE facilityflow.request_display_number_seq TO facilityflow_prod_backup");
            assertThrows(DataAccessException.class, () -> productionOwner.execute(backupSetup));
            productionOwner.execute("REVOKE UPDATE ON SEQUENCE facilityflow.request_display_number_seq FROM facilityflow_prod_backup");
            productionOwner.execute(backupSetup);

            productionOwner.execute("CREATE SCHEMA unrelated");
            productionOwner.execute("CREATE TABLE unrelated.sensitive (secret text)");
            productionOwner.execute("CREATE SEQUENCE unrelated.counter");
            productionOwner.execute("CREATE FUNCTION unrelated.helper() RETURNS int LANGUAGE sql AS 'SELECT 1'");
            for (var grant : new String[]{
                    "SELECT ON TABLE unrelated.sensitive",
                    "SELECT (secret) ON TABLE unrelated.sensitive",
                    "SELECT ON SEQUENCE unrelated.counter",
                    "USAGE ON SCHEMA unrelated",
                    "EXECUTE ON FUNCTION unrelated.helper()",
                    "CONNECT ON DATABASE postgres"}) {
                productionOwner.execute("GRANT " + grant + " TO facilityflow_prod_backup");
                assertThrows(DataAccessException.class, () -> productionOwner.execute(backupSetup), grant);
                productionOwner.execute("REVOKE " + grant + " FROM facilityflow_prod_backup");
                productionOwner.execute(backupSetup);
            }
            // Shared ACL dependencies also expose grants in another database.
            admin.execute("GRANT SELECT ON facilityflow.user_account TO facilityflow_prod_backup");
            assertThrows(DataAccessException.class, () -> productionOwner.execute(backupSetup));
            admin.execute("REVOKE SELECT ON facilityflow.user_account FROM facilityflow_prod_backup");
            productionOwner.execute(backupSetup);

            // A later migration's revocations must remove stale Production grants.
            migrator.execute("REVOKE UPDATE (display_name) ON facilityflow.user_account FROM facilityflow_app");
            migrator.execute("REVOKE DELETE ON facilityflow.spring_session_attributes FROM facilityflow_app");
            migrator.execute("REVOKE USAGE ON SEQUENCE facilityflow.request_display_number_seq FROM facilityflow_app");
            migrator.execute(permissions);
            assertThrows(DataAccessException.class, () -> prod.execute(
                    "UPDATE facilityflow.user_account SET display_name = 'Must be denied'"));
            assertThrows(DataAccessException.class, () -> prod.execute("DELETE FROM facilityflow.spring_session_attributes"));
            assertThrows(DataAccessException.class, () -> prod.queryForObject(
                    "SELECT nextval('facilityflow.request_display_number_seq')", Long.class));
            assertEquals(1L, prod.queryForObject("SELECT count(*) FROM facilityflow.user_account", Long.class));

            var prodInDev = new JdbcTemplate(source(postgres, "postgres", "facilityflow_prod_app"));
            assertThrows(DataAccessException.class,
                    () -> prodInDev.queryForList("SELECT * FROM facilityflow.user_account"));
            var devInProd = new JdbcTemplate(source(postgres, "facilityflow_prod", "facilityflow_app"));
            assertThrows(DataAccessException.class,
                    () -> devInProd.queryForList("SELECT * FROM facilityflow.user_account"));
            assertThrows(DataAccessException.class, () -> new JdbcTemplate(
                    source(postgres, "postgres", "facilityflow_migrator")).execute(permissions));
        }
    }

    private static void migrate(PGSimpleDataSource source) {
        assertEquals(6, Flyway.configure().dataSource(source).schemas("facilityflow")
                .defaultSchema("facilityflow").cleanDisabled(true).load().migrate().migrationsExecuted);
    }

    private static PGSimpleDataSource source(EmbeddedPostgres postgres, String database, String user) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName(database);
        source.setUser(user);
        return source;
    }
}
