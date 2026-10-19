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
import sg.edu.nus.facilityflow.ai.*;
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
    private final FakeAiTransport ai = new FakeAiTransport();

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

        startWebApplication();
    }

    private void startWebApplication() {
        var application = new SpringApplication(FacilityFlowApplication.class);
        var environment = new StandardServletEnvironment();
        environment.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        application.addInitializers(ctx -> {
            var definition = org.springframework.beans.factory.support.BeanDefinitionBuilder
                    .genericBeanDefinition(AiTransport.class, () -> ai).getBeanDefinition();
            definition.setPrimary(true);
            ((org.springframework.beans.factory.support.BeanDefinitionRegistry) ctx.getBeanFactory())
                    .registerBeanDefinition("syntheticAiTransport", definition);
        });
        context = (ConfigurableWebApplicationContext) application.run(
                "--spring.profiles.active=test", "--spring.flyway.enabled=false",
                "--AI_ENABLED=true", "--AI_TIMEOUT_SECONDS=1",
                "--server.servlet.session.cookie.secure=" + !Boolean.getBoolean("facilityflow.verifyRequester"),
                "--spring.datasource.hikari.maximum-pool-size=" + System.getProperty("facilityflow.verificationPoolSize", "2"),
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
        admin.execute("INSERT INTO facilityflow.rate_limit_bucket (scope, slot) SELECT 'GLOBAL_SLOT', generate_series(1, 5)");
        ai.reset();
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
        assertThrows(AccessDeniedException.class, () -> requester.list(RequestFilters.empty()));
        assertThrows(AccessDeniedException.class, requester::dashboard);
        assertThrows(AccessDeniedException.class, () -> requester.create(draft(), UUID.randomUUID().toString()));
        app.update("UPDATE facilityflow.user_account SET active = true, session_version = 1 WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, requester::categories);
        assertThrows(AccessDeniedException.class, requester::dashboard);
        app.update("UPDATE facilityflow.user_account SET session_version = 0, role = 'TECHNICIAN' WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, () -> requester.list(RequestFilters.empty()));
        assertThrows(AccessDeniedException.class, () -> service(MANAGER, AccountRole.FACILITIES_MANAGER, 0).list(RequestFilters.empty()));
        assertThrows(AccessDeniedException.class, requester::dashboard);
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
        var result = service(OWNER, AccountRole.REQUESTER, 0).list(RequestFilters.empty()).requests();
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

    @Test
    void reqRq006OwnOpenEditIsAuditedAndDoesNotChangeOwnershipOrAcceptStaleReplay() throws Exception {
        String id = managerRecorded(OWNER, "Manager recorded original");
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests/" + id + "/edit").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Manager recorded original")))
                .andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
        mvc.perform(post("/requester/requests/" + id + "/edit").cookie(cookie).with(csrf())
                        .param("version", "0").param("title", " Updated light ").param("description", "Updated detailed description")
                        .param("location", "Block B").param("categoryId", category).param("reportedUrgency", "EMERGENCY")
                        .param("status", "CLOSED").param("requesterId", OTHER.toString()).param("recordedBy", OWNER.toString())
                        .param("managerPriority", "CRITICAL").param("assignedTechnicianId", TECHNICIAN.toString()))
                .andExpect(redirectedUrl("/requester/requests/" + id));
        var saved = service(OWNER, AccountRole.REQUESTER, 0).detail(id).orElseThrow();
        assertEquals("Updated light", saved.title());
        assertEquals("OPEN", saved.status());
        assertEquals(1, saved.version());
        assertNull(saved.managerPriority());
        assertEquals(MANAGER, app.queryForObject("SELECT recorded_by FROM facilityflow.maintenance_request WHERE display_id = ?", UUID.class, id));
        mvc.perform(post("/requester/requests/" + id + "/edit").cookie(cookie).with(csrf())
                        .param("version", "0").param("title", "Retained stale title").param("description", "Retained stale description")
                        .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "NORMAL"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Retained stale title")))
                .andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
        assertEquals(1, mutationAudits("REQUEST_EDITED"));
        assertEquals("Updated light", service(OWNER, AccountRole.REQUESTER, 0).detail(id).orElseThrow().title());
    }

    @Test
    void reqRq007CancellationRequiresExplicitDecisionConfirmationAndReasonThenRejectsLaterWrites() throws Exception {
        String id = managerRecorded(OWNER, "Open cancellation report");
        Cookie cookie = login("requester1", "/requester");
        String page = mvc.perform(get("/requester/requests/" + id + "/cancel").cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.indexOf("value=\"keep\"") < page.indexOf("value=\"cancel\""));
        mvc.perform(post("/requester/requests/" + id + "/cancel").cookie(cookie).with(csrf())
                        .param("version", "0").param("reason", "Reason provided").param("confirmed", "true"))
                .andExpect(redirectedUrl("/requester/requests/" + id));
        assertEquals("OPEN", service(OWNER, AccountRole.REQUESTER, 0).detail(id).orElseThrow().status());
        mvc.perform(post("/requester/requests/" + id + "/cancel").cookie(cookie).with(csrf())
                        .param("decision", "cancel").param("version", "0").param("reason", "Reason provided"))
                .andExpect(status().isUnprocessableContent()).andExpect(content().string(containsString("Confirm that you want")));
        mvc.perform(post("/requester/requests/" + id + "/cancel").cookie(cookie).with(csrf())
                        .param("decision", "cancel").param("version", "0").param("confirmed", "true").param("reason", "bad"))
                .andExpect(status().isUnprocessableContent()).andExpect(content().string(containsString("Cancellation reason must contain")));
        String reason = "Problem resolved — cancellation requested";
        mvc.perform(post("/requester/requests/" + id + "/cancel").cookie(cookie).with(csrf())
                        .param("decision", "cancel").param("version", "0").param("confirmed", "true").param("reason", reason))
                .andExpect(redirectedUrl("/requester/requests/" + id));
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        var saved = requester.detail(id).orElseThrow();
        assertEquals("CANCELLED", saved.status());
        assertEquals(1, saved.version());
        assertTrue(saved.history().stream().anyMatch(entry -> entry.note().equals(reason)));
        mvc.perform(get("/requester/requests/" + id).cookie(cookie))
                .andExpect(content().string(not(containsString(">Edit request</a>"))))
                .andExpect(content().string(not(containsString(">Add Requester Update</a>"))));
        assertThrows(StaleRequestException.class, () -> requester.edit(id, draft(), "1"));
        assertThrows(StaleRequestException.class, () -> requester.cancel(id, reason, true, "1"));
        assertThrows(StaleRequestException.class, () -> requester.addUpdate(id, "Late update", "1"));
        assertEquals(1, mutationAudits("REQUEST_CANCELLED"));
        assertEquals(0, count("requester_update"));
    }

    @Test
    void reqRq008UpdatesAllowedInEveryNonTerminalStateAndReplayCreatesNoDuplicate() throws Exception {
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        Cookie cookie = login("requester1", "/requester");
        for (String state : List.of("OPEN", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED")) {
            String id = managerRecorded(OWNER, "Report for " + state);
            setState(id, state);
            String version = Long.toString(requester.detail(id).orElseThrow().version());
            if (state.equals("CLOSED") || state.equals("CANCELLED")) {
                assertThrows(StaleRequestException.class, () -> requester.addUpdate(id, "Not permitted", version));
                mvc.perform(get("/requester/requests/" + id + "/updates/new").cookie(cookie)).andExpect(status().isConflict());
            } else {
                mvc.perform(get("/requester/requests/" + id + "/updates/new").cookie(cookie)).andExpect(status().isOk());
                String note = state.equals("ASSIGNED") ? "😀".repeat(1000) : "  <script>visible update</script>  ";
                mvc.perform(post("/requester/requests/" + id + "/updates").cookie(cookie).with(csrf())
                                .param("version", version).param("note", note).param("authorId", OTHER.toString()))
                        .andExpect(redirectedUrl("/requester/requests/" + id));
                assertThrows(StaleRequestException.class, () -> requester.addUpdate(id, note, version));
                var updated = requester.detail(id).orElseThrow();
                assertEquals(state, updated.status());
                assertTrue(updated.history().stream().anyMatch(entry -> entry.note().equals(note.strip())));
            }
        }
        assertEquals(4, count("requester_update"));
        assertEquals(4, mutationAudits("REQUESTER_UPDATE_ADDED"));
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.requester_update WHERE author_id <> ?", Integer.class, OWNER));
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = 'REQUESTER_UPDATE_ADDED' AND details::text LIKE '%visible update%'", Integer.class));
    }

    @Test
    void reqRq006011AssignmentAndCategoryChangesBetweenLoadAndSaveCannotBeBypassed() throws Exception {
        Cookie cookie = login("requester1", "/requester");
        String assigned = managerRecorded(OWNER, "Assignment race original");
        mvc.perform(get("/requester/requests/" + assigned + "/edit").cookie(cookie)).andExpect(status().isOk());
        setState(assigned, "ASSIGNED");
        mvc.perform(post("/requester/requests/" + assigned + "/edit").cookie(cookie).with(csrf())
                        .param("version", "0").param("title", "Retain assignment draft").param("description", "Retained description")
                        .param("location", "Block B").param("categoryId", category).param("reportedUrgency", "HIGH"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Retain assignment draft")));
        mvc.perform(get("/requester/requests/" + assigned + "/edit").cookie(cookie)).andExpect(status().isConflict());
        assertThrows(StaleRequestException.class, () -> service(OWNER, AccountRole.REQUESTER, 0).cancel(assigned, "Attempt stale cancellation", true, "1"));
        assertEquals("Assignment race original", service(OWNER, AccountRole.REQUESTER, 0).detail(assigned).orElseThrow().title());
        String inactive = managerRecorded(OWNER, "Category race original");
        mvc.perform(get("/requester/requests/" + inactive + "/edit").cookie(cookie)).andExpect(status().isOk());
        admin.update("UPDATE facilityflow.category SET active = false WHERE id = ?::uuid", category);
        mvc.perform(post("/requester/requests/" + inactive + "/edit").cookie(cookie).with(csrf())
                        .param("version", "0").param("title", "Retain category draft").param("description", "Retained description")
                        .param("location", "Block B").param("categoryId", category).param("reportedUrgency", "HIGH"))
                .andExpect(status().isUnprocessableContent()).andExpect(content().string(containsString("no longer active")))
                .andExpect(content().string(containsString("Retain category draft")));
        assertEquals("Category race original", service(OWNER, AccountRole.REQUESTER, 0).detail(inactive).orElseThrow().title());
        assertEquals(0, mutationAudits("REQUEST_EDITED"));
    }

    @Test
    void reqRq011ConcurrentEditsAndCancellationUpdateRacesHaveOneWinner() throws Exception {
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        String editId = managerRecorded(OWNER, "Concurrent edit original");
        Callable<Boolean> edit = () -> {
            try { requester.edit(editId, draft(), "0"); return true; }
            catch (StaleRequestException exception) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var edits = executor.invokeAll(List.of(edit, edit));
            assertEquals(1, (edits.get(0).get() ? 1 : 0) + (edits.get(1).get() ? 1 : 0));
            String raceId = managerRecorded(OWNER, "Cancellation update race");
            Callable<Boolean> cancel = () -> {
                try { requester.cancel(raceId, "No longer needed", true, "0"); return true; }
                catch (StaleRequestException exception) { return false; }
            };
            Callable<Boolean> update = () -> {
                try { requester.addUpdate(raceId, "Follow-up update", "0"); return true; }
                catch (StaleRequestException exception) { return false; }
            };
            var results = executor.invokeAll(List.of(cancel, update));
            assertEquals(1, (results.get(0).get() ? 1 : 0) + (results.get(1).get() ? 1 : 0));
            var after = requester.detail(raceId).orElseThrow();
            assertEquals(1, after.version());
            if (after.status().equals("CANCELLED")) assertEquals(0, count("requester_update"));
            else assertEquals(1, count("requester_update"));
        }
        assertEquals(1, mutationAudits("REQUEST_EDITED"));
        assertEquals(1, mutationAudits("REQUEST_CANCELLED") + mutationAudits("REQUESTER_UPDATE_ADDED"));
    }

    @Test
    void reqRq006008011EveryMutationRollsBackOnAuditFailureAndRetainsInputForRetry() throws Exception {
        Cookie cookie = login("requester1", "/requester");
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        for (String operation : List.of("edit", "cancel", "updates")) {
            String action = operation.equals("edit") ? "REQUEST_EDITED" : operation.equals("cancel") ? "REQUEST_CANCELLED" : "REQUESTER_UPDATE_ADDED";
            String id = managerRecorded(OWNER, "Rollback original " + operation);
            var before = requester.detail(id).orElseThrow();
            admin.execute("ALTER TABLE facilityflow.audit_event ADD CONSTRAINT reject_mutation_test CHECK (action <> '" + action + "')");
            try {
                var post = post("/requester/requests/" + id + "/" + operation).cookie(cookie).with(csrf()).param("version", "0");
                if (operation.equals("edit")) post.param("title", "Retained rollback draft").param("description", "Retained detailed description")
                        .param("location", "Block B").param("categoryId", category).param("reportedUrgency", "HIGH");
                else if (operation.equals("cancel")) post.param("reason", "Retained rollback draft").param("confirmed", "true").param("decision", "cancel");
                else post.param("note", "Retained rollback draft");
                mvc.perform(post).andExpect(status().isServiceUnavailable())
                        .andExpect(content().string(containsString("Retained rollback draft")))
                        .andExpect(content().string(not(containsString("reject_mutation_test"))));
                assertEquals(before, requester.detail(id).orElseThrow());
                assertEquals(0, mutationAudits(action));
                assertEquals(0, count("requester_update"));
            } finally {
                admin.execute("ALTER TABLE facilityflow.audit_event DROP CONSTRAINT reject_mutation_test");
            }
            if (operation.equals("edit")) requester.edit(id, draft(), "0");
            else if (operation.equals("cancel")) requester.cancel(id, "Valid cancellation reason", true, "0");
            else requester.addUpdate(id, "Valid follow-up update", "0");
            assertEquals(1, mutationAudits(action));
        }
    }

    @Test
    void reqRq009010MutationsDenyForeignObjectsWrongRolesAndMissingCsrf() throws Exception {
        String id = managerRecorded(OTHER, "FOREIGN PRIVATE REPORT");
        Cookie owner = login("requester1", "/requester");
        for (String operation : List.of("edit", "cancel", "updates")) {
            mvc.perform(post("/requester/requests/" + id + "/" + operation).cookie(owner).with(csrf())
                            .param("version", "0").param("decision", "cancel").param("confirmed", "true")
                            .param("reason", "Valid cancellation reason").param("note", "Valid update")
                            .param("title", "Forged report title").param("description", "Valid report description")
                            .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "HIGH"))
                    .andExpect(status().isNotFound()).andExpect(content().string(not(containsString("FOREIGN PRIVATE REPORT"))));
            mvc.perform(post("/requester/requests/" + id + "/" + operation).cookie(owner)).andExpect(status().isForbidden());
        }
        for (String role : List.of("technician1", "manager1")) {
            Cookie otherRole = login(role, role.equals("technician1") ? "/technician" : "/manager");
            for (String operation : List.of("edit", "cancel", "updates")) {
                mvc.perform(post("/requester/requests/" + id + "/" + operation).cookie(otherRole).with(csrf())).andExpect(status().isForbidden());
            }
        }
        assertEquals(0, count("requester_update"));
        assertEquals(0, mutationAudits("REQUEST_EDITED") + mutationAudits("REQUEST_CANCELLED") + mutationAudits("REQUESTER_UPDATE_ADDED"));
    }

    @Test
    void reqRq011MalformedVersionsAndInvalidUpdatesWriteNothing() {
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        String id = managerRecorded(OWNER, "Input validation original");
        for (String version : new String[]{"", "-1", "not-a-version", "9223372036854775808"}) {
            assertThrows(RequestValidationException.class, () -> requester.edit(id, draft(), version));
        }
        for (String note : List.of("", "  ", "😀".repeat(1001))) {
            assertThrows(RequestValidationException.class, () -> requester.addUpdate(id, note, "0"));
        }
        assertEquals(0, requester.detail(id).orElseThrow().version());
        assertEquals(0, count("requester_update"));
    }

    private int mutationAudits(String action) {
        return app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = ?", Integer.class, action);
    }

    @Test
    void reqRq001DashboardCountsAndFiveRecentReportsAreOwnerScoped() throws Exception {
        var owned = new java.util.ArrayList<String>();
        int day = 1;
        for (String status : RequesterRequests.STATUSES) {
            String id = managerRecorded(OWNER, "Owned dashboard " + status);
            setState(id, status);
            setCreated(id, java.time.OffsetDateTime.parse("2026-01-0" + day++ + "T00:00:00Z"));
            owned.add(id);
        }
        String newest = managerRecorded(OWNER, "Newest owner report");
        setCreated(newest, java.time.OffsetDateTime.parse("2026-01-07T00:00:00Z"));
        owned.add(newest);
        for (int index = 0; index < 3; index++) {
            String foreign = managerRecorded(OTHER, "FOREIGN PRIVATE DASHBOARD " + index);
            setCreated(foreign, java.time.OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        }
        var dashboard = service(OWNER, AccountRole.REQUESTER, 0).dashboard();
        assertEquals(6, dashboard.counts().size());
        assertEquals(2, dashboard.counts().getFirst().count());
        assertEquals(7, dashboard.counts().stream().mapToLong(RequesterRequests.StatusCount::count).sum());
        assertTrue(dashboard.counts().stream().skip(1).allMatch(count -> count.count() == 1));
        assertEquals(List.of(owned.get(6), owned.get(5), owned.get(4), owned.get(3), owned.get(2)),
                dashboard.recent().stream().map(RequesterRequests.Summary::displayId).toList());
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester").cookie(cookie)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Recent requests")))
                .andExpect(content().string(containsString("status=OPEN")))
                .andExpect(content().string(containsString("Newest owner report")))
                .andExpect(content().string(not(containsString("FOREIGN PRIVATE DASHBOARD"))));
    }

    @Test
    void reqRq003LiteralCaseInsensitiveSearchCoversOnlyIdTitleAndLocation() throws Exception {
        String title = managerRecorded(OWNER, "Leaking pipe by lift");
        String literal = managerRecorded(OWNER, "100%_safe\\check report");
        String location = managerRecorded(OWNER, "Storage door report");
        admin.update("UPDATE facilityflow.maintenance_request SET location = 'North STORAGE' WHERE display_id = ?", location);
        managerRecorded(OTHER, "Leaking pipe by lift FOREIGN");
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        assertEquals(List.of(title), ids(requester.list(new RequestFilters("\u2003lEaKiNg ", "", "", "", ""))));
        assertEquals(List.of(title), ids(requester.list(new RequestFilters(title.toLowerCase(java.util.Locale.ROOT), "", "", "", ""))));
        assertEquals(List.of(location), ids(requester.list(new RequestFilters(" north storage ", "", "", "", ""))));
        for (String text : List.of("%", "_", "\\")) {
            assertEquals(List.of(literal), ids(requester.list(new RequestFilters(text, "", "", "", ""))));
        }
        assertTrue(requester.list(new RequestFilters("Recorded for the requester", "", "", "", "")).requests().isEmpty());
        assertTrue(requester.list(new RequestFilters("' OR 1=1 --", "", "", "", "")).requests().isEmpty());
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests").cookie(cookie).param("search", " lEaKiNg ").param("requesterId", OTHER.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Leaking pipe by lift")))
                .andExpect(content().string(not(containsString("FOREIGN"))))
                .andExpect(content().string(not(containsString("Storage door report"))));
    }

    @Test
    void reqRq003AndSh009FiltersCombineWithInclusiveLocalDatesAndInactiveHistory() throws Exception {
        var start = java.time.OffsetDateTime.parse("2026-10-15T16:00:00Z");
        var end = java.time.OffsetDateTime.parse("2026-10-16T16:00:00Z");
        String before = managerRecorded(OWNER, "Pipe before day");
        String first = managerRecorded(OWNER, "Pipe at day start");
        String last = managerRecorded(OWNER, "Pipe at last microsecond");
        String after = managerRecorded(OWNER, "Pipe next day");
        String otherCategory = managerRecorded(OWNER, "Pipe other category");
        String foreign = managerRecorded(OTHER, "Pipe FOREIGN in range");
        setCreated(before, start.minusNanos(1000));
        setCreated(first, start);
        setCreated(last, end.minusNanos(1000));
        setCreated(after, end);
        setCreated(otherCategory, start.plusHours(1));
        setCreated(foreign, start.plusHours(2));
        setState(last, "ASSIGNED");
        String otherId = app.queryForObject("SELECT id::text FROM facilityflow.category WHERE name = 'Other'", String.class);
        admin.update("UPDATE facilityflow.maintenance_request SET category_id = ?::uuid WHERE display_id = ?", otherId, otherCategory);
        admin.update("UPDATE facilityflow.category SET active = false WHERE id = ?::uuid", category);
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        var day = requester.list(new RequestFilters("", "", "", "2026-10-16", "2026-10-16"));
        assertEquals(List.of(last, otherCategory, first), ids(day));
        assertFalse(ids(day).contains(before));
        assertFalse(ids(day).contains(after));
        assertEquals(List.of(first), ids(requester.list(new RequestFilters(" PiPe ", "OPEN", category, "2026-10-16", "2026-10-16"))));
        assertTrue(ids(requester.list(new RequestFilters("", "", "", "2026-10-16", ""))).contains(after));
        assertFalse(ids(requester.list(new RequestFilters("", "", "", "2026-10-16", ""))).contains(before));
        assertTrue(ids(requester.list(new RequestFilters("", "", "", "", "2026-10-16"))).contains(before));
        assertFalse(ids(requester.list(new RequestFilters("", "", "", "", "2026-10-16"))).contains(after));
        assertTrue(day.categories().stream().anyMatch(option -> option.id().toString().equals(category) && option.name().contains("inactive")));
        assertTrue(requester.categories().stream().noneMatch(option -> option.id().toString().equals(category)));
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests").cookie(cookie).param("search", "PiPe").param("status", "OPEN")
                        .param("categoryId", category).param("createdFrom", "2026-10-16").param("createdTo", "2026-10-16"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pipe at day start")))
                .andExpect(content().string(not(containsString("Pipe next day"))))
                .andExpect(content().string(containsString("Electrical (inactive)")));
    }

    @Test
    void reqSh008EmptyAndZeroResultStatesAreDistinctAndResetRestoresOwnList() throws Exception {
        managerRecorded(OTHER, "FOREIGN exists but owner empty");
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests").cookie(cookie))
                .andExpect(content().string(containsString("You have no requests yet")))
                .andExpect(content().string(not(containsString("FOREIGN exists"))));
        var dashboard = service(OWNER, AccountRole.REQUESTER, 0).dashboard();
        assertTrue(dashboard.recent().isEmpty());
        assertTrue(dashboard.counts().stream().allMatch(count -> count.count() == 0));
        managerRecorded(OWNER, "Own reset report");
        mvc.perform(get("/requester/requests").cookie(cookie).param("search", "nonexistent search text"))
                .andExpect(content().string(containsString("No requests match these filters")))
                .andExpect(content().string(not(containsString("You have no requests yet"))))
                .andExpect(content().string(containsString("href=\"/requester/requests\">Reset filters")));
        mvc.perform(get("/requester/requests").cookie(cookie)).andExpect(content().string(containsString("Own reset report")));
    }

    @Test
    void reqRq003InvalidFiltersRetainValuesWithSpecificErrorsAndNoUnfilteredFallback() throws Exception {
        managerRecorded(OWNER, "Must not appear under invalid filters");
        var filters = new RequestFilters("<script>alert(1)</script>", "INVALID", UUID.randomUUID().toString(), "2026-10-17", "2026-10-16");
        var result = service(OWNER, AccountRole.REQUESTER, 0).list(filters);
        assertTrue(result.requests().isEmpty());
        assertTrue(result.hasRequests());
        assertEquals(java.util.Set.of("status", "categoryId", "createdTo"), result.errors().keySet());
        Cookie cookie = login("requester1", "/requester");
        var response = mvc.perform(get("/requester/requests").cookie(cookie).param("search", filters.search())
                        .param("status", filters.status()).param("categoryId", filters.categoryId())
                        .param("createdFrom", filters.createdFrom()).param("createdTo", filters.createdTo()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("End date must be on or after")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(not(containsString("Must not appear under invalid filters")))).andReturn();
        assertEquals(filters, response.getModelAndView().getModel().get("filters"));
    }

    @Test
    void reqRq012014AssistantCreateAndOwnedOpenEditReturnSuggestionsWithoutBusinessWrites() throws Exception {
        String owned = managerRecorded(OWNER, "Own report");
        managerRecorded(OTHER, "UNAUTHORIZED_PRIVATE_CANARY_791");
        Cookie cookie = login("requester1", "/requester");
        int audit = count("audit_event");
        mvc.perform(post("/requester/requests/assistant").cookie(cookie).with(csrf())
                        .param("roughText", "The light keeps flickering").param("categoryId", category)
                        .param("reportedUrgency", "NORMAL").param("actorId", OTHER.toString()).param("accepted", "true"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.suggestion.draftTitle").value("Flickering light"));
        mvc.perform(post("/requester/requests/" + owned + "/assistant").cookie(cookie).with(csrf())
                        .param("version", "0").param("roughText", "Current unsaved report"))
                .andExpect(status().isOk());
        assertEquals(2, ai.calls.get());
        assertEquals(2, count("maintenance_request"));
        assertEquals(0, count("requester_update"));
        assertEquals(audit, count("audit_event"));
        assertEquals(0L, app.queryForObject("SELECT version FROM facilityflow.maintenance_request WHERE display_id = ?", Long.class, owned));
        assertEquals(2, count("ai_invocation"));
        assertEquals(2, app.queryForObject("SELECT count(*) FROM facilityflow.ai_invocation WHERE actor_id = ? AND status = 'SUCCEEDED' AND input_tokens = 10 AND output_tokens = 20 AND total_tokens = 30", Integer.class, OWNER));
        String metadata = admin.queryForList("SELECT * FROM facilityflow.ai_invocation").toString();
        assertFalse(metadata.contains("Current unsaved report"));
        assertFalse(metadata.contains("Flickering light"));
        assertFalse(ai.user.contains("UNAUTHORIZED_PRIVATE_CANARY_791"));
        assertFalse(ai.user.contains(owned));
        assertFalse(ai.user.contains(OWNER.toString()));
        assertTrue(ai.user.contains("Electrical"));
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.rate_limit_bucket WHERE scope = 'GLOBAL_SLOT' AND lease_expires_at IS NOT NULL", Integer.class));
    }

    @Test
    void aiSec002029DenialsAndInputLimitsPrecedeContextAndProvider() throws Exception {
        Cookie owner = login("requester1", "/requester");
        Cookie technician = login("technician1", "/technician");
        Cookie manager = login("manager1", "/manager");
        String foreign = managerRecorded(OTHER, "Foreign AI report");
        mvc.perform(post("/requester/requests/assistant").cookie(owner).param("roughText", "rough"))
                .andExpect(status().isForbidden());
        for (Cookie wrongRole : List.of(technician, manager)) {
            mvc.perform(post("/requester/requests/assistant").cookie(wrongRole).with(csrf()).param("roughText", "rough"))
                    .andExpect(status().isForbidden());
        }
        String foreignResponse = mvc.perform(post("/requester/requests/" + foreign + "/assistant").cookie(owner)
                        .with(csrf()).param("version", "0").param("roughText", "rough"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String absentResponse = mvc.perform(post("/requester/requests/FF-000000/assistant").cookie(owner)
                        .with(csrf()).param("version", "0").param("roughText", "rough"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreignResponse, absentResponse);
        mvc.perform(post("/requester/requests/assistant").cookie(owner).with(csrf()).param("roughText", "a".repeat(2001)))
                .andExpect(status().isUnprocessableContent());
        assertEquals(0, ai.calls.get());
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.rate_limit_bucket WHERE scope = 'ACCOUNT_RATE'", Integer.class));
        var requester = assistant(OWNER, AccountRole.REQUESTER, 0);
        app.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, () -> requester.suggest(null, null, rough()));
        app.update("UPDATE facilityflow.user_account SET active = true, session_version = 1 WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, () -> requester.suggest(null, null, rough()));
        app.update("UPDATE facilityflow.user_account SET session_version = 0, role = 'TECHNICIAN' WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, () -> requester.suggest(null, null, rough()));
        assertEquals(0, ai.calls.get());
    }

    @Test
    void reqRq012AssistantRejectsAllNonOpenAndStaleVersionsBeforeProvider() {
        var assistant = assistant(OWNER, AccountRole.REQUESTER, 0);
        for (String state : List.of("ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED")) {
            String id = managerRecorded(OWNER, "AI state boundary");
            setState(id, state);
            assertThrows(RequestUnavailableException.class, () -> assistant.suggest(id, "0", rough()));
        }
        String open = managerRecorded(OWNER, "AI version boundary");
        assertThrows(StaleRequestException.class, () -> assistant.suggest(open, "1", rough()));
        assertThrows(StaleRequestException.class, () -> assistant.suggest(open, "", rough()));
        assertEquals(0, ai.calls.get());
    }

    @Test
    void aiSec028AccessAndCatalogueChangesDuringInferenceDiscardEntireResult() {
        var assistant = assistant(OWNER, AccountRole.REQUESTER, 0);
        ai.behavior = (s, u, d) -> {
            app.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", OWNER);
            return FakeAiTransport.success(FakeAiTransport.VALID);
        };
        var revoked = assistant.suggest(null, null, rough());
        assertNull(revoked.suggestion());
        assertEquals(AiGateway.Status.AI_UNAVAILABLE, revoked.status());
        app.update("UPDATE facilityflow.user_account SET active = true WHERE id = ?", OWNER);
        String owned = managerRecorded(OWNER, "Assigned while AI runs");
        ai.behavior = (s, u, d) -> {
            setState(owned, "ASSIGNED");
            return FakeAiTransport.success(FakeAiTransport.VALID);
        };
        assertNull(assistant.suggest(owned, "0", rough()).suggestion());
        ai.behavior = (s, u, d) -> {
            app.update("UPDATE facilityflow.category SET active = false WHERE id = ?::uuid", category);
            return FakeAiTransport.success(FakeAiTransport.VALID);
        };
        assertEquals(AiGateway.Status.AI_INVALID_OUTPUT, assistant.suggest(null, null, rough()).status());
        assertEquals(3, ai.calls.get());
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.ai_invocation WHERE status = 'SUCCEEDED'", Integer.class));
        assertEquals(3, app.queryForObject("SELECT count(*) FROM facilityflow.ai_invocation WHERE input_tokens = 10 AND output_tokens = 20 AND total_tokens = 30", Integer.class));
    }

    @Test
    void aiSec008021DatabaseLimitsAreRollingGlobalAndLeaseReleaseUsesVersion() throws Exception {
        var limits = context.getBean(JdbcAiOperations.class);
        var permits = new java.util.ArrayList<AiOperations.Permit>();
        for (int i = 0; i < 5; i++) permits.add(limits.acquire(OWNER, 30));
        assertTrue(permits.stream().allMatch(java.util.Objects::nonNull));
        assertNull(limits.acquire(OTHER, 30));
        permits.forEach(limits::release);
        assertNull(limits.acquire(OWNER, 30));
        app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = array_fill(clock_timestamp() - interval '61 seconds', ARRAY[30]) WHERE account_id = ?", OWNER);
        assertNull(limits.acquire(OWNER, 30));
        app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = array_fill(clock_timestamp() - interval '3601 seconds', ARRAY[30]) WHERE account_id = ?", OWNER);
        var expired = limits.acquire(OWNER, 1);
        assertNotNull(expired);
        app.update("UPDATE facilityflow.rate_limit_bucket SET lease_expires_at = clock_timestamp() - interval '1 second' WHERE id = ?", expired.slotId());
        var renewed = limits.acquire(OTHER, 1);
        assertEquals(expired.slotId(), renewed.slotId());
        limits.release(expired);
        assertNotNull(app.queryForObject("SELECT lease_expires_at FROM facilityflow.rate_limit_bucket WHERE id = ?", java.time.OffsetDateTime.class, renewed.slotId()));
        limits.release(renewed);
        app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = '{}' WHERE scope = 'ACCOUNT_RATE'");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var attempts = java.util.stream.IntStream.range(0, 8).<Callable<AiOperations.Permit>>mapToObj(i -> () -> limits.acquire(OWNER, 30)).toList();
            var results = pool.invokeAll(attempts);
            var admitted = new java.util.ArrayList<AiOperations.Permit>();
            for (var result : results) { var permit = result.get(); if (permit != null) admitted.add(permit); }
            assertEquals(5, admitted.size());
            admitted.forEach(limits::release);
        }
    }

    @Test
    void reqRq017DisabledMalformedTimeoutAndRateLimitLeaveManualInputAndWritesAlone() throws Exception {
        var assistant = assistant(OWNER, AccountRole.REQUESTER, 0);
        var input = rough();
        ai.enabled = false;
        assertEquals(AiGateway.Status.AI_UNAVAILABLE, assistant.suggest(null, null, input).status());
        assertEquals(0, ai.calls.get());
        ai.enabled = true;
        ai.behavior = (s, u, d) -> FakeAiTransport.success("{broken");
        assertEquals(AiGateway.Status.AI_INVALID_OUTPUT, assistant.suggest(null, null, input).status());
        ai.behavior = (s, u, d) -> { Thread.sleep(5000); return FakeAiTransport.success(FakeAiTransport.VALID); };
        assertEquals(AiGateway.Status.AI_TIMEOUT, assistant.suggest(null, null, input).status());
        ai.behavior = (s, u, d) -> FakeAiTransport.success(FakeAiTransport.VALID);
        app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = array_fill(clock_timestamp(), ARRAY[5]) WHERE account_id = ?", OWNER);
        int before = ai.calls.get();
        assertEquals(AiGateway.Status.AI_LIMITED, assistant.suggest(null, null, input).status());
        assertEquals(before, ai.calls.get());
        assertEquals("The light keeps flickering", input.roughText());
        assertEquals(0, count("maintenance_request"));
        assertEquals(0, count("request_submission"));
        assertEquals(0, count("requester_update"));
        assertEquals(0, count("audit_event"));
    }

    @Test
    void aiSec017MonitoringStartFailurePreventsProviderAndTerminalFailureWithholdsSuggestion() {
        admin.execute("ALTER TABLE facilityflow.ai_invocation ADD CONSTRAINT reject_started CHECK (status <> 'STARTED') NOT VALID");
        try {
            var reply = assistant(OWNER, AccountRole.REQUESTER, 0).suggest(null, null, rough());
            assertEquals(AiGateway.Status.AI_UNAVAILABLE, reply.status());
            assertNull(reply.suggestion());
            assertEquals(0, ai.calls.get());
            assertEquals(0, count("ai_invocation"));
        } finally { admin.execute("ALTER TABLE facilityflow.ai_invocation DROP CONSTRAINT reject_started"); }
        admin.execute("ALTER TABLE facilityflow.ai_invocation ADD CONSTRAINT reject_succeeded CHECK (status <> 'SUCCEEDED') NOT VALID");
        try {
            var reply = assistant(OWNER, AccountRole.REQUESTER, 0).suggest(null, null, rough());
            assertEquals(AiGateway.Status.AI_UNAVAILABLE, reply.status());
            assertNull(reply.suggestion());
            assertEquals(1, ai.calls.get());
            assertEquals("STARTED", app.queryForObject("SELECT status FROM facilityflow.ai_invocation", String.class));
            assertEquals(0, count("maintenance_request"));
            assertEquals(0, count("audit_event"));
        } finally { admin.execute("ALTER TABLE facilityflow.ai_invocation DROP CONSTRAINT reject_succeeded"); }
    }

    private SmartReportService assistant(UUID id, AccountRole role, long version) {
        return new SmartReportService(() -> new CurrentAccount(id, "synthetic", "Synthetic", role, version),
                context.getBean(RequesterRequestRepository.class), context.getBean(PlatformTransactionManager.class),
                context.getBean(JdbcAiOperations.class), context.getBean(AiGateway.class),
                context.getBean(sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.class));
    }

    @Test
    void reqRq011Rel001SavedReportAndJdbcSessionSurviveApplicationRestart() throws Exception {
        String id = service(OWNER, AccountRole.REQUESTER, 0).create(draft(), UUID.randomUUID().toString());
        Cookie cookie = login("requester1", "/requester");
        context.close();
        startWebApplication();
        mvc.perform(get("/requester/requests/" + id).cookie(cookie))
                .andExpect(status().isOk()).andExpect(content().string(containsString("The ceiling light is flickering.")));
        assertEquals(1, count("maintenance_request"));
        assertEquals(1, requestAuditCount());
        assertEquals("OPEN", service(OWNER, AccountRole.REQUESTER, 0).detail(id).orElseThrow().status());
    }

    @Test
    @Tag("requester-verification")
    void rqAcceptanceChromeAndFiftyUserLocalVerification() throws Exception {
        for (int i = 0; i < 50; i++) {
            UUID id = UUID.nameUUIDFromBytes(("verification-user-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            app.update("INSERT INTO facilityflow.user_account (id, username, display_name, password_hash, role) VALUES (?, ?, ?, ?, 'REQUESTER')",
                    id, "load" + i, "Load " + i, passwordHash);
            managerRecorded(id, "Synthetic load report " + i);
        }
        String foreign = managerRecorded(OTHER, "FOREIGN_BROWSER_CANARY");
        var output = java.nio.file.Path.of("build/verification/requester");
        java.nio.file.Files.createDirectories(output);
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        var pool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
        var peakWaiting = new java.util.concurrent.atomic.AtomicInteger();
        var peakActive = new java.util.concurrent.atomic.AtomicInteger();
        var sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> {
            peakWaiting.accumulateAndGet(pool.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
            peakActive.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
        }, 0, 20, java.util.concurrent.TimeUnit.MILLISECONDS);
        Process process = null;
        try {
            process = new ProcessBuilder("node", "src/test/js/requester-verification.cjs", "http://127.0.0.1:" + port, foreign,
                    Integer.toString(pool.getMaximumPoolSize()))
                    .redirectErrorStream(true).redirectOutput(output.resolve("runner.txt").toFile()).start();
            assertTrue(process.waitFor(240, java.util.concurrent.TimeUnit.SECONDS), "Verification exceeded four minutes; see build/verification/requester/runner.txt");
            java.nio.file.Files.writeString(output.resolve("pool-metrics.json"), "{\"maximumPoolSize\":" + pool.getMaximumPoolSize()
                    + ",\"peakActive\":" + peakActive.get() + ",\"peakWaiting\":" + peakWaiting.get() + "}");
            assertEquals(0, process.exitValue(), "Browser/load verification failed; see build/verification/requester/runner.txt");
            assertEquals(1, app.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request WHERE requester_id = ?", Integer.class, OWNER));
            assertEquals("CANCELLED", app.queryForObject("SELECT status FROM facilityflow.maintenance_request WHERE requester_id = ?", String.class, OWNER));
        } finally { sampler.shutdownNow(); if (process != null && process.isAlive()) process.destroyForcibly(); }
    }

    @Test
    void reqRq015016ReviewUiAndExplicitOrdinarySubmissionRecheckStateRegardlessOfAiFlags() throws Exception {
        Cookie cookie = login("requester1", "/requester");
        mvc.perform(get("/requester/requests/new").cookie(cookie))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Smart Report Assistant (optional)")))
                .andExpect(content().string(containsString("data-endpoint=\"/requester/requests/assistant\"")))
                .andExpect(content().string(containsString("/js/smart-report.js")))
                .andExpect(content().string(containsString("Nothing is saved automatically")))
                .andExpect(content().string(containsString("<noscript>")));
        String token = formToken(cookie);
        var response = mvc.perform(post("/requester/requests/assistant").cookie(cookie).with(csrf())
                        .param("roughText", "Flickering ceiling light"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String suggestedTitle = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response)
                .get("suggestion").get("draftTitle").asString();
        assertEquals(0, count("maintenance_request"));
        var created = mvc.perform(post("/requester/requests").cookie(cookie).with(csrf())
                        .param("submissionId", token).param("title", suggestedTitle).param("description", "User reviewed and edited description")
                        .param("location", "Block A").param("categoryId", category).param("reportedUrgency", "NORMAL")
                        .param("aiAccepted", "true").param("status", "CLOSED").param("managerPriority", "CRITICAL"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String id = created.substring(created.lastIndexOf('/') + 1);
        assertEquals(1, count("maintenance_request"));
        assertEquals(1, requestAuditCount());
        var detail = service(OWNER, AccountRole.REQUESTER, 0).detail(id).orElseThrow();
        assertEquals("OPEN", detail.status()); assertNull(detail.managerPriority());
        assertEquals("User reviewed and edited description", detail.description());
        mvc.perform(get("/requester/requests/" + id + "/edit").cookie(cookie))
                .andExpect(content().string(containsString("data-endpoint=\"/requester/requests/" + id + "/assistant\"")))
                .andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
        mvc.perform(post("/requester/requests/" + id + "/assistant").cookie(cookie).with(csrf())
                        .param("version", "0").param("roughText", "More light details"))
                .andExpect(status().isOk());
        setState(id, "ASSIGNED");
        mvc.perform(post("/requester/requests/" + id + "/edit").cookie(cookie).with(csrf())
                        .param("version", "0").param("title", "AI approved replacement").param("description", "User description after AI review")
                        .param("location", "Block B").param("categoryId", category).param("reportedUrgency", "HIGH").param("aiAccepted", "true"))
                .andExpect(status().isConflict());
        assertEquals(suggestedTitle, service(OWNER, AccountRole.REQUESTER, 0).detail(id).orElseThrow().title());
    }

    @Test
    void aiSec002029NoCatalogueContextOnDeniedInvalidOrQuotaLimitedCalls() {
        var repository = org.mockito.Mockito.mock(RequesterRequestRepository.class,
                org.mockito.AdditionalAnswers.delegatesTo(context.getBean(RequesterRequestRepository.class)));
        var assistant = new SmartReportService(() -> new CurrentAccount(OWNER, "synthetic", "Synthetic", AccountRole.REQUESTER, 0),
                repository, context.getBean(PlatformTransactionManager.class), context.getBean(AiOperations.class), context.getBean(AiGateway.class),
                context.getBean(sg.edu.nus.facilityflow.ai.AiInvocationTelemetry.class));
        assertThrows(RequestUnavailableException.class, () -> assistant.suggest("FF-000000", "0", rough()));
        assertEquals(AiGateway.Status.AI_INVALID_REQUEST,
                assistant.suggest(null, null, new SmartReportInput("a".repeat(2001), "", "", "", "", "")).status());
        app.update("INSERT INTO facilityflow.rate_limit_bucket (scope, account_id) VALUES ('ACCOUNT_RATE', ?)", OWNER);
        app.update("UPDATE facilityflow.rate_limit_bucket SET invocation_times = array_fill(clock_timestamp(), ARRAY[5]) WHERE account_id = ?", OWNER);
        assertEquals(AiGateway.Status.AI_LIMITED, assistant.suggest(null, null, rough()).status());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).categories();
        assertEquals(0, ai.calls.get());
    }

    private static SmartReportInput rough() { return new SmartReportInput("The light keeps flickering", "", "", "", "", ""); }

    private static List<String> ids(RequesterRequests.ListPage page) {
        return page.requests().stream().map(RequesterRequests.Summary::displayId).toList();
    }

    private void setCreated(String id, java.time.OffsetDateTime time) {
        admin.update("UPDATE facilityflow.maintenance_request SET created_at = ?, updated_at = ? WHERE display_id = ?", time, time, id);
    }

    @Test
    void reqSh002EveryMutationRechecksPersistedAccountAndRoleOutsideHttp() {
        String id = managerRecorded(OWNER, "Account access checks");
        var requester = service(OWNER, AccountRole.REQUESTER, 0);
        app.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, () -> requester.edit(id, draft(), "0"));
        assertThrows(AccessDeniedException.class, () -> requester.cancel(id, "Valid cancellation reason", true, "0"));
        assertThrows(AccessDeniedException.class, () -> requester.addUpdate(id, "Valid update", "0"));
        app.update("UPDATE facilityflow.user_account SET active = true, session_version = 1 WHERE id = ?", OWNER);
        assertThrows(AccessDeniedException.class, () -> requester.edit(id, draft(), "0"));
        assertThrows(AccessDeniedException.class, () -> requester.cancel(id, "Valid cancellation reason", true, "0"));
        assertThrows(AccessDeniedException.class, () -> requester.addUpdate(id, "Valid update", "0"));
        for (var role : List.of(AccountRole.TECHNICIAN, AccountRole.FACILITIES_MANAGER)) {
            var wrongRole = service(role == AccountRole.TECHNICIAN ? TECHNICIAN : MANAGER, role, 0);
            assertThrows(AccessDeniedException.class, () -> wrongRole.edit(id, draft(), "0"));
            assertThrows(AccessDeniedException.class, () -> wrongRole.cancel(id, "Valid cancellation reason", true, "0"));
            assertThrows(AccessDeniedException.class, () -> wrongRole.addUpdate(id, "Valid update", "0"));
        }
        assertEquals(0, count("requester_update"));
        assertEquals(0, mutationAudits("REQUEST_EDITED") + mutationAudits("REQUEST_CANCELLED") + mutationAudits("REQUESTER_UPDATE_ADDED"));
    }

    private void setState(String id, String status) {
        boolean assigned = List.of("ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED").contains(status);
        boolean completed = status.equals("COMPLETED") || status.equals("CLOSED");
        admin.update("UPDATE facilityflow.maintenance_request SET status = ?, manager_priority = ?, assigned_technician_id = ?, "
                        + "assigned_at = ?::timestamptz, resolution_summary = ?, completed_by = ?, completed_at = ?::timestamptz, version = version + 1 WHERE display_id = ?",
                status, assigned ? "MEDIUM" : null, assigned ? TECHNICIAN : null, assigned ? "2026-01-01T00:00:00Z" : null,
                completed ? "Synthetic completed repair summary" : null, completed ? TECHNICIAN : null,
                completed ? "2026-01-02T00:00:00Z" : null, id);
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
