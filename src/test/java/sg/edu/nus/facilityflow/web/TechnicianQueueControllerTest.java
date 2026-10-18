package sg.edu.nus.facilityflow.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import sg.edu.nus.facilityflow.identity.Account;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.technician.TechnicianQueryException;
import sg.edu.nus.facilityflow.technician.TechnicianQueryService;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

class TechnicianQueueControllerTest {
    private static final UUID CATEGORY = UUID.fromString("8e400000-0000-0000-0000-000000000001");
    private static final String UNAVAILABLE = "Assigned work is temporarily unavailable. Please try again.";

    private TechnicianQueryService service;
    private TechnicianQueueController controller;
    private CurrentAccountPrincipal principal;

    @BeforeEach
    void setUp() {
        service = mock(TechnicianQueryService.class);
        controller = new TechnicianQueueController(service);
        principal = CurrentAccountPrincipal.from(new Account(UUID.randomUUID(), "tech.user", "Tech User",
                "synthetic-test-hash", AccountRole.TECHNICIAN, true, 2,
                Instant.parse("2026-10-17T00:00:00Z"), Instant.parse("2026-10-17T00:00:00Z")));
    }

    @Test
    @DisplayName("REQ-TECH-001/002/SH-009: valid filters reach the service and carry the local display zone")
    void queueParsesCanonicalFiltersAndPreservesRawSearchInput() {
        QueueView queue = new QueueView(new QueueFilter("<script>", RequestStatus.ASSIGNED, CATEGORY,
                ManagerPriority.CRITICAL), new DashboardCounts(1, 0, 0), List.of(), 1, List.of());
        when(service.queue(any())).thenReturn(queue);
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();

        String view = controller.queue(principal, "  <script>  ", "ASSIGNED", CATEGORY.toString(),
                "CRITICAL", model, response);

        assertEquals("technician/queue", view);
        assertEquals(queue, model.getAttribute("queue"));
        assertTrue(model.getAttribute("submittedFilters").toString().contains("q=  <script>  "));
        assertEquals(ZoneId.of("Asia/Singapore"), model.getAttribute("displayZone"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        org.mockito.ArgumentCaptor<QueueFilter> filter = org.mockito.ArgumentCaptor.forClass(QueueFilter.class);
        verify(service).queue(filter.capture());
        assertEquals(new QueueFilter("<script>", RequestStatus.ASSIGNED, CATEGORY, ManagerPriority.CRITICAL),
                filter.getValue());
    }

    @Test
    @DisplayName("REQ-TECH-002: invalid enum and noncanonical UUID filters return 400 without querying broader data")
    void invalidFiltersArePreservedAndNeverFallBackToUnfilteredResults() {
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();
        String noncanonical = CATEGORY.toString().toUpperCase(java.util.Locale.ROOT);

        String view = controller.queue(principal, "restricted", "NOT_A_STATUS", noncanonical,
                "URGENT", model, response);

        assertEquals("technician/queue", view);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertFalse(model.containsAttribute("queue"));
        assertNotNull(model.getAttribute("submittedFilters"));
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) model.getAttribute("fieldErrors");
        assertEquals("Choose a valid status.", errors.get("status"));
        assertEquals("Choose a valid category.", errors.get("category"));
        assertEquals("Choose a valid Manager priority.", errors.get("priority"));
        verify(service, never()).queue(any());
    }

    @Test
    @DisplayName("REQ-TECH-002: malformed category input is a field error and produces no queue data")
    void malformedCategoryDoesNotBroadenTheRead() {
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();

        controller.queue(principal, "pump", null, "not-a-uuid", null, model, response);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertFalse(model.containsAttribute("queue"));
        assertEquals(List.of("Choose a valid category."), model.getAttribute("queryErrors"));
        verify(service, never()).queue(any());
    }

    @Test
    @DisplayName("REQ-TECH-001: the existing workspace entry requests the default unfiltered queue")
    void workspaceUsesTheDefaultFilterAndCombinedQueueView() {
        when(service.queue(any())).thenReturn(new QueueView(emptyFilter(), new DashboardCounts(0, 0, 0),
                List.of(), 0, List.of()));
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();

        String view = controller.workspace(principal, model, response);

        assertEquals("technician/queue", view);
        assertEquals("Technician workspace", model.getAttribute("title"));
        verify(service).queue(emptyFilter());
    }

    @Test
    @DisplayName("REQ-TECH-001/009: operational queue failures render one generic retryable 503")
    void unavailableQueueDoesNotRenderCountsOrProtectedRows() {
        when(service.queue(any())).thenThrow(new TechnicianQueryException(TechnicianQueryException.Code.UNAVAILABLE));
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();

        controller.queue(principal, "private needle", null, null, null, model, response);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals(UNAVAILABLE, model.getAttribute("pageError"));
        assertFalse(model.containsAttribute("queue"));
        assertTrue(model.getAttribute("submittedFilters").toString().contains("private needle"));
        assertFalse(model.getAttribute("pageError").toString().contains("private needle"));
    }

    @Test
    @DisplayName("REQ-TECH-009: malformed, unknown and unassigned details return the same safe 404")
    void unavailableDetailsHaveTheSameSafePage() {
        when(service.detail(any())).thenThrow(new TechnicianQueryException(TechnicianQueryException.Code.NOT_AVAILABLE));
        var malformedModel = new ExtendedModelMap();
        var unknownModel = new ExtendedModelMap();
        var malformedResponse = new MockHttpServletResponse();
        var unknownResponse = new MockHttpServletResponse();

        String malformedView = controller.detail(principal, "bad-id", malformedModel, malformedResponse);
        String unknownView = controller.detail(principal, "FF-999999", unknownModel, unknownResponse);

        assertEquals("technician/request-detail", malformedView);
        assertEquals(malformedView, unknownView);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, malformedResponse.getStatus());
        assertEquals(HttpServletResponse.SC_NOT_FOUND, unknownResponse.getStatus());
        assertEquals("The requested work is not available.", malformedModel.getAttribute("pageError"));
        assertEquals(malformedModel.getAttribute("pageError"), unknownModel.getAttribute("pageError"));
        assertFalse(malformedModel.containsAttribute("detail"));
        assertFalse(unknownModel.containsAttribute("detail"));
    }

