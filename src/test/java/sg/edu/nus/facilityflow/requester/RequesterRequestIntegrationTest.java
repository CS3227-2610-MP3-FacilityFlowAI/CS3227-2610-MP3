package sg.edu.nus.facilityflow.requester;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.StandardServletEnvironment;
import sg.edu.nus.facilityflow.FacilityFlowApplication;
import sg.edu.nus.facilityflow.identity.*;
import sg.edu.nus.facilityflow.operations.SchemaReadinessCheck;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;
import sg.edu.nus.facilityflow.requestcore.RequestValidationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequesterRequestIntegrationTest {
    private static final UUID OWNER = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID MANAGER = UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final UUID TECHNICIAN = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private EmbeddedPostgres postgres;
    private ConfigurableWebApplicationContext context;
    private JdbcTemplate admin;
    private JdbcTemplate app;
    private MockMvc mvc;
    private String passwordHash;
    private String category;

    @BeforeAll
    void startIsolatedApplication() throws Exception {
        postgres = EmbeddedPostgres.start();
        admin = new JdbcTemplate(postgres.getPostgresDatabase());
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        var migrator = new PGSimpleDataSource();
        migrator.setServerNames(new String[]{"127.0.0.1"});
        migrator.setPortNumbers(new int[]{postgres.getPort()});
        migrator.setDatabaseName("postgres");
        migrator.setUser("facilityflow_migrator");
        Flyway.configure().dataSource(migrator).schemas("facilityflow").defaultSchema("facilityflow")
                .ignoreMigrationPatterns(new String[0]).load().migrate();

        var application = new SpringApplication(FacilityFlowApplication.class);
        var environment = new StandardServletEnvironment();
        environment.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        context = (ConfigurableWebApplicationContext) application.run(
                "--spring.profiles.active=test", "--spring.flyway.enabled=false",
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres",
                "--spring.datasource.username=facilityflow_app", "--spring.datasource.password=",
                "--spring.datasource.hikari.data-source-properties.sslmode=disable",
                "--spring.datasource.hikari.data-source-properties.sslrootcert=synthetic-test.crt");
        app = context.getBean(JdbcTemplate.class);
        passwordHash = context.getBean(PasswordEncoder.class).encode("Synthetic123");
        category = app.queryForObject("SELECT id::text FROM facilityflow.category WHERE name = 'Electrical'", String.class);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSessionRepositoryFilter", jakarta.servlet.Filter.class))
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @AfterAll
    void close() throws Exception {
        if (context != null) context.close();
        if (postgres != null) postgres.close();
    }

    @BeforeEach
    void seed() {
        admin.execute("TRUNCATE facilityflow.user_account CASCADE");
        app.update("DELETE FROM facilityflow.spring_session");
        admin.update("UPDATE facilityflow.category SET active = true");
        for (var account : List.of(new Object[]{OWNER, "requester1", "REQUESTER"},
                new Object[]{OTHER, "requester2", "REQUESTER"}, new Object[]{MANAGER, "manager1", "FACILITIES_MANAGER"},
                new Object[]{TECHNICIAN, "technician1", "TECHNICIAN"})) {
            app.update("INSERT INTO facilityflow.user_account (id, username, display_name, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                    account[0], account[1], account[1], passwordHash, account[2]);
        }
    }

    @Test
    void reqRq002005CreateListDetailIgnoresForgedAuthorityAndEncodesOutput() throws Exception {
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests").cookie(cookie)).andExpect(content().string(containsString("You have no requests yet")));
        String command = formToken(cookie);
        var result = mvc.perform(post("/requester/requests").cookie(cookie).with(csrf())
                        .param("submissionId", command).param("title", "  Broken light  ")
                        .param("description", "<script>alert('x')</script>\n  Follow-up line")
                        .param("location", " Block A ").param("categoryId", category).param("reportedUrgency", "HIGH")
                        .param("requesterId", OTHER.toString()).param("recordedBy", MANAGER.toString())
                        .param("status", "CLOSED").param("managerPriority", "CRITICAL").param("displayId", "FF-999999"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String destination = result.getResponse().getRedirectedUrl();
        assertNotNull(destination);
        mvc.perform(get(destination).cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Broken light")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("Asia/Singapore")))
                .andExpect(content().string(containsString("Request submitted")));
        mvc.perform(get("/requester/requests").cookie(cookie)).andExpect(content().string(containsString("Broken light")));
        var row = app.queryForMap("SELECT requester_id, recorded_by, status, manager_priority FROM facilityflow.maintenance_request");
        assertEquals(OWNER, row.get("requester_id"));
        assertEquals(OWNER, row.get("recorded_by"));
        assertEquals("OPEN", row.get("status"));
        assertNull(row.get("manager_priority"));
        assertEquals(1, count("maintenance_request"));
        assertEquals(1, requestAuditCount());
        mvc.perform(post("/requester/requests").cookie(cookie).with(csrf())
                        .param("submissionId", command).param("title", "Broken light").param("description", "Valid description")
                        .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "NORMAL"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("already been submitted")));
        assertEquals(1, count("maintenance_request"));
        assertEquals(1, requestAuditCount());
    }

    @Test
    void reqRq002011FieldErrorsRetainInputAndInactiveCategoryCreatesNothing() throws Exception {
        Cookie cookie = login("requester1", "/requester");
        String command = formToken(cookie);
        mvc.perform(post("/requester/requests").cookie(cookie).with(csrf())
                        .param("submissionId", command).param("title", "Tiny").param("description", "Keep this description")
                        .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "CRITICAL"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("Keep this description")))
                .andExpect(content().string(containsString("title-error")))
                .andExpect(content().string(containsString(command)));
        admin.update("UPDATE facilityflow.category SET active = false WHERE id = ?::uuid", category);
        mvc.perform(post("/requester/requests").cookie(cookie).with(csrf())
                        .param("submissionId", command).param("title", "Broken light").param("description", "Keep this description")
                        .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "NORMAL"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("no longer active")))
                .andExpect(content().string(containsString("Previous selection unavailable")));
        assertEquals(0, count("maintenance_request"));
        assertEquals(0, count("request_submission"));
        assertEquals(0, requestAuditCount());
    }

    @Test
    void reqRq009OwnScopeIncludesManagerRecordedAndHidesPrivateHistory() throws Exception {
        var owned = managerRecorded(OWNER, "Reported by manager");
        var foreign = managerRecorded(OTHER, "Foreign secret title");
        UUID request = app.queryForObject("SELECT id FROM facilityflow.maintenance_request WHERE display_id = ?", UUID.class, owned);
        app.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'PRIVATE WORK LOG', 5)", request, TECHNICIAN);
        app.update("INSERT INTO facilityflow.requester_update (request_id, author_id, note) VALUES (?, ?, 'Visible requester update')", request, OWNER);
        app.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details) "
                + "VALUES (?, 'MANAGER_CORRECTION', 'REQUEST', ?, '{\"reason\":\"PRIVATE MANAGER DATA\"}')", MANAGER, request);
        app.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details) "
                + "VALUES (?, 'REQUEST_CANCELLED', 'REQUEST', ?, '{\"reason\":\"Visible cancellation reason\",\"private\":\"PRIVATE STRUCTURED DATA\"}')", MANAGER, request);
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests").cookie(cookie))
                .andExpect(content().string(containsString("Reported by manager")))
                .andExpect(content().string(not(containsString("Foreign secret title"))));
        var detail = service(OWNER, AccountRole.REQUESTER, 0).detail(owned).orElseThrow();
        assertEquals(2, detail.history().size());
        assertTrue(detail.history().stream().anyMatch(entry -> entry.note().equals("Visible cancellation reason")));
        assertFalse(detail.toString().contains("PRIVATE"));
        mvc.perform(get("/requester/requests/" + owned).cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Visible requester update")))
                .andExpect(content().string(not(containsString("PRIVATE"))));
        String denied = mvc.perform(get("/requester/requests/" + foreign).cookie(cookie)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String absent = mvc.perform(get("/requester/requests/FF-000000").cookie(cookie)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals(absent, denied);
    }

    @Test
    void reqRq010AndNfrSec001DenyWrongRoleCsrfAndForgedLifecycleRoutes() throws Exception {
        Cookie technician = login("technician1", "/technician");
        mvc.perform(get("/requester/requests/new").cookie(technician)).andExpect(status().isForbidden());
        mvc.perform(post("/requester/requests").cookie(technician).with(csrf())).andExpect(status().isForbidden());
        Cookie manager = login("manager1", "/manager");
        mvc.perform(get("/requester/requests").cookie(manager)).andExpect(status().isForbidden());
        Cookie requester = login("requester1", "/requester");
        mvc.perform(post("/requester/requests").cookie(requester)).andExpect(status().isForbidden());
        mvc.perform(post("/manager/requests/FF-000001/assign").cookie(requester).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/technician/requests/FF-000001/complete").cookie(requester).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/about").cookie(requester)).andExpect(status().isOk());
        mvc.perform(get("/requester").cookie(requester)).andExpect(content().string(containsString("My requests")));
        assertEquals(0, count("maintenance_request"));
    }

    @Test
    void reqRq011AuditFailureRollsBackAndSameFormCanRetry() throws Exception {
        Cookie cookie = login("requester1", "/requester");
        String command = formToken(cookie);
        admin.execute("ALTER TABLE facilityflow.audit_event ADD CONSTRAINT reject_create_test CHECK (action <> 'REQUEST_CREATED')");
        try {
            mvc.perform(post("/requester/requests").cookie(cookie).with(csrf())
                            .param("submissionId", command).param("title", "Broken light").param("description", "Keep this description")
                            .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "NORMAL"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(containsString("Keep this description")))
                    .andExpect(content().string(not(containsString("reject_create_test"))));
            assertEquals(0, count("maintenance_request"));
            assertEquals(0, count("request_submission"));
            assertEquals(0, requestAuditCount());
        } finally {
            admin.execute("ALTER TABLE facilityflow.audit_event DROP CONSTRAINT reject_create_test");
        }
        service(OWNER, AccountRole.REQUESTER, 0).create(draft(), command);
        assertEquals(1, count("maintenance_request"));
        assertEquals(1, requestAuditCount());
    }

    @Test
    void reqRq002ConcurrentSubmissionCreatesOneRequestAndOneAudit() throws Exception {
        var service = service(OWNER, AccountRole.REQUESTER, 0);
        String command = UUID.randomUUID().toString();
        Callable<Boolean> submit = () -> {
            try { service.create(draft(), command); return true; }
            catch (DuplicateSubmissionException exception) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(java.util.Collections.nCopies(8, submit));
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, count("maintenance_request"));
        assertEquals(1, count("request_submission"));
        assertEquals(1, requestAuditCount());
    }

    @Test
    void reqSh002RechecksAccountAtUseCaseSeamAndCategoryBeforeCreate() {
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        app.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, requester::list);
        assertThrows(AccessDeniedException.class, () -> requester.create(draft(), UUID.randomUUID().toString()));
        app.update("UPDATE facilityflow.user_account SET active = true, session_version = 1 WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, requester::categories);
        app.update("UPDATE facilityflow.user_account SET session_version = 0, role = 'TECHNICIAN' WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, requester::list);
        assertThrows(AccessDeniedException.class, () -> service(MANAGER, AccountRole.FACILITIES_MANAGER, 0).list());
        app.update("UPDATE facilityflow.user_account SET role = 'REQUESTER' WHERE id = ?", OWNER);
        assertThrows(RequestValidationException.class, () -> requester.create(
                new RequestDraft("Broken light", "Valid report description", "Block A", UUID.randomUUID().toString(), "NORMAL"),
                UUID.randomUUID().toString()));
        assertEquals(0, count("maintenance_request"));
    }

    @Test
    void reqRq004ListTiesUseDisplayIdAscendingAndIncludeInactiveCategories() {
        var first = managerRecorded(OWNER, "First report");
        var second = managerRecorded(OWNER, "Second report");
        admin.update("UPDATE facilityflow.maintenance_request SET created_at = '2026-01-01T00:00:00Z'");
        admin.update("UPDATE facilityflow.category SET active = false WHERE id = ?::uuid", category);
        var result = service(OWNER, AccountRole.REQUESTER, 0).list();
        assertEquals(List.of(first, second), result.stream().map(RequesterRequests.Summary::displayId).toList());
    }

    @Test
    void reqRq002DisplayIdExhaustionLeavesNoPartialSecondWrite() {
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        admin.execute("ALTER SEQUENCE facilityflow.request_display_number_seq RESTART WITH 999999");
        try {
            assertEquals("FF-999999", requester.create(draft(), UUID.randomUUID().toString()));
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> requester.create(draft(), UUID.randomUUID().toString()));
            assertEquals(1, count("maintenance_request"));
            assertEquals(1, count("request_submission"));
            assertEquals(1, requestAuditCount());
        } finally {
            admin.execute("ALTER SEQUENCE facilityflow.request_display_number_seq RESTART WITH 1");
        }
    }

    @Test
    void technicalSpecRuntimeReadinessRejectsFutureOrFailedVersionsAndCannotWriteHistory() {
        var readiness = new SchemaReadinessCheck(app);
        readiness.run(null);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> app.queryForList("SELECT * FROM facilityflow.flyway_schema_history"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> app.update("UPDATE facilityflow.schema_version SET success = false WHERE version = '5'"));
        admin.update("INSERT INTO facilityflow.flyway_schema_history (installed_rank, version, description, type, script, installed_by, execution_time, success) "
                + "VALUES (99, '99', 'synthetic future', 'SQL', 'V99__test.sql', 'postgres', 0, true)");
        try { assertThrows(IllegalStateException.class, () -> readiness.run(null)); }
        finally { admin.update("DELETE FROM facilityflow.flyway_schema_history WHERE installed_rank = 99"); }
        admin.update("UPDATE facilityflow.flyway_schema_history SET success = false WHERE version = '5'");
        try { assertThrows(IllegalStateException.class, () -> readiness.run(null)); }
        finally { admin.update("UPDATE facilityflow.flyway_schema_history SET success = true WHERE version = '5'"); }
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> app.update("DELETE FROM facilityflow.request_submission"));
    }

    private RequesterRequestService service(UUID id, AccountRole role, long version) {
        return new RequesterRequestService(() -> new CurrentAccount(id, "synthetic", "Synthetic", role, version),
                context.getBean(RequesterRequestRepository.class), context.getBean(PlatformTransactionManager.class));
    }

    private RequestDraft draft() { return new RequestDraft("Broken light", "The ceiling light is flickering.", "Block A", category, "NORMAL"); }

    private int count(String table) {
        // Only fixed test-source table names are passed here.
        return app.queryForObject("SELECT count(*) FROM facilityflow." + table, Integer.class);
    }

    private int requestAuditCount() {
        return app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = 'REQUEST_CREATED'", Integer.class);
    }

    private String managerRecorded(UUID owner, String title) {
        return app.queryForObject("INSERT INTO facilityflow.maintenance_request (requester_id, recorded_by, title, description, location, category_id, reported_urgency) "
                + "VALUES (?, ?, ?, 'Recorded for the requester', 'Block A', ?::uuid, 'NORMAL') RETURNING display_id", String.class, owner, MANAGER, title, category);
    }

    private Cookie login(String username, String destination) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("username", username).param("password", "Synthetic123"))
                .andExpect(redirectedUrl(destination)).andReturn().getResponse().getCookie("SESSION");
    }

    private String formToken(Cookie cookie) throws Exception {
        var result = mvc.perform(get("/requester/requests/new").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("_csrf"))).andReturn();
        return result.getModelAndView().getModel().get("submissionId").toString();
    }
}
