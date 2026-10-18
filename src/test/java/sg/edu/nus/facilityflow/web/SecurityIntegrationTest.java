package sg.edu.nus.facilityflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import sg.edu.nus.facilityflow.identity.AccountRole;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIntegrationTest {

    private static final String VALID_PASSWORD = "Welcome123";
    private static final UUID REQUESTER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICIAN_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID MANAGER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID INACTIVE_ID = UUID.fromString("30000000-0000-0000-0000-000000000004");

    private MockMvc mockMvc;

    private sg.edu.nus.facilityflow.technician.TechnicianCommandService commandService;

    private sg.edu.nus.facilityflow.technician.TechnicianWorkPlanService workPlanService;

    private sg.edu.nus.facilityflow.identity.PasswordChangeService passwordChanges;

    // MVC uses a command double: PostgreSQL command atomicity/locking is tested
    // separately. This context verifies real routing, security and Thymeleaf.
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TechnicianCommandMvcConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        sg.edu.nus.facilityflow.technician.TechnicianCommandService technicianMvcCommands() {
            return org.mockito.Mockito.mock(sg.edu.nus.facilityflow.technician.TechnicianCommandService.class);
        }

        // Service/provider semantics have separate PostgreSQL and asynchronous tests.
        // This double exercises the real HTTP security, serialization and page integration.
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        sg.edu.nus.facilityflow.technician.TechnicianWorkPlanService technicianMvcWorkPlans() {
            return org.mockito.Mockito.mock(sg.edu.nus.facilityflow.technician.TechnicianWorkPlanService.class);
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        sg.edu.nus.facilityflow.identity.PasswordChangeService passwordMvcChanges() {
            return org.mockito.Mockito.mock(sg.edu.nus.facilityflow.identity.PasswordChangeService.class);
        }
    }

    private JdbcTemplate jdbc;

    private PasswordEncoder passwordEncoder;

    private String validPasswordHash;
    private org.springframework.web.context.ConfigurableWebApplicationContext context;

    @BeforeAll
    void hashFixturePasswordOnce() {
        // TECHNICAL-SPEC pins JUnit 5; Spring 7's JUnit extension requires JUnit 6.
        var application = new org.springframework.boot.SpringApplication(
                sg.edu.nus.facilityflow.FacilityFlowApplication.class);
        application.addPrimarySources(java.util.Set.of(TechnicianCommandMvcConfiguration.class));
        var environment = new org.springframework.web.context.support.StandardServletEnvironment();
        environment.getPropertySources().remove(
                org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        context = (org.springframework.web.context.ConfigurableWebApplicationContext) application.run(
                "--spring.profiles.active=test", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:h2:mem:facilityflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.datasource.hikari.data-source-properties.sslrootcert=synthetic-test.crt");
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSessionRepositoryFilter", jakarta.servlet.Filter.class))
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbc = context.getBean(JdbcTemplate.class);
        commandService = context.getBean(sg.edu.nus.facilityflow.technician.TechnicianCommandService.class);
        workPlanService = context.getBean(sg.edu.nus.facilityflow.technician.TechnicianWorkPlanService.class);
        passwordChanges = context.getBean(sg.edu.nus.facilityflow.identity.PasswordChangeService.class);
        // This identity-only H2 fixture needs an empty read workspace for the
        // Technician landing route. PostgreSQL behavior has its own isolated tests.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS facilityflow.category (
                    id UUID PRIMARY KEY, name VARCHAR(50), active BOOLEAN DEFAULT TRUE)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS facilityflow.maintenance_request (
                    id UUID PRIMARY KEY, display_id VARCHAR(9), assigned_technician_id UUID,
                    category_id UUID, status VARCHAR(20), manager_priority VARCHAR(20),
                    reported_urgency VARCHAR(20), assigned_at TIMESTAMP WITH TIME ZONE,
                    title VARCHAR(100), description VARCHAR(2000), location VARCHAR(120),
                    version BIGINT DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE,
                    updated_at TIMESTAMP WITH TIME ZONE, resolution_summary VARCHAR(2000),
                    completed_at TIMESTAMP WITH TIME ZONE)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS facilityflow.requester_update (
                    id UUID PRIMARY KEY, request_id UUID, author_id UUID, note VARCHAR(1000),
                    created_at TIMESTAMP WITH TIME ZONE)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS facilityflow.work_log (
                    id UUID PRIMARY KEY, request_id UUID, author_id UUID, note VARCHAR(1000),
                    minutes INTEGER, created_at TIMESTAMP WITH TIME ZONE)
                """);
        passwordEncoder = context.getBean(PasswordEncoder.class);
        validPasswordHash = passwordEncoder.encode(VALID_PASSWORD);
    }

    @AfterAll
    void closeApplication() {
        if (context != null) context.close();
    }

    @BeforeEach
    void seedAccounts() {
        org.mockito.Mockito.reset(commandService);
        org.mockito.Mockito.reset(workPlanService);
        org.mockito.Mockito.reset(passwordChanges);
        org.mockito.Mockito.when(workPlanService.request(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse.failure(
                                sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode.AI_UNAVAILABLE)));
        jdbc.update("DELETE FROM facilityflow.spring_session_attributes");
        jdbc.update("DELETE FROM facilityflow.spring_session");
        jdbc.update("DELETE FROM facilityflow.requester_update");
        jdbc.update("DELETE FROM facilityflow.work_log");
        jdbc.update("DELETE FROM facilityflow.maintenance_request");
        jdbc.update("DELETE FROM facilityflow.category");
        jdbc.update("DELETE FROM facilityflow.user_account");
        insertAccount(REQUESTER_ID, "requester1", AccountRole.REQUESTER, true);
        insertAccount(TECHNICIAN_ID, "technician1", AccountRole.TECHNICIAN, true);
        insertAccount(MANAGER_ID, "manager1", AccountRole.FACILITIES_MANAGER, true);
        insertAccount(INACTIVE_ID, "inactive1", AccountRole.REQUESTER, false);
    }

    @Test
    void reqSh001ValidUsersReachOnlyTheirRoleDashboard() throws Exception {
        Cookie requester = login("REQUESTER1", VALID_PASSWORD, "/requester");
        mockMvc.perform(get("/requester").cookie(requester))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Requester workspace")));
        mockMvc.perform(get("/technician").cookie(requester)).andExpect(status().isForbidden());
        mockMvc.perform(get("/manager").cookie(requester)).andExpect(status().isForbidden());

        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(get("/technician").cookie(technician)).andExpect(status().isOk());

        Cookie manager = login("manager1", VALID_PASSWORD, "/manager");
        mockMvc.perform(get("/manager").cookie(manager)).andExpect(status().isOk());
    }

    @Test
    void reqTech001009AssignedQueueAndDetailRenderEscapedHistoryAndLocalTimes() throws Exception {
        UUID requestId = UUID.fromString("30000000-0000-0000-0000-000000000051");
        UUID categoryId = UUID.fromString("30000000-0000-0000-0000-000000000061");
        UUID priorTechnician = UUID.fromString("30000000-0000-0000-0000-000000000005");
        insertAccount(priorTechnician, "previous-tech", AccountRole.TECHNICIAN, false);
        OffsetDateTime time = OffsetDateTime.parse("2026-10-18T01:02:03Z");
        jdbc.update("INSERT INTO facilityflow.category (id, name) VALUES (?, 'Electrical')", categoryId);
        jdbc.update("""
                INSERT INTO facilityflow.maintenance_request
                    (id, display_id, assigned_technician_id, category_id, status, manager_priority,
                     reported_urgency, assigned_at, title, description, location,
                     version, created_at, updated_at)
                VALUES (?, 'FF-000051', ?, ?, 'IN_PROGRESS', 'HIGH', 'EMERGENCY', ?,
                        'Light <script>canary</script>', 'Check <b>panel</b>', 'Block A', 3, ?, ?)
                """, requestId, TECHNICIAN_ID, categoryId, time, time, time);
        jdbc.update("""
                INSERT INTO facilityflow.requester_update (id, request_id, author_id, note, created_at)
                VALUES (?, ?, ?, 'Update <script>canary</script>', ?)
                """, UUID.fromString("30000000-0000-0000-0000-000000000071"), requestId, REQUESTER_ID, time);
        jdbc.update("""
                INSERT INTO facilityflow.work_log (id, request_id, author_id, note, minutes, created_at)
                VALUES (?, ?, ?, 'Prior inspection <b>note</b>', 12, ?)
                """, UUID.fromString("30000000-0000-0000-0000-000000000072"), requestId, priorTechnician, time);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String queue = mockMvc.perform(get("/technician/requests").cookie(technician))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        assertThat(queue).contains("FF-000051", "&lt;script&gt;canary&lt;/script&gt;", "09:02", "/css/technician.css")
                .doesNotContain("<script>", "<style>");
        String detail = mockMvc.perform(get("/technician/requests/FF-000051").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("&lt;b&gt;panel&lt;/b&gt;", "previous-tech display", "12", "09:02", "2026")
                .doesNotContain("<script>", "<b>panel</b>");
        mockMvc.perform(get("/technician/requests/FF-000051")
                        .cookie(login("requester1", VALID_PASSWORD, "/requester")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reqTech002InvalidFilterRendersErrorsWithoutQueueOrBroadenedResults() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(get("/technician/requests").cookie(technician)
                        .param("q", "<script>search</script>").param("status", "INVALID")
                        .param("category", "not-a-uuid"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Choose a valid status.", "Choose a valid category.", "not-a-uuid", "&lt;script&gt;")
                .doesNotContain("<script>", "Completed, awaiting review");
    }

    @Test
    void reqTech009MissingAndUnassignedDetailsRenderTheSameSafe404() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        for (String displayId : new String[]{"bad-id", "FF-000000", "FF-999999"}) {
            mockMvc.perform(get("/technician/requests/" + displayId).cookie(technician))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("The requested work is not available.")));
        }
    }

    @Test
    void reqTech004006RequiredVersionedFormsFollowPersistedStateAndIncludeCsrf() throws Exception {
        seedTechnicianCommandRequest("ASSIGNED", 4);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String assigned = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(assigned).contains("/technician/requests/FF-000081/start", "name=\"version\"", "value=\"4\"", "_csrf")
                .doesNotContain("/technician/requests/FF-000081/work-logs");
        jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'IN_PROGRESS' WHERE display_id = 'FF-000081'");
        String inProgress = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(inProgress).contains("/technician/requests/FF-000081/work-logs", "name=\"note\"", "name=\"minutes\"", "required",
                "/js/technician-commands.js").doesNotContain("/technician/requests/FF-000081/start", "onclick=", "<style>");
        mockMvc.perform(get("/js/technician-commands.js").cookie(technician))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
    }

    @Test
    void reqTech004005SuccessfulCommandPostsUseSafeSeeOtherRedirect() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/technician/requests/FF-000081/start").cookie(technician).with(csrf())
                        .param("version", "4"))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/technician/requests/FF-000081"));
        org.mockito.Mockito.verify(commandService).startWork("FF-000081", 4);
        mockMvc.perform(post("/technician/requests/FF-000081/work-logs").cookie(technician).with(csrf())
                        .param("version", "5").param("note", "  Inspected lamp  ").param("minutes", "15"))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/technician/requests/FF-000081"));
        org.mockito.Mockito.verify(commandService).appendWorkLog("FF-000081", 5, "  Inspected lamp  ", 15);
    }

    @Test
    void reqSh010MalformedCommandInputsRenderEscapedUnsavedValuesWithoutSubmission() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 4);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(post("/technician/requests/FF-000081/work-logs").cookie(technician).with(csrf())
                        .param("version", "not-a-version").param("note", "<script>unsaved</script>").param("minutes", "1.5"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;unsaved&lt;/script&gt;", "1.5").doesNotContain("<script>unsaved</script>");
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    @Test
    void reqTech010StaleWriteRendersCurrentAssignedStateAndPreservesNoteWithoutReplay() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 5);
        var conflict = new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.CONFLICT);
        org.mockito.Mockito.doThrow(conflict).when(commandService).appendWorkLog("FF-000081", 4, "Unsaved <b>inspection</b>", 25);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(post("/technician/requests/FF-000081/work-logs").cookie(technician).with(csrf())
                        .param("version", "4").param("note", "Unsaved <b>inspection</b>").param("minutes", "25"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;b&gt;inspection&lt;/b&gt;", "25", "IN PROGRESS").doesNotContain("<b>inspection</b>");
        org.mockito.Mockito.verify(commandService, org.mockito.Mockito.times(1))
                .appendWorkLog("FF-000081", 4, "Unsaved <b>inspection</b>", 25);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facilityflow.work_log", Integer.class)).isZero();
    }

    @Test
    void reqTech010ReassignedAndMissingCommandsRenderSafe404WithoutDetails() throws Exception {
        seedTechnicianCommandRequest("ASSIGNED", 4);
        jdbc.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE display_id = 'FF-000081'", MANAGER_ID);
        var unavailable = new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.NOT_AVAILABLE);
        org.mockito.Mockito.doThrow(unavailable).when(commandService).startWork("FF-000081", 4);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(post("/technician/requests/FF-000081/start").cookie(technician).with(csrf())
                        .param("version", "4"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("Private assigned title", "Private assigned description", "name=\"version\"");
    }

    @Test
    void reqSh010OperationalCommandFailureRendersSafe503WithUnsavedNote() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 4);
        org.mockito.Mockito.doThrow(new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.UNAVAILABLE))
                .when(commandService).appendWorkLog("FF-000081", 4, "Inspect <panel>", 25);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(post("/technician/requests/FF-000081/work-logs").cookie(technician).with(csrf())
                        .param("version", "4").param("note", "Inspect <panel>").param("minutes", "25"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Inspect &lt;panel&gt;").doesNotContain("SQLException", "jdbc:", "<panel>");
    }

    @Test
    void reqSh010StaleLogKeepsDraftAfterStateChangesOrAssignmentDisappears() throws Exception {
        seedTechnicianCommandRequest("ASSIGNED", 5);
        String note = "Unsent <inspection> draft";
        org.mockito.Mockito.doThrow(new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.CONFLICT))
                .when(commandService).appendWorkLog("FF-000081", 4, note, 35);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String changed = mockMvc.perform(post("/technician/requests/FF-000081/work-logs").cookie(technician).with(csrf())
                        .param("version", "4").param("note", note).param("minutes", "35"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(changed).contains("Unsent &lt;inspection&gt; draft", "35")
                .doesNotContain("Unsent <inspection> draft", "/technician/requests/FF-000081/work-logs");
        jdbc.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE display_id = 'FF-000081'", MANAGER_ID);
        String reassigned = mockMvc.perform(post("/technician/requests/FF-000081/work-logs").cookie(technician).with(csrf())
                        .param("version", "4").param("note", note).param("minutes", "35"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(reassigned).contains("Unsent &lt;inspection&gt; draft", "35")
                .doesNotContain("Private assigned title", "Private assigned description", "Unsent <inspection> draft");
    }

    @Test
    void reqTech011CommandEndpointsRejectCsrfCrossRoleAndForbiddenOperations() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        Cookie manager = login("manager1", VALID_PASSWORD, "/manager");
        for (String action : new String[]{"start", "work-logs"}) {
            String route = "/technician/requests/FF-000081/" + action;
            mockMvc.perform(post(route).cookie(technician).param("version", "0"))
                    .andExpect(status().isForbidden());
            for (Cookie otherRole : new Cookie[]{requester, manager}) {
                mockMvc.perform(post(route).cookie(otherRole).with(csrf()).param("version", "0"))
                        .andExpect(status().isForbidden());
            }
        }
        for (String forbidden : new String[]{"assign", "reassign", "priority", "edit", "cancel", "close", "reopen", "status"}) {
            mockMvc.perform(post("/technician/requests/FF-000081/" + forbidden).cookie(technician).with(csrf())
                            .param("status", "CLOSED").param("version", "0"))
                    .andExpect(status().isNotFound());
        }
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    @Test
    void reqTech007CompletionFormIsVersionedRequiredCsrfProtectedAndStateGated() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 5);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/technician/requests/FF-000081/complete", "name=\"resolutionSummary\"", "required", "_csrf", "value=\"5\"")
                .doesNotContain("maxlength=\"2000\"");
        jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED' WHERE display_id = 'FF-000081'");
        String assigned = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(assigned).doesNotContain("/technician/requests/FF-000081/complete");
    }

    @Test
    void reqTech008CompletionSuccessRedirectsAndCompletedDetailHasNoMutationForms() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 5);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "5").param("resolutionSummary", "  Replaced <panel> and verified operation.  "))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/technician/requests/FF-000081"));
        org.mockito.Mockito.verify(commandService).completeWork("FF-000081", 5, "  Replaced <panel> and verified operation.  ");
        // Command is doubled for MVC; actual PG tests prove the persisted write.
        jdbc.update("""
                UPDATE facilityflow.maintenance_request SET status = 'COMPLETED', version = 6,
                    resolution_summary = 'Replaced <panel> and verified operation.',
                    completed_at = TIMESTAMP WITH TIME ZONE '2026-10-18 11:00:00Z'
                WHERE display_id = 'FF-000081'
                """);
        String html = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("COMPLETED", "Replaced &lt;panel&gt; and verified operation.", "19:00", "Assigned")
                .doesNotContain("Replaced <panel>", "/technician/requests/FF-000081/start", "/technician/requests/FF-000081/work-logs", "/technician/requests/FF-000081/complete");
    }

    @Test
    void reqTech007MissingLogAndInvalidResolutionRenderActionablePreservedErrors() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 5);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        org.mockito.Mockito.doThrow(new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.WORK_LOG_REQUIRED))
                .when(commandService).completeWork("FF-000081", 5, "Resolution <unsaved> summary");
        String missingLog = mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "5").param("resolutionSummary", "Resolution <unsaved> summary"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(missingLog).contains("Work Log", "Resolution &lt;unsaved&gt; summary")
                .doesNotContain("Resolution <unsaved>");
        org.mockito.Mockito.doThrow(new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.INVALID_INPUT))
                .when(commandService).completeWork("FF-000081", 5, "<bad>");
        String invalid = mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "5").param("resolutionSummary", "<bad>"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(invalid).contains("&lt;bad&gt;", "aria-invalid=\"true\"").doesNotContain("><bad></textarea>");
    }

    @Test
    void reqTech010CompletionConflictAndReassignmentPreserveOnlyOwnedDraft() throws Exception {
        seedTechnicianCommandRequest("ASSIGNED", 6);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        org.mockito.Mockito.doThrow(new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.CONFLICT))
                .when(commandService).completeWork("FF-000081", 5, "Unsaved <resolution> text");
        String conflict = mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "5").param("resolutionSummary", "Unsaved <resolution> text"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(conflict).contains("Unsaved &lt;resolution&gt; text")
                .doesNotContain("/technician/requests/FF-000081/complete", "Unsaved <resolution>");
        jdbc.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE display_id = 'FF-000081'", MANAGER_ID);
        String reassigned = mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "5").param("resolutionSummary", "Unsaved <resolution> text"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(reassigned).contains("Unsaved &lt;resolution&gt; text")
                .doesNotContain("Private assigned title", "Private assigned description", "name=\"version\"");
        org.mockito.Mockito.verify(commandService, org.mockito.Mockito.times(2)).completeWork("FF-000081", 5, "Unsaved <resolution> text");
    }

    @Test
    void reqSh010CompletionOperationalFailurePreservesDraftAndMalformedVersionDoesNotSubmit() throws Exception {
        seedTechnicianCommandRequest("IN_PROGRESS", 5);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        org.mockito.Mockito.doThrow(new sg.edu.nus.facilityflow.technician.TechnicianCommandException(
                sg.edu.nus.facilityflow.technician.TechnicianCommandException.Code.UNAVAILABLE))
                .when(commandService).completeWork("FF-000081", 5, "Unsaved <resolution> text");
        String unavailable = mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "5").param("resolutionSummary", "Unsaved <resolution> text"))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(unavailable).contains("Unsaved &lt;resolution&gt; text").doesNotContain("SQLException", "jdbc:");
        org.mockito.Mockito.reset(commandService);
        mockMvc.perform(post("/technician/requests/FF-000081/complete").cookie(technician).with(csrf())
                        .param("version", "9223372036854775808").param("resolutionSummary", "Unsaved <resolution> text"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    @Test
    void reqTech011CompletionRequiresCsrfAndTechnicianRole() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String route = "/technician/requests/FF-000081/complete";
        mockMvc.perform(post(route).cookie(technician).param("version", "0"))
                .andExpect(status().isForbidden());
        for (Cookie cookie : new Cookie[]{login("requester1", VALID_PASSWORD, "/requester"), login("manager1", VALID_PASSWORD, "/manager")}) {
            mockMvc.perform(post(route).cookie(cookie).with(csrf()).param("version", "0")
                            .param("resolutionSummary", "Valid completion summary"))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    private void seedTechnicianCommandRequest(String state, long version) {
        UUID categoryId = UUID.fromString("30000000-0000-0000-0000-000000000082");
        OffsetDateTime now = OffsetDateTime.parse("2026-10-18T08:00:00Z");
        jdbc.update("INSERT INTO facilityflow.category (id, name) VALUES (?, 'Electrical')", categoryId);
        jdbc.update("""
                INSERT INTO facilityflow.maintenance_request
                    (id, display_id, assigned_technician_id, category_id, status, manager_priority,
                     reported_urgency, assigned_at, title, description, location, version, created_at, updated_at)
                VALUES (?, 'FF-000081', ?, ?, ?, 'HIGH', 'NORMAL', ?,
                        'Private assigned title', 'Private assigned description', 'Block A', ?, ?, ?)
                """, UUID.fromString("30000000-0000-0000-0000-000000000081"), TECHNICIAN_ID,
                categoryId, state, now, version, now, now);
    }

    @Test
    void reqSh003InvalidUnknownAndInactiveAccountsHaveIdenticalExternalFailure() throws Exception {
        String wrongPassword = failedLoginRedirect("requester1", "not-the-password");
        String unknown = failedLoginRedirect("does-not-exist", VALID_PASSWORD);
        String inactive = failedLoginRedirect("inactive1", VALID_PASSWORD);

        assertThat(wrongPassword).isEqualTo("/login?error");
        assertThat(unknown).isEqualTo(wrongPassword);
        assertThat(inactive).isEqualTo(wrongPassword);
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Invalid username or password")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("inactive1"))));
    }

    @Test
    void reqSh002UnauthenticatedAccessRedirectsAndCrossRoleAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/requester"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        mockMvc.perform(get("/manager").cookie(requester)).andExpect(status().isForbidden());
    }

    @Test
    void nfrSec001StateChangingRequestsRequireCsrf() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");

        mockMvc.perform(post("/logout").cookie(requester)).andExpect(status().isForbidden());
        mockMvc.perform(get("/requester").cookie(requester)).andExpect(status().isOk());
    }

    @Test
    void aiSec029RequesterSmartReportIsPostCsrfRoleProtectedAndNonCacheable() throws Exception {
        String body = """
                {"roughTitle":"Flickering light","roughDescription":"The ceiling light flickers.",
                 "roughLocation":"Room 4","selectedCategory":"Electrical","selectedUrgency":"High"}
                """;
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        mockMvc.perform(post("/requester/smart-report")
                        .cookie(requester).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/requester/smart-report")
                        .cookie(technician).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        MvcResult smartReport = mockMvc.perform(post("/requester/smart-report")
                        .cookie(requester).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(smartReport))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unavailable")))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI_UNAVAILABLE")));
    }

    @Test
    void aiSec029ManagerTriageIsPostCsrfRoleProtectedAndNonCacheable() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/manager/requests/FF-000051/triage")
                        .cookie(technician).with(csrf()))
                .andExpect(status().isForbidden());

        Cookie manager = login("manager1", VALID_PASSWORD, "/manager");
        MvcResult triage = mockMvc.perform(post("/manager/requests/FF-000051/triage")
                        .cookie(manager).with(csrf()))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(triage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unavailable")))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI_UNAVAILABLE")));
    }

    @Test
    void reqMgr024TriageReviewPageIsManagerOnlyCsrfProtectedAndNonCacheable() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        mockMvc.perform(get("/manager/requests/FF-000051/triage-assistant").cookie(requester))
                .andExpect(status().isForbidden());

        Cookie manager = login("manager1", VALID_PASSWORD, "/manager");
        mockMvc.perform(get("/manager/requests/FF-000051/triage-assistant").cookie(manager))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Advisory only")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Nothing is saved or assigned")));

        mockMvc.perform(post("/manager/requests/FF-000051/triage-assistant").cookie(manager))
                .andExpect(status().isForbidden());

        MvcResult review = mockMvc.perform(post("/manager/requests/FF-000051/triage-assistant")
                        .cookie(manager).with(csrf()))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(review))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "AI is unavailable")));
    }

    @Test
    void reqAi005WorkPlanFailureExposesSafeCodeAndMessage() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        MvcResult workPlan = mockMvc.perform(post("/technician/requests/FF-000051/work-plan")
                        .cookie(technician).with(csrf()))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(workPlan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unavailable")));
    }

    @Test
    void reqTech012016WorkPlanPanelIsEligibleReadOnlyAndKeepsManualForms() throws Exception {
        seedTechnicianCommandRequest("ASSIGNED", 4);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        for (String state : new String[]{"ASSIGNED", "IN_PROGRESS"}) {
            jdbc.update("UPDATE facilityflow.maintenance_request SET status = ? WHERE display_id = 'FF-000081'", state);
            String html = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("id=\"work-plan-panel\"", "id=\"work-plan-form\"",
                    "id=\"work-plan-result\"", "id=\"work-plan-discard\"", "_csrf",
                    "/technician/requests/FF-000081/work-plan", "/js/technician-work-plan.js",
                    "work-plan-safetyPrecautions", "work-plan-stopOrEscalateWhen", "<noscript>");
            assertThat(html.indexOf("work-plan-safetyPrecautions"))
                    .isLessThan(html.indexOf("work-plan-diagnosticSteps"));
            assertThat(html.indexOf("work-plan-stopOrEscalateWhen"))
                    .isLessThan(html.indexOf("work-plan-diagnosticSteps"));
            assertThat(html).doesNotContain("onclick=", "<style>");
            assertThat(html).contains(state.equals("ASSIGNED") ? "/FF-000081/start" : "/FF-000081/work-logs");
        }
        jdbc.update("UPDATE facilityflow.maintenance_request SET status = 'COMPLETED' WHERE display_id = 'FF-000081'");
        String completed = mockMvc.perform(get("/technician/requests/FF-000081").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(completed).doesNotContain("id=\"work-plan-panel\"", "id=\"work-plan-form\"");
        org.mockito.Mockito.verifyNoInteractions(workPlanService, commandService);
    }

    @Test
    void aiSec029WorkPlanRequiresPostCsrfAndTechnicianBeforeService() throws Exception {
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/technician/requests/FF-000081/work-plan").cookie(technician))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/technician/requests/FF-000081/work-plan").cookie(technician))
                .andExpect(status().isMethodNotAllowed());
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        mockMvc.perform(post("/technician/requests/FF-000081/work-plan").cookie(requester).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/technician/requests/FF-000081/work-plan").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/js/technician-work-plan.js").cookie(technician))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
        org.mockito.Mockito.verifyNoInteractions(workPlanService, commandService);
    }

    @Test
    void reqTech014017WorkPlanJsonIsWholeTypedNoStoreAndNeverCallsCommands() throws Exception {
        var plan = new sg.edu.nus.facilityflow.ai.technician.WorkPlanResult(
                java.util.List.of("Inspect from a safe distance."),
                java.util.List.of("Keep clear of energized equipment."),
                java.util.List.of("Inspection light"), java.util.List.of("When did the fault begin?"),
                java.util.List.of("Record visible symptoms."),
                java.util.List.of("Stop and contact the supervisor if isolation cannot be confirmed."),
                "These are suggestions for an initial assessment.");
        org.mockito.Mockito.when(workPlanService.request("FF-000081"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        new sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse.Success(plan)));
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        MvcResult result = mockMvc.perform(post("/technician/requests/FF-000081/work-plan")
                        .cookie(technician).with(csrf()))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.plan.safetyPrecautions[0]").value("Keep clear of energized equipment."))
                .andExpect(jsonPath("$.plan.stopOrEscalateWhen[0]").value(plan.stopOrEscalateWhen().getFirst()))
                .andExpect(jsonPath("$.plan.rationale").value(plan.rationale()))
                .andExpect(jsonPath("$.code").doesNotExist());
        for (var code : sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode.values()) {
            org.mockito.Mockito.when(workPlanService.request("FF-000081"))
                    .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                            sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse.failure(code)));
            MvcResult failure = mockMvc.perform(post("/technician/requests/FF-000081/work-plan")
                            .cookie(technician).with(csrf()))
                    .andExpect(request().asyncStarted()).andReturn();
            mockMvc.perform(asyncDispatch(failure)).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(jsonPath("$.code").value(code.name()))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.plan").doesNotExist());
        }
        org.mockito.Mockito.verifyNoInteractions(commandService);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facilityflow.work_log", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request", Integer.class)).isZero();
    }

    @Test
    void reqSh004LogoutInvalidatesTheServerSession() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");

        mockMvc.perform(post("/logout").cookie(requester).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
        mockMvc.perform(get("/requester").cookie(requester))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void reqSh012SharedNavigationShowsOnlyCurrentRoleWorkspace() throws Exception {
        String[][] roles = {{"requester1", "/requester"}, {"technician1", "/technician"}, {"manager1", "/manager"}};
        for (String[] role : roles) {
            Cookie session = login(role[0], VALID_PASSWORD, role[1]);
            for (String page : new String[]{role[1], "/account/password", "/about"}) {
                String html = mockMvc.perform(get(page).cookie(session)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                assertThat(html).contains("href=\"" + role[1] + "\"", "href=\"/account/password\"",
                        "href=\"/about\"", "action=\"/logout\"", role[0], "_csrf", "<nav", "id=\"main-content\"");
                for (String[] other : roles) {
                    if (!role[1].equals(other[1])) assertThat(html).doesNotContain("href=\"" + other[1] + "\"");
                }
            }
        }
        org.mockito.Mockito.verifyNoInteractions(passwordChanges);
    }

    @Test
    void nfrSec001PasswordPostRequiresAuthenticationAndCsrfWhileLoginScriptIsPublic() throws Exception {
        mockMvc.perform(get("/js/form-submission.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
        for (String path : new String[]{"/account/password", "/about"}) {
            mockMvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        }
        mockMvc.perform(post("/account/password").with(csrf()))
                .andExpect(status().is3xxRedirection());
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/account/password").cookie(technician)
                        .param("currentPassword", VALID_PASSWORD).param("newPassword", "synthetic-new-value")
                        .param("confirmPassword", "synthetic-new-value"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(passwordChanges);
    }

    @Test
    void reqSh004010OwnPasswordUsesFixed303AndRetainsCurrentWebSessionWithoutSecretEcho() throws Exception {
        // The service double isolates web/session behavior; real PostgreSQL password/audit
        // and session_version semantics belong to JdbcPasswordChangeRepositoryTest.
        String newPassword = "synthetic-new-value";
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(post("/account/password").cookie(technician).with(csrf())
                        .param("currentPassword", VALID_PASSWORD).param("newPassword", newPassword)
                        .param("confirmPassword", newPassword).param("accountId", MANAGER_ID.toString())
                        .param("returnTo", "https://invalid.example/"))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/account/password?changed"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
        org.mockito.Mockito.verify(passwordChanges).changeOwnPassword(VALID_PASSWORD, newPassword, newPassword);
        String page = mockMvc.perform(get("/account/password?changed").cookie(technician))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("name=\"currentPassword\"", "name=\"newPassword\"", "name=\"confirmPassword\"",
                        "autocomplete=\"current-password\"", "autocomplete=\"new-password\"", "required")
                .doesNotContain(VALID_PASSWORD, newPassword, "https://invalid.example/");
        mockMvc.perform(get("/technician").cookie(technician)).andExpect(status().isOk());
        jdbc.query("SELECT attribute_bytes FROM facilityflow.spring_session_attributes", (rs, row) -> rs.getBytes(1))
                .forEach(bytes -> assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
                        .doesNotContain(newPassword, VALID_PASSWORD));
    }

    @Test
    void reqSh010PasswordFailureRendersAssociatedErrorsWithoutReturningSubmittedSecrets() throws Exception {
        String currentPassword = "synthetic-current";
        String newPassword = "synthetic-next-secret";
        var problem = new sg.edu.nus.facilityflow.identity.PasswordChangeException(
                sg.edu.nus.facilityflow.identity.PasswordChangeException.Code.INCORRECT_CURRENT_PASSWORD,
                java.util.Map.of("currentPassword", "The current password is incorrect."));
        org.mockito.Mockito.doThrow(problem).when(passwordChanges)
                .changeOwnPassword(currentPassword, newPassword, newPassword);
        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        String html = mockMvc.perform(post("/account/password").cookie(technician).with(csrf())
                        .param("currentPassword", currentPassword).param("newPassword", newPassword)
                        .param("confirmPassword", newPassword))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("The current password is incorrect.", "aria-invalid=\"true\"", "aria-describedby=")
                .doesNotContain(currentPassword, newPassword);
        mockMvc.perform(get("/technician").cookie(technician)).andExpect(status().isOk());
    }

    @Test
    void reqSh004DeactivationAndSessionVersionChangesRejectExistingSessions() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        jdbc.update("UPDATE facilityflow.user_account SET active = FALSE WHERE id = ?", REQUESTER_ID);

        mockMvc.perform(get("/requester").cookie(requester))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        jdbc.update("UPDATE facilityflow.user_account SET active = TRUE WHERE id = ?", REQUESTER_ID);
        Cookie newSession = login("requester1", VALID_PASSWORD, "/requester");
        jdbc.update("UPDATE facilityflow.user_account SET session_version = session_version + 1 WHERE id = ?", REQUESTER_ID);
        mockMvc.perform(get("/requester").cookie(newSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void reqSh004PersistedRoleChangeTakesEffectWithoutNewLogin() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        jdbc.update(
                "UPDATE facilityflow.user_account SET role = 'FACILITIES_MANAGER', updated_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC),
                REQUESTER_ID);

        mockMvc.perform(get("/requester").cookie(requester)).andExpect(status().isForbidden());
        mockMvc.perform(get("/manager").cookie(requester)).andExpect(status().isOk());
    }

    @Test
    void reqSh005SessionCookieIsBrowserScopedSecureHttpOnlyAndSameSiteLax() throws Exception {
        MvcResult result = performSuccessfulLogin("requester1", VALID_PASSWORD, "/requester");
        String setCookie = result.getResponse().getHeader("Set-Cookie");

        assertThat(setCookie).contains("SESSION=");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).doesNotContain("Max-Age");
        Integer inactiveInterval = jdbc.queryForObject(
                "SELECT max_inactive_interval FROM facilityflow.spring_session", Integer.class);
        assertThat(inactiveInterval).isNegative();
    }

    @Test
    void reqSh003PasswordIsStoredOnlyAsAnArgon2Hash() {
        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM facilityflow.user_account WHERE id = ?", String.class, REQUESTER_ID);

        assertThat(stored).isNotEqualTo(VALID_PASSWORD).startsWith("{argon2}$argon2id$");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, stored)).isTrue();
    }

    @Test
    void nfrSec002SessionSerializationContainsNoPasswordOrPasswordHash() throws Exception {
        performSuccessfulLogin("requester1", VALID_PASSWORD, "/requester");

        var serializedAttributes = jdbc.query(
                "SELECT attribute_bytes FROM facilityflow.spring_session_attributes",
                (resultSet, rowNumber) -> resultSet.getBytes(1));
        assertThat(serializedAttributes).isNotEmpty();
        assertThat(serializedAttributes)
                .allSatisfy(bytes -> {
                    String serialized = new String(bytes, StandardCharsets.ISO_8859_1);
                    assertThat(serialized).doesNotContain(VALID_PASSWORD);
                    assertThat(serialized).doesNotContain(validPasswordHash);
                    assertThat(serialized).doesNotContain("$argon2id$");
                });
    }

    @Test
    void nfrSec002FailedLoginNeverStoresTheAttemptedCredentialInAnExistingSession() throws Exception {
        Cookie session = login("requester1", VALID_PASSWORD, "/requester");
        String attempted = "synthetic-wrong-secret";
        mockMvc.perform(post("/login").cookie(session).with(csrf())
                        .param("username", "requester1").param("password", attempted))
                .andExpect(redirectedUrl("/login?error"));
        jdbc.query("SELECT attribute_bytes FROM facilityflow.spring_session_attributes", (rs, row) -> rs.getBytes(1))
                .forEach(bytes -> assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
                        .doesNotContain(attempted, VALID_PASSWORD, "BadCredentialsException"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facilityflow.spring_session_attributes WHERE attribute_name = ?",
                Integer.class, org.springframework.security.web.WebAttributes.AUTHENTICATION_EXCEPTION)).isZero();
    }

    @Test
    void nfrA11y001LoginAndLogoutWorkWithoutJavaScript() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<label for=\"username\">Username</label>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<label for=\"password\">Password</label>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("method=\"post\"")));
    }

    @Test
    void reqSh003ExistingPbkdf2DemoAccountCanLogIn() throws Exception {
        var hash = "{pbkdf2@SpringSecurity_v5_8}" +
                org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
                        .defaultsForSpringSecurity_v5_8().encode(VALID_PASSWORD);
        jdbc.update("UPDATE facilityflow.user_account SET password_hash = ? WHERE id = ?", hash, REQUESTER_ID);
        login("REQUESTER1", VALID_PASSWORD, "/requester");
    }

    private Cookie login(String username, String password, String destination) throws Exception {
        MvcResult result = performSuccessfulLogin(username, password, destination);
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("Spring Session cookie").isNotNull();
        return cookie;
    }

    private MvcResult performSuccessfulLogin(
            String username,
            String password,
            String destination) throws Exception {
        return mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(destination))
                .andReturn();
    }

    private String failedLoginRedirect(String username, String password) throws Exception {
        return mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private void insertAccount(UUID id, String username, AccountRole role, boolean active) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                        INSERT INTO facilityflow.user_account (
                            id, username, normalized_username, display_name, password_hash,
                            role, active, session_version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                username,
                username.toLowerCase(Locale.ROOT),
                username + " display",
                validPasswordHash,
                role.name(),
                active,
                0L,
                now,
                now);
    }
}
