package sg.edu.nus.facilityflow.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagerInputTest {
    @Test
    @DisplayName("REQ-MGR-003 / REQ-SH-010: on-behalf draft trims outer whitespace and reports field errors")
    void validatesOnBehalfDraftWithoutDiscardingValidFields() {
        var requester = UUID.randomUUID().toString();
        var category = UUID.randomUUID().toString();
        var valid = new ManagerRequestDraft(requester, "  Leaking pipe  ",
                "  Water is leaking below the sink.  ", "  Block A  ", category, "normal");
        assertTrue(valid.errors().isEmpty());
        assertEquals("Leaking pipe", valid.title());
        assertEquals("Water is leaking below the sink.", valid.description());
        assertEquals("Block A", valid.location());
        assertEquals("NORMAL", valid.reportedUrgency());

        var invalid = new ManagerRequestDraft("not-an-id", "bad", "short", "", "", "critical");
        assertEquals(6, invalid.errors().size());
        assertEquals("bad", invalid.title());
    }

    @Test
    @DisplayName("REQ-MGR-001 / REQ-SH-009: queue filters use inclusive Singapore dates and reject invalid selections")
    void validatesQueueFiltersAndSingaporeDateBounds() {
        var filters = new ManagerRequestFilters(" pump ", "open", UUID.randomUUID().toString(),
                "high", UUID.randomUUID().toString(), "2026-10-16", "2026-10-17");
        assertTrue(filters.errors().isEmpty());
        assertEquals("pump", filters.search());
        assertEquals("OPEN", filters.status());
        assertEquals(LocalDate.parse("2026-10-16").atStartOfDay(ZoneId.of("Asia/Singapore")).toInstant(),
                filters.fromInstant());
        assertEquals(LocalDate.parse("2026-10-18").atStartOfDay(ZoneId.of("Asia/Singapore")).toInstant(),
                filters.toExclusiveInstant());

        var invalid = new ManagerRequestFilters("", "UNKNOWN", "bad", "urgent", "bad",
                "2026-10-18", "2026-10-17");
        assertFalse(invalid.errors().isEmpty());
        assertTrue(invalid.errors().containsKey("createdTo"));
    }

    @Test
    @DisplayName("REQ-MGR-011: audit filters reject reversed dates and overlong action input")
    void validatesAuditFilters() {
        var filters = new ManagerAuditFilters("FF-000001", "manager1", "x".repeat(101),
                "2026-10-18", "2026-10-17");
        assertTrue(filters.errors().containsKey("action"));
        assertTrue(filters.errors().containsKey("occurredTo"));
    }

    @Test
    @DisplayName("REQ-SH-009: Manager rows format timestamps explicitly in Asia/Singapore")
    void formatsManagerTimesInSingaporeZone() {
        Instant time = Instant.parse("2026-10-17T00:30:00Z");
        var request = new ManagerViews.RequestSummary("FF-000001", "Title", "Block A", "Electrical",
                "NORMAL", null, "OPEN", "Requester", null, time, time);
        var audit = new ManagerViews.AuditRow(time, "manager", "REQUEST_CREATED", "REQUEST", "FF-000001");
        assertTrue(request.createdTime().contains("Asia/Singapore"));
        assertTrue(request.createdTime().startsWith("17 Oct 2026, 8:30"));
        assertTrue(audit.occurredTime().contains("Asia/Singapore"));
    }
}