    @Test
    @DisplayName("REQ-TECH-009: detail read failures use a generic retryable 503")
    void unavailableDetailDoesNotLeakStorageFailure() {
        when(service.detail("FF-000101")).thenThrow(new TechnicianQueryException(TechnicianQueryException.Code.UNAVAILABLE));
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();

        controller.detail(principal, "FF-000101", model, response);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals(UNAVAILABLE, model.getAttribute("pageError"));
        assertFalse(model.containsAttribute("detail"));
    }

    @Test
    @DisplayName("REQ-SH-009/TECH-009: detail exposes one local zone for UTC instants and empty-history inputs")
    void detailCarriesZoneAndCompleteHistoryModel() {
        var row = new QueueRow("FF-000101", "Light flickers", "Room 2", CATEGORY, "Electrical",
                RequestStatus.CLOSED, null, ReportedUrgency.NORMAL, null);
        var detail = new RequestDetail(row, "First line\nSecond <line>",
                Instant.parse("2026-10-17T16:30:00Z"), Instant.parse("2026-10-17T17:00:00Z"),
                3, null, null, List.of(), List.of());
        when(service.detail("FF-000101")).thenReturn(detail);
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();

        String view = controller.detail(principal, "FF-000101", model, response);

        assertEquals("technician/request-detail", view);
        assertEquals(detail, model.getAttribute("detail"));
        assertEquals(ZoneId.of("Asia/Singapore"), model.getAttribute("displayZone"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("First line\nSecond <line>", ((RequestDetail) model.getAttribute("detail")).description());
    }

    @Test
    @DisplayName("REQ-SH-009/NFR-SEC-001: Technician templates escape text and load styles without inline CSS")
    void technicianTemplatesKeepEscapedOutputAndExternalStyles() throws IOException {
        String queue = template("queue.html");
        String detail = template("request-detail.html");
        String rows = template("fragments/request-rows.html");
        String history = template("fragments/history.html");

        for (String html : List.of(queue, detail)) {
            assertTrue(html.contains("th:href=\"@{/css/technician.css}\""));
        }
        for (String html : List.of(queue, detail, rows, history)) {
            assertFalse(html.matches("(?is).*\\sstyle\\s*=.*"));
            assertFalse(html.contains("th:utext"));
        }
        assertTrue(queue.contains("th:if=\"${pageError}\""));
        assertTrue(queue.contains("th:if=\"${queue}\""));
        assertTrue(detail.contains("th:if=\"${pageError}\""));
        assertTrue(detail.contains("th:if=\"${detail}\""));
        assertTrue(detail.contains("detail.description"));
        assertTrue(detail.contains(".atZone(displayZone)"));
        assertTrue(history.contains("update.note"));
        assertTrue(history.contains("log.note"));
    }

    private static String template(String name) throws IOException {
        String path = "/templates/technician/" + name;
        try (InputStream input = TechnicianQueueControllerTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing template resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static QueueFilter emptyFilter() {
        return new QueueFilter("", null, null, null);
    }
}
