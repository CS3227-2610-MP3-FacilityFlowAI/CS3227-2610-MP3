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
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
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

    @Test
    @DisplayName("REQ-MGR-004–009/012/025: named lifecycle operations enforce state, version and active-Technician rules")
    void performsNamedLifecycleOperationsWithOptimisticChecks() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var first = account("TECHNICIAN", true);
        var second = account("TECHNICIAN", true);
        var inactive = account("TECHNICIAN", false);
        var requestId = createRequest(requester, manager, "HIGH", "Lifecycle pump failure");
        var service = service(manager, AccountRole.FACILITIES_MANAGER);
        String displayId = displayId(requestId);

        service.assign(displayId, new ManagerRequestCommands.Assignment("0", first.toString(), "CRITICAL"));
        assertRequest(requestId, "ASSIGNED", first, 1L);
        assertThrows(ManagerValidationException.class,
                () -> service.reassign(displayId, new ManagerRequestCommands.Reassignment(
                        "0", second.toString(), "Workload balancing")));
        assertThrows(ManagerValidationException.class,
                () -> service.reassign(displayId, new ManagerRequestCommands.Reassignment(
                        "1", inactive.toString(), "Workload balancing")));

        service.reassign(displayId, new ManagerRequestCommands.Reassignment(
                "1", second.toString(), "Technician has the required access"));
        assertRequest(requestId, "ASSIGNED", second, 2L);
        service.cancel(displayId, new ManagerRequestCommands.Cancellation("2", "Duplicate report", "true"));
        assertRequest(requestId, "CANCELLED", second, 3L);
        assertThrows(ManagerValidationException.class,
                () -> service.cancel(displayId, new ManagerRequestCommands.Cancellation("3", "Again cancelled", "true")));
        assertEquals(3, app.queryForObject("SELECT count(*) FROM facilityflow.audit_event "
                + "WHERE target_id = ? AND action IN ('REQUEST_ASSIGNED','REQUEST_REASSIGNED','REQUEST_CANCELLED')",
                Integer.class, requestId));
    }

    @Test
    @DisplayName("REQ-MGR-024/025: approved triage uses the ordinary locked assignment operation")
    void savesHumanApprovedTriageThroughDeterministicAssignment() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var technician = account("TECHNICIAN", true);
        var requestId = createRequest(requester, manager, "HIGH", "Approved triage test");
        UUID category = app.queryForObject("SELECT id FROM facilityflow.category WHERE name = 'Safety'", UUID.class);
        UUID skill = app.queryForObject("INSERT INTO facilityflow.skill_tag (name) "
                + "VALUES ('Emergency isolation') RETURNING id", UUID.class);
        app.update("INSERT INTO facilityflow.technician_skill (technician_id, skill_tag_id) VALUES (?, ?)",
                technician, skill);
        var service = service(manager, AccountRole.FACILITIES_MANAGER);
        String fingerprint = triageFingerprint();

        service.saveTriage(displayId(requestId), new ManagerRequestCommands.TriageDecision(
                "0", fingerprint, category.toString(), "CRITICAL", technician.toString(),
                java.util.List.of("Emergency isolation"), java.util.List.of()));
        assertRequest(requestId, "ASSIGNED", technician, 1L);
        var row = app.queryForMap("SELECT category_id, manager_priority FROM facilityflow.maintenance_request WHERE id = ?",
                requestId);
        assertEquals(category, row.get("category_id"));
        assertEquals("CRITICAL", row.get("manager_priority"));
        assertEquals("true", app.queryForObject("SELECT details ->> 'triageReviewed' FROM facilityflow.audit_event "
                + "WHERE target_id = ? AND action = 'REQUEST_ASSIGNED'", String.class, requestId));
        assertThrows(ManagerValidationException.class, () -> service.saveTriage(displayId(requestId),
                new ManagerRequestCommands.TriageDecision("0", fingerprint, category.toString(), "HIGH",
                        technician.toString(), java.util.List.of(), java.util.List.of())));

        UUID staleRequest = createRequest(requester, manager, "NORMAL", "Stale triage catalogue test");
        String staleFingerprint = triageFingerprint();
        app.update("UPDATE facilityflow.skill_tag SET name = 'Emergency power isolation', "
                + "version = version + 1 WHERE id = ?", skill);
        assertThrows(ManagerValidationException.class, () -> service.saveTriage(displayId(staleRequest),
                new ManagerRequestCommands.TriageDecision("0", staleFingerprint, category.toString(), "HIGH",
                        technician.toString(), java.util.List.of("Emergency isolation"), java.util.List.of())));
        assertRequest(staleRequest, "OPEN", null, 0L);
    }

    @Test
    @DisplayName("REQ-MGR-007–009: close, return, reopen and correction preserve historical work and completion evidence")
    void reviewsReopensAndCorrectsWithoutErasingHistory() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var technician = account("TECHNICIAN", true);
        var requestId = createRequest(requester, manager, "NORMAL", "Completed lighting repair");
        String displayId = displayId(requestId);
        app.update("UPDATE facilityflow.maintenance_request SET status = 'COMPLETED', manager_priority = 'MEDIUM', "
                + "assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP, resolution_summary = 'Replaced failed light fitting', "
                + "completed_by = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?", technician, technician, requestId);
        app.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) "
                + "VALUES (?, ?, 'Isolated power and replaced fitting', 25)", requestId, technician);
        app.update("INSERT INTO facilityflow.request_completion_history "
                + "(request_id, technician_id, resolution_summary) VALUES (?, ?, 'Replaced failed light fitting')",
                requestId, technician);
        var service = service(manager, AccountRole.FACILITIES_MANAGER);

        service.returnCompleted(displayId, new ManagerRequestCommands.ReturnCompleted(
                "0", technician.toString(), "Please add a safety test result"));
        assertRequest(requestId, "ASSIGNED", technician, 1L);
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?",
                Integer.class, requestId));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.request_completion_history WHERE request_id = ?",
                Integer.class, requestId));
        assertEquals(null, app.queryForObject("SELECT resolution_summary FROM facilityflow.maintenance_request WHERE id = ?",
                String.class, requestId));

        app.update("UPDATE facilityflow.maintenance_request SET status = 'COMPLETED', "
                + "resolution_summary = 'Retested fitting and recorded safety result', completed_by = ?, "
                + "completed_at = CURRENT_TIMESTAMP WHERE id = ?", technician, requestId);
        service.close(displayId, "1");
        assertRequest(requestId, "CLOSED", technician, 2L);
        service.reopen(displayId, new ManagerRequestCommands.Reopen(
                "2", technician.toString(), "Requester reports the light failed again"));
        assertRequest(requestId, "ASSIGNED", technician, 3L);

        UUID category = app.queryForObject("SELECT id FROM facilityflow.category WHERE name = 'Safety'", UUID.class);
        service.correct(displayId, new ManagerRequestCommands.Correction("3", "Recurring lighting repair",
                "The repaired fitting failed again after completion.", "Block B level 3", category.toString(),
                "HIGH", "HIGH", "Corrected location and recurring issue details"));
        assertEquals("Recurring lighting repair", app.queryForObject(
                "SELECT title FROM facilityflow.maintenance_request WHERE id = ?", String.class, requestId));
        assertRequest(requestId, "ASSIGNED", technician, 4L);
        assertEquals(1, service.request(displayId).workLogs().size());
        assertEquals(1, service.request(displayId).completions().size());
    }

    @Test
    @DisplayName("REQ-MGR-004 / REQ-SH-006/014: assignment and audit are one rollback-safe transaction")
    void rollsBackLifecycleMutationWhenAuditFails() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var technician = account("TECHNICIAN", true);
        var requestId = createRequest(requester, manager, "NORMAL", "Rollback assignment test");
        var service = service(manager, AccountRole.FACILITIES_MANAGER);
        admin.execute("ALTER TABLE facilityflow.audit_event ADD CONSTRAINT reject_manager_assign "
                + "CHECK (action <> 'REQUEST_ASSIGNED') NOT VALID");
        try {
            assertThrows(DataAccessException.class, () -> service.assign(displayId(requestId),
                    new ManagerRequestCommands.Assignment("0", technician.toString(), "MEDIUM")));
        } finally {
            admin.execute("ALTER TABLE facilityflow.audit_event DROP CONSTRAINT reject_manager_assign");
        }
        assertRequest(requestId, "OPEN", null, 0L);
    }

    @Test
    @DisplayName("REQ-MGR-013–015: account administration prevents self changes and active-Technician removal")
    void enforcesAccountAdministrationGuardsAndInvalidatesSessions() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var technician = account("TECHNICIAN", true);
        var requestId = createRequest(requester, manager, "HIGH", "Assigned account guard test");
        app.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED', manager_priority = 'HIGH', "
                + "assigned_technician_id = ?, assigned_at = CURRENT_TIMESTAMP WHERE id = ?", technician, requestId);
        var service = adminService(manager, AccountRole.FACILITIES_MANAGER);

        assertThrows(ManagerValidationException.class, () -> service.setAccountState(manager,
                new ManagerAdminCommands.AccountState("0", "false", "true")));
        assertThrows(ManagerValidationException.class, () -> service.setAccountState(technician,
                new ManagerAdminCommands.AccountState("0", "false", "true")));
        assertThrows(ManagerValidationException.class, () -> service.changeAccountRole(technician,
                new ManagerAdminCommands.AccountRole("0", "REQUESTER")));

        service.resetPassword(requester, new ManagerAdminCommands.PasswordReset("0", "Replacement123"));
        assertEquals(1L, app.queryForObject("SELECT session_version FROM facilityflow.user_account WHERE id = ?",
                Long.class, requester));
        assertEquals(1L, app.queryForObject("SELECT version FROM facilityflow.user_account WHERE id = ?",
                Long.class, requester));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.audit_event "
                + "WHERE target_id = ? AND action = 'ACCOUNT_PASSWORD_RESET'", Integer.class, requester));

        service.changeAccountRole(requester, new ManagerAdminCommands.AccountRole("1", "TECHNICIAN"));
        assertEquals("TECHNICIAN", app.queryForObject("SELECT role FROM facilityflow.user_account WHERE id = ?",
                String.class, requester));
        assertEquals(1L, app.queryForObject("SELECT session_version FROM facilityflow.user_account WHERE id = ?",
                Long.class, requester));
    }

    @Test
    @DisplayName("REQ-MGR-016/017: category removal migrates requests and audits atomically")
    void managesCategoriesWithAtomicReplacementMigration() {
        var manager = account("FACILITIES_MANAGER", true);
        var requester = account("REQUESTER", true);
        var requestId = createRequest(requester, manager, "NORMAL", "Category migration test");
        var service = adminService(manager, AccountRole.FACILITIES_MANAGER);
        service.createCategory("Water Systems");
        UUID source = app.queryForObject("SELECT id FROM facilityflow.category WHERE name = 'Water Systems'", UUID.class);
        UUID replacement = app.queryForObject("SELECT id FROM facilityflow.category WHERE name = 'Plumbing'", UUID.class);
        app.update("UPDATE facilityflow.maintenance_request SET category_id = ? WHERE id = ?", source, requestId);

        admin.execute("ALTER TABLE facilityflow.audit_event ADD CONSTRAINT reject_category_remove "
                + "CHECK (action <> 'CATEGORY_REMOVED') NOT VALID");
        try {
            assertThrows(DataAccessException.class, () -> service.removeCategory(source,
                    new ManagerAdminCommands.CategoryRemoval("0", replacement.toString(), "true")));
        } finally {
            admin.execute("ALTER TABLE facilityflow.audit_event DROP CONSTRAINT reject_category_remove");
        }
        assertTrue(app.queryForObject("SELECT active FROM facilityflow.category WHERE id = ?", Boolean.class, source));
        assertEquals(source, app.queryForObject("SELECT category_id FROM facilityflow.maintenance_request WHERE id = ?",
                UUID.class, requestId));

        service.removeCategory(source, new ManagerAdminCommands.CategoryRemoval("0", replacement.toString(), "true"));
        assertFalse(app.queryForObject("SELECT active FROM facilityflow.category WHERE id = ?", Boolean.class, source));
        assertEquals(replacement, app.queryForObject("SELECT category_id FROM facilityflow.maintenance_request WHERE id = ?",
                UUID.class, requestId));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.audit_event "
                + "WHERE target_id = ? AND action = 'REQUEST_CATEGORY_MIGRATED'", Integer.class, requestId));
    }

    @Test
    @DisplayName("REQ-MGR-018/019: skill deactivation preserves historical Technician links")
    void managesSkillsAndPreservesInactiveAssociations() {
        var manager = account("FACILITIES_MANAGER", true);
        var technician = account("TECHNICIAN", true);
        var service = adminService(manager, AccountRole.FACILITIES_MANAGER);
        service.createSkill("Electrical isolation");
        UUID skill = app.queryForObject("SELECT id FROM facilityflow.skill_tag WHERE name = 'Electrical isolation'", UUID.class);
        service.updateTechnicianSkills(technician,
                new ManagerAdminCommands.TechnicianSkills("0", java.util.List.of(skill.toString())));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.technician_skill "
                + "WHERE technician_id = ? AND skill_tag_id = ?", Integer.class, technician, skill));

        service.deactivateSkill(skill, new ManagerAdminCommands.SkillDeactivation("0", "true"));
        assertFalse(app.queryForObject("SELECT active FROM facilityflow.skill_tag WHERE id = ?", Boolean.class, skill));
        assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.technician_skill "
                + "WHERE technician_id = ? AND skill_tag_id = ?", Integer.class, technician, skill));
        assertFalse(service.skills().technicians().getFirst().activeSkillIds().contains(skill));
    }

    private static ManagerService service(UUID id, AccountRole role) {
        String username = app.queryForObject("SELECT username FROM facilityflow.user_account WHERE id = ?", String.class, id);
        var account = new CurrentAccount(id, username, "Synthetic Account", role);
        var repository = new JdbcManagerRepository(new NamedParameterJdbcTemplate(appDataSource));
        return new ManagerService(() -> account, repository, new DataSourceTransactionManager(appDataSource));
    }

    private static ManagerAdminService adminService(UUID id, AccountRole role) {
        String username = app.queryForObject("SELECT username FROM facilityflow.user_account WHERE id = ?", String.class, id);
        var account = new CurrentAccount(id, username, "Synthetic Account", role);
        var repository = new JdbcManagerAdminRepository(new NamedParameterJdbcTemplate(appDataSource));
        return new ManagerAdminService(() -> account, repository,
                Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                new DataSourceTransactionManager(appDataSource));
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

    private static String displayId(UUID requestId) {
        return app.queryForObject("SELECT display_id FROM facilityflow.maintenance_request WHERE id = ?",
                String.class, requestId);
    }

    private static String triageFingerprint() {
        return new JdbcManagerRepository(new NamedParameterJdbcTemplate(appDataSource))
                .triageSnapshotFingerprint();
    }

    private static void assertRequest(UUID requestId, String status, UUID technician, long version) {
        var row = app.queryForMap("SELECT status, assigned_technician_id, version FROM facilityflow.maintenance_request WHERE id = ?",
                requestId);
        assertEquals(status, row.get("status"));
        assertEquals(technician, row.get("assigned_technician_id"));
        assertEquals(version, ((Number) row.get("version")).longValue());
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
