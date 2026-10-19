package sg.edu.nus.facilityflow.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import sg.edu.nus.facilityflow.identity.Account;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.technician.TechnicianCommandException;
import sg.edu.nus.facilityflow.technician.TechnicianCommandService;
import sg.edu.nus.facilityflow.technician.TechnicianQueryException;
import sg.edu.nus.facilityflow.technician.TechnicianQueryService;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

class TechnicianCommandControllerTest {
    private static final UUID CATEGORY = UUID.fromString("8e400000-0000-0000-0000-000000000001");
    private static final String DISPLAY_ID = "FF-000321";

    private TechnicianCommandService commands;
    private TechnicianQueryService queries;
    private TechnicianCommandController controller;
    private CurrentAccountPrincipal principal;

    @BeforeEach
    void setUp() {
        commands = mock(TechnicianCommandService.class);
        queries = mock(TechnicianQueryService.class);
        controller = new TechnicianCommandController(commands, queries);
        principal = CurrentAccountPrincipal.from(new Account(UUID.randomUUID(), "tech.user", "Tech User",
                "synthetic-test-hash", AccountRole.TECHNICIAN, true, 7,
                Instant.parse("2026-10-17T00:00:00Z"), Instant.parse("2026-10-17T00:00:00Z")));
    }

    @Test
    @DisplayName("REQ-TECH-004/SH-010: valid start command uses POST/redirect/303 to a fixed detail path")
    void startUsesSafeSeeOtherRedirect() throws Exception {
        Object outcome = controller.startWork(principal, DISPLAY_ID, "4", new ExtendedModelMap(), response());

        RedirectView redirect = assertInstanceOf(RedirectView.class, outcome);
        assertRedirect(redirect);
        verify(commands).startWork(DISPLAY_ID, 4L);
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("REQ-TECH-005/006/SH-010: valid Work Log command parses whole minutes and redirects after save")
    void workLogParsesAndSubmitsRawFields() throws Exception {
        Object outcome = controller.appendWorkLog(principal, DISPLAY_ID, "9", " valve  checked ", "1440",
                new ExtendedModelMap(), response());

        RedirectView redirect = assertInstanceOf(RedirectView.class, outcome);
        assertRedirect(redirect);
        verify(commands).appendWorkLog(DISPLAY_ID, 9L, " valve  checked ", 1440);
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("REQ-SH-010: malformed versions and minutes preserve raw form input and never invoke commands")
    void malformedRawNumbersAreFieldErrors() {
        var startModel = new ExtendedModelMap();
        var startResponse = response();
        Object start = controller.startWork(principal, DISPLAY_ID, "9223372036854775808", startModel, startResponse);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, startResponse.getStatus());
        assertEquals("technician/request-detail", assertInstanceOf(ModelAndView.class, start).getViewName());
        assertEquals("9223372036854775808", startModel.getAttribute("submittedVersion"));
        assertEquals("start", startModel.getAttribute("commandAction"));
        assertEquals("Enter a valid whole number.", ((Map<?, ?>) startModel.getAttribute("commandFieldErrors")).get("version"));

        var logModel = new ExtendedModelMap();
        var logResponse = response();
        Object log = controller.appendWorkLog(principal, DISPLAY_ID, "2.0", " <script>\nkeep raw ", "999999999999999",
                logModel, logResponse);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, logResponse.getStatus());
        assertEquals("technician/request-detail", assertInstanceOf(ModelAndView.class, log).getViewName());
        assertEquals("2.0", logModel.getAttribute("submittedVersion"));
        var submitted = assertInstanceOf(TechnicianCommandController.SubmittedWorkLog.class,
                logModel.getAttribute("submittedWorkLog"));
        assertEquals(" <script>\nkeep raw ", submitted.note());
        assertEquals("999999999999999", submitted.minutes());
        assertTrue(((Map<?, ?>) logModel.getAttribute("commandFieldErrors")).containsKey("version"));
        assertTrue(((Map<?, ?>) logModel.getAttribute("commandFieldErrors")).containsKey("minutes"));
        verifyNoInteractions(commands);
        verify(queries, org.mockito.Mockito.times(2)).detail(DISPLAY_ID);
    }

