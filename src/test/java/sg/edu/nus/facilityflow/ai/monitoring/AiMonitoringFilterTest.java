package sg.edu.nus.facilityflow.ai.monitoring;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiMonitoringFilterTest {
    @Test @DisplayName("REQ-SH-009: local-date bounds use an exclusive next day, independent of host zone")
    void mapsDatesToUtc() {
        var filter = new AiMonitoringFilter(" 2026-10-19 ", "2026-10-19", "TRIAGE");
        assertTrue(filter.errors().isEmpty());
        assertEquals(Instant.parse("2026-10-18T16:00:00Z"), filter.start().toInstant());
        assertEquals(Instant.parse("2026-10-19T16:00:00Z"), filter.before().toInstant());
    }

    @Test @DisplayName("REQ-AI-008/009: malformed dates, reversed periods and unknown assistants are rejected")
    void validatesFilters() {
        for (String date : new String[]{"0000-01-01", "2026-02-30", "2026-1-01", "10000-01-01", "<script>"}) {
            assertTrue(new AiMonitoringFilter(date, "", "").errors().containsKey("from"));
        }
        assertTrue(new AiMonitoringFilter("2026-10-20", "2026-10-19", "").errors().containsKey("to"));
        assertTrue(new AiMonitoringFilter("", "", "<script>").errors().containsKey("feature"));
    }

    @Test @DisplayName("REQ-AI-009: portal counts preserve independently reported totals and reject invalid numbers")
    void validatesPortalSnapshots() {
        assertTrue(new AiUsageComparison("0", "0", "0").errors().isEmpty());
        for (String count : new String[]{"-1", "1.5", "", "1".repeat(20), "1e3", "secret-canary"}) {
            assertFalse(new AiUsageComparison(count, "0", "0").errors().isEmpty());
        }
        assertTrue(new AiUsageComparison("1", "2", "4").errors().isEmpty());
    }
}
