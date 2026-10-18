package sg.edu.nus.facilityflow.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

class TechnicianCompletionControllerTest {
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
    @DisplayName("REQ-TECH-007/SH-010: completion parses raw fields and redirects with 303 after save")
    void completionRedirectsAfterCommand() throws Exception {
        Object outcome = controller.completeWork(principal, DISPLAY_ID, "9", "  Repaired pump.\nChecked flow.  ",
                new ExtendedModelMap(), response());

        assertRedirect(assertInstanceOf(RedirectView.class, outcome));
        verify(commands).completeWork(DISPLAY_ID, 9L, "  Repaired pump.\nChecked flow.  ");
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("REQ-SH-010: malformed or missing versions preserve the raw resolution draft")
    void malformedVersionPreservesDraftAndDoesNotComplete() {
        String raw = " <script>unfinished\nresolution ";
        var model = new ExtendedModelMap();
        var response = response();

        Object outcome = controller.completeWork(principal, DISPLAY_ID, null, raw, model, response);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("technician/request-detail", assertInstanceOf(ModelAndView.class, outcome).getViewName());
        assertEquals(raw, model.getAttribute("submittedResolution"));
        assertEquals("complete", model.getAttribute("commandAction"));
        assertTrue(((Map<?, ?>) model.getAttribute("commandFieldErrors")).containsKey("version"));
        var overflowModel = new ExtendedModelMap();
        var overflowResponse = response();
        controller.completeWork(principal, DISPLAY_ID, "9223372036854775808", raw, overflowModel, overflowResponse);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, overflowResponse.getStatus());
        assertEquals(raw, overflowModel.getAttribute("submittedResolution"));
        assertTrue(((Map<?, ?>) overflowModel.getAttribute("commandFieldErrors")).containsKey("version"));
        verify(commands, never()).completeWork(DISPLAY_ID, 0, raw);
    }