    @Test
    @DisplayName("REQ-TECH-010/SH-010: current assigned details and submitted Work Log input survive a conflict")
    void conflictRendersFreshDetailsAndPreservesSubmittedValues() {
        RequestDetail fresh = detail(RequestStatus.IN_PROGRESS, 10);
        when(queries.detail(DISPLAY_ID)).thenReturn(fresh);
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.CONFLICT))
                .when(commands).appendWorkLog(DISPLAY_ID, 8, "  <script> checked\n twice  ", 30);
        var model = new ExtendedModelMap();
        var response = response();

        Object outcome = controller.appendWorkLog(principal, DISPLAY_ID, "8", "  <script> checked\n twice  ", "30",
                model, response);

        assertEquals(HttpServletResponse.SC_CONFLICT, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("technician/request-detail", assertInstanceOf(ModelAndView.class, outcome).getViewName());
        assertEquals(fresh, model.getAttribute("detail"));
        assertEquals(10, ((RequestDetail) model.getAttribute("detail")).version());
        assertEquals("The current assigned request is shown below. Review its status and submit again only if the action is still available.",
                model.getAttribute("commandRefreshGuidance"));
        assertEquals("8", model.getAttribute("submittedVersion"));
        var submitted = assertInstanceOf(TechnicianCommandController.SubmittedWorkLog.class,
                model.getAttribute("submittedWorkLog"));
        assertEquals("  <script> checked\n twice  ", submitted.note());
        assertEquals("30", submitted.minutes());
        verify(commands).appendWorkLog(DISPLAY_ID, 8, "  <script> checked\n twice  ", 30);
    }

    @Test
    @DisplayName("REQ-TECH-010/SH-006: reassigned work renders generic 404 without exposing stale detail")
    void inaccessibleAfterFailureNeverRendersDetailOrRetriesCommand() {
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.NOT_AVAILABLE))
                .when(commands).startWork(DISPLAY_ID, 0);
        when(queries.detail(DISPLAY_ID)).thenThrow(
                new TechnicianQueryException(TechnicianQueryException.Code.NOT_AVAILABLE));
        var model = new ExtendedModelMap();
        var response = response();

        Object outcome = controller.startWork(principal, DISPLAY_ID, "0", model, response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertEquals("The requested work is not available.", model.getAttribute("pageError"));
        assertNull(model.getAttribute("detail"));
        assertEquals("0", model.getAttribute("submittedVersion"));
        verify(commands).startWork(DISPLAY_ID, 0);
        verify(commands, never()).startWork(DISPLAY_ID, 1);
    }

    @Test
    @DisplayName("REQ-SH-002: identity denials propagate to existing authorization handling")
    void accessDeniedIsNotConvertedToBusinessError() {
        doThrow(new AccessDeniedException("denied")).when(commands).startWork(DISPLAY_ID, 1);

        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class,
                () -> controller.startWork(principal, DISPLAY_ID, "1", new ExtendedModelMap(), response()));

        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("REQ-SH-010/NFR-SEC-001: changed detail template guards command models and escapes external form output")
    void detailTemplateUsesEscapedTextExternalScriptAndServerLimits() throws IOException {
        String html = resource("/templates/technician/request-detail.html");
        String javascript = resource("/static/js/technician-commands.js");

        assertTrue(html.contains("/technician/requests/{id}/start(id=${detail.request.displayId})"));
        assertTrue(html.contains("/technician/requests/{id}/work-logs(id=${detail.request.displayId})"));
        assertTrue(html.contains("commandErrors != null and !commandErrors.isEmpty()"));
        assertTrue(html.contains("submittedWorkLog != null"));
        assertFalse(html.matches("(?is).*maxlength\\s*=\\s*['\"]?1000.*"));
        assertFalse(html.contains("th:utext"));
        assertFalse(html.matches("(?is).*\\sstyle\\s*=.*"));
        assertTrue(html.contains("/js/technician-commands.js"));
        assertTrue(javascript.contains("pageshow"));
        assertTrue(javascript.contains("disabled"));
    }

    @Test
    @DisplayName("REQ-SH-010: operational detail reload failure preserves 503 instead of stale details")
    void errorReloadFailureRemainsGenericUnavailable() {
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.CONFLICT))
                .when(commands).startWork(DISPLAY_ID, 0);
        when(queries.detail(DISPLAY_ID)).thenThrow(
                new TechnicianQueryException(TechnicianQueryException.Code.UNAVAILABLE));
        var model = new ExtendedModelMap();
        var response = response();

        controller.startWork(principal, DISPLAY_ID, "0", model, response);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals("Assigned work is temporarily unavailable. Please try again.", model.getAttribute("pageError"));
        assertNull(model.getAttribute("detail"));
        assertNotNull(model.getAttribute("commandErrors"));
    }

    private static RequestDetail detail(RequestStatus status, long version) {
        QueueRow row = new QueueRow(DISPLAY_ID, "Synthetic request", "Block A", CATEGORY, "Electrical",
                status, ManagerPriority.MEDIUM, ReportedUrgency.NORMAL, Instant.parse("2026-10-17T08:00:00Z"));
        return new RequestDetail(row, "Synthetic description", Instant.parse("2026-10-17T08:00:00Z"),
                Instant.parse("2026-10-17T09:00:00Z"), version, null, null, List.of(), List.of());
    }

    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    private static void assertRedirect(RedirectView redirect) throws Exception {
        var response = new MockHttpServletResponse();
        redirect.render(Map.of(), new MockHttpServletRequest("POST", "/technician/requests"), response);
        assertEquals(HttpServletResponse.SC_SEE_OTHER, response.getStatus());
        assertEquals("/technician/requests/" + DISPLAY_ID, response.getRedirectedUrl());
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = TechnicianCommandControllerTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
