package sg.edu.nus.facilityflow.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;

class ManagerRepositoryIntegrationTest {
    private static EmbeddedPostgres postgres;
    private static JdbcTemplate admin;
    private static JdbcTemplate app;
    private static DataSource appDataSource;

    @BeforeAll
    static void database() throws Exception {
        postgres = EmbeddedPostgres.start();
        var adminDataSource = postgres.getPostgresDatabase();
        admin = new JdbcTemplate(adminDataSource);
        try (var connection = adminDataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        Flyway.configure().dataSource(dataSource("facilityflow_migrator"))
                .schemas("facilityflow").defaultSchema("facilityflow")
                .cleanDisabled(true).ignoreMigrationPatterns(new String[0])
                .target("4").load().migrate();
        appDataSource = dataSource("facilityflow_app");
        app = new JdbcTemplate(appDataSource);
    }

    @AfterAll
    static void close() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    @DisplayName("REQ-MGR-003 / REQ-SH-006/014: on-behalf creation excludes inactive Requesters and audits atomically")
    void createsOnBehalfForActiveRequesterAndRollsBackOnAuditFailure() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var inactive = account("REQUESTER", false);
        var service = service(manager, AccountRole.FACILITIES_MANAGER);
        var options = service.creationOptions();
        assertTrue(options.requesters().stream().anyMatch(option -> option.id().equals(requester)));
        assertFalse(options.requesters().stream().anyMatch(option -> option.id().equals(inactive)));

        var draft = draft(requester);
        var displayId = service.createOnBehalf(draft);
        var row = app.queryForMap("SELECT requester_id, recorded_by, status FROM facilityflow.maintenance_request WHERE display_id = ?", displayId);
        assertEquals(requester, row.get("requester_id"));
        assertEquals(manager, row.get("recorded_by"));
        assertEquals("OPEN", row.get("status"));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE target_id = "
                + "(SELECT id FROM facilityflow.maintenance_request WHERE display_id = ?) "
                + "AND actor_id = ? AND action = 'REQUEST_RECORDED_ON_BEHALF'", Integer.class, displayId, manager));

        assertThrows(ManagerValidationException.class,
                () -> service.createOnBehalf(new ManagerRequestDraft(inactive.toString(), draft.title(),
                        draft.description(), draft.location(), draft.categoryId(), draft.reportedUrgency())));

        long requestsBefore = app.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Long.class);
        admin.execute("ALTER TABLE facilityflow.audit_event ADD CONSTRAINT reject_manager_create "
                + "CHECK (action <> 'REQUEST_RECORDED_ON_BEHALF') NOT VALID");
        try {
            assertThrows(DataAccessException.class, () -> service.createOnBehalf(draft));
        } finally {
            admin.execute("ALTER TABLE facilityflow.audit_event DROP CONSTRAINT reject_manager_create");
        }
        assertEquals(requestsBefore,
                app.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Long.class));
    }

    @Test
    @DisplayName("REQ-MGR-001/002/010: queue ordering, literal search and workloads use persisted data")
    void listsRequestsInManagerOrderAndCalculatesWorkload() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var technician = account("TECHNICIAN", true);
        var inactiveTechnician = account("TECHNICIAN", false);
        var low = createRequest(requester, manager, "LOW", "Low urgency pump");
        var emergency = createRequest(requester, manager, "EMERGENCY", "Emergency 100% pump");
        app.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED', manager_priority = 'HIGH', "
                + "assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP WHERE id = ?", technician, low);

        var service = service(manager, AccountRole.FACILITIES_MANAGER);
        var page = service.requests(ManagerRequestFilters.empty());
        var rows = page.rows();
        assertTrue(page.hasRequests());
        assertTrue(page.technicians().stream().anyMatch(option -> option.id().equals(inactiveTechnician)
                && option.label().contains("inactive")));
        assertEquals(emergency, requestId(rows.getFirst().displayId()));
        assertTrue(service.requests(new ManagerRequestFilters("100%", "", "", "", "", "", ""))
                .rows().stream().allMatch(row -> row.title().contains("100%")));
        assertEquals(1L, service.overview().technicianWorkloads().stream()
                .filter(item -> item.id().equals(technician)).findFirst().orElseThrow().activeRequests());
    }

    @Test
    @DisplayName("REQ-MGR-011 / MP2 regression: action filtering works and non-Request targets never receive a fake Request ID")
    void filtersAuditActionsAndPreservesMissingRequestAssociation() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var request = createRequest(requester, manager, "NORMAL", "Audit filter request");
        app.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id) "
                + "VALUES (?, 'REQUEST_ASSIGNED', 'REQUEST', ?)", manager, request);
        app.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id) "
                + "VALUES (?, 'ACCOUNT_CREATED', 'ACCOUNT', ?)", manager, requester);

        var service = service(manager, AccountRole.FACILITIES_MANAGER);
        var assigned = service.audit(new ManagerAuditFilters("", "", "assigned", "", "")).rows();
        assertEquals(1, assigned.size());
        assertEquals(requestId(assigned.getFirst().requestDisplayId()), request);
        var accounts = service.audit(new ManagerAuditFilters("", "", "account_created", "", "")).rows();
        assertEquals(1, accounts.size());
        assertEquals(null, accounts.getFirst().requestDisplayId());
    }

    @Test
    @DisplayName("REQ-SH-002: Manager use cases reject a non-Manager even when called directly")
    void rejectsWrongRoleAtUseCaseSeam() {
        var requester = account("REQUESTER", true);
        assertThrows(AccessDeniedException.class,
                () -> service(requester, AccountRole.REQUESTER).requests(ManagerRequestFilters.empty()));
    }

    @Test
    @DisplayName("REQ-SH-002: persisted role and active state are rechecked after a stale Manager principal")
    void rejectsStaleManagerPrincipalAgainstPersistedAccount() {
        var persistedRequester = account("REQUESTER", true);
        var inactiveManager = account("FACILITIES_MANAGER", false);
        assertThrows(AccessDeniedException.class,
                () -> service(persistedRequester, AccountRole.FACILITIES_MANAGER)
                        .requests(ManagerRequestFilters.empty()));
        assertThrows(AccessDeniedException.class,
                () -> service(inactiveManager, AccountRole.FACILITIES_MANAGER)
                        .requests(ManagerRequestFilters.empty()));
    }

    private static ManagerService service(UUID id, AccountRole role) {
        String username = app.queryForObject("SELECT username FROM facilityflow.user_account WHERE id = ?", String.class, id);
        var account = new CurrentAccount(id, username, "Synthetic Account", role);
        var repository = new JdbcManagerRepository(new NamedParameterJdbcTemplate(appDataSource));
        return new ManagerService(() -> account, repository, new DataSourceTransactionManager(appDataSource));
    }

    private static ManagerRequestDraft draft(UUID requester) {
        UUID category = app.queryForObject("SELECT id FROM facilityflow.category WHERE name = 'Plumbing'", UUID.class);
        return new ManagerRequestDraft(requester.toString(), "Leaking water pipe",
                "Water is leaking continuously below the sink.", "Block A level 2", category.toString(), "HIGH");
    }

    private static UUID account(String role, boolean active) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        return app.queryForObject("INSERT INTO facilityflow.user_account "
                + "(username, display_name, password_hash, role, active) VALUES (?, 'Synthetic Account', 'synthetic', ?, ?) RETURNING id",
                UUID.class, "fixture-" + suffix, role, active);
    }

    private static UUID createRequest(UUID requester, UUID manager, String urgency, String title) {
        return app.queryForObject("INSERT INTO facilityflow.maintenance_request "
                + "(requester_id, recorded_by, title, description, location, category_id, reported_urgency) "
                + "VALUES (?, ?, ?, 'Synthetic request description', 'Block A', "
                + "(SELECT id FROM facilityflow.category WHERE name = 'Electrical'), ?) RETURNING id",
                UUID.class, requester, manager, title, urgency);
    }

    private static UUID requestId(String displayId) {
        return app.queryForObject("SELECT id FROM facilityflow.maintenance_request WHERE display_id = ?", UUID.class, displayId);
    }

    private static DataSource dataSource(String role) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"});
        source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres");
        source.setUser(role);
        return source;
    }
}