    @Test
    @DisplayName("REQ-TECH-007/TECH-A04: missing Work Log gets actionable guidance and keeps draft")
    void missingLogExplainsRequiredAction() {
        String raw = "  Finished repair and tested operation.  ";
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.WORK_LOG_REQUIRED))
                .when(commands).completeWork(DISPLAY_ID, 3, raw);
        var model = new ExtendedModelMap();
        var response = response();

        Object outcome = controller.completeWork(principal, DISPLAY_ID, "3", raw, model, response);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals(raw, model.getAttribute("submittedResolution"));
        assertTrue(((List<?>) model.getAttribute("commandErrors")).contains("Add a Work Log before completion."));
        assertEquals("complete", model.getAttribute("commandAction"));
        verify(queries).detail(DISPLAY_ID);
    }

    @Test
    @DisplayName("REQ-TECH-007: invalid summary receives a specific resolution field error")
    void invalidSummaryHasFieldErrorAndRawValue() {
        String raw = "  too short ";
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.INVALID_INPUT))
                .when(commands).completeWork(DISPLAY_ID, 3, raw);
        var model = new ExtendedModelMap();
        var response = response();

        controller.completeWork(principal, DISPLAY_ID, "3", raw, model, response);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals(raw, model.getAttribute("submittedResolution"));
        assertTrue(((Map<?, ?>) model.getAttribute("commandFieldErrors")).containsKey("resolutionSummary"));
    }

    @Test
    @DisplayName("REQ-TECH-010: conflict shows fresh assigned detail and preserves unsaved completion text")
    void conflictRefreshesDetailAndKeepsDraft() {
        RequestDetail fresh = detail(RequestStatus.IN_PROGRESS, 11);
        when(queries.detail(DISPLAY_ID)).thenReturn(fresh);
        String raw = "  Repaired unit; <script> remains text.  ";
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.CONFLICT))
                .when(commands).completeWork(DISPLAY_ID, 10, raw);
        var model = new ExtendedModelMap();
        var response = response();

        Object outcome = controller.completeWork(principal, DISPLAY_ID, "10", raw, model, response);

        assertEquals(HttpServletResponse.SC_CONFLICT, response.getStatus());
        assertEquals("technician/request-detail", ((ModelAndView) outcome).getViewName());
        assertEquals(fresh, model.getAttribute("detail"));
        assertEquals(raw, model.getAttribute("submittedResolution"));
        assertEquals("10", model.getAttribute("submittedVersion"));
        assertTrue(model.containsAttribute("commandRefreshGuidance"));
        verify(commands).completeWork(DISPLAY_ID, 10, raw);
    }

    @Test
    @DisplayName("REQ-TECH-010/SH-006: inaccessible or unavailable reload keeps draft without exposing protected detail")
    void reloadFailureDoesNotLeakDetailsOrDiscardDraft() {
        String raw = "  Keep this completion draft  ";
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.NOT_AVAILABLE))
                .when(commands).completeWork(DISPLAY_ID, 2, raw);
        doThrow(new TechnicianQueryException(TechnicianQueryException.Code.NOT_AVAILABLE))
                .when(queries).detail(DISPLAY_ID);
        var model = new ExtendedModelMap();
        var response = response();

        controller.completeWork(principal, DISPLAY_ID, "2", raw, model, response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertNull(model.getAttribute("detail"));
        assertEquals(raw, model.getAttribute("submittedResolution"));
        assertEquals("The requested work is not available.", model.getAttribute("pageError"));
        verify(commands).completeWork(DISPLAY_ID, 2, raw);

        doThrow(new TechnicianQueryException(TechnicianQueryException.Code.UNAVAILABLE))
                .when(queries).detail(DISPLAY_ID);
        doThrow(new TechnicianCommandException(TechnicianCommandException.Code.UNAVAILABLE))
                .when(commands).completeWork(DISPLAY_ID, 2, raw);
        var unavailableModel = new ExtendedModelMap();
        var unavailableResponse = response();
        controller.completeWork(principal, DISPLAY_ID, "2", raw, unavailableModel, unavailableResponse);
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, unavailableResponse.getStatus());
        assertNull(unavailableModel.getAttribute("detail"));
        assertEquals(raw, unavailableModel.getAttribute("submittedResolution"));
    }

    @Test
    @DisplayName("REQ-SH-002: identity denial propagates without a detail reload")
    void identityDenialIsNotConvertedToBusinessError() {
        doThrow(new AccessDeniedException("denied")).when(commands).completeWork(DISPLAY_ID, 1, "Valid resolution summary");
        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class,
                () -> controller.completeWork(principal, DISPLAY_ID, "1", "Valid resolution summary",
                        new ExtendedModelMap(), response()));
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("REQ-TECH-007/008/SH-010: detail template presents completion and renders saved resolution as escaped text")
    void templateHasCompletionFormAndCompletedSummary() throws IOException {
        String html = resource("/templates/technician/request-detail.html");
        assertTrue(html.contains("/technician/requests/{id}/complete(id=${detail.request.displayId})"));
        assertTrue(html.contains("resolutionSummary"));
        assertTrue(html.contains("data-command-form"));
        assertTrue(html.contains("submittedResolution"));
        assertTrue(!html.matches("(?is).*maxlength\\s*=\\s*['\"]?2000.*"));
        assertTrue(!html.contains("th:utext"));
    }

    private static RequestDetail detail(RequestStatus status, long version) {
        QueueRow row = new QueueRow(DISPLAY_ID, "Synthetic request", "Block A", CATEGORY, "Electrical",
                status, ManagerPriority.MEDIUM, ReportedUrgency.NORMAL, Instant.parse("2026-10-17T08:00:00Z"));
        return new RequestDetail(row, "Synthetic description", Instant.parse("2026-10-17T08:00:00Z"),
                Instant.parse("2026-10-17T09:00:00Z"), version, null, null, List.of(), List.of());
    }

    private static MockHttpServletResponse response() { return new MockHttpServletResponse(); }

    private static void assertRedirect(RedirectView redirect) throws Exception {
        var response = new MockHttpServletResponse();
        redirect.render(Map.of(), new MockHttpServletRequest("POST", "/technician/requests"), response);
        assertEquals(HttpServletResponse.SC_SEE_OTHER, response.getStatus());
        assertEquals("/technician/requests/" + DISPLAY_ID, response.getRedirectedUrl());
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = TechnicianCompletionControllerTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing classpath resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
