package sg.edu.nus.facilityflow.requester;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestFiltersTest {
    @Test
    void reqRq003OptionalFiltersTrimAndRetainLiteralSearch() {
        String category = UUID.randomUUID().toString();
        var filters = new RequestFilters("\u2003WaTer 100%_pipe\\\u2003", " OPEN ", category.toUpperCase(java.util.Locale.ROOT), "", "");
        assertEquals("WaTer 100%_pipe\\", filters.search());
        assertEquals("OPEN", filters.status());
        assertEquals(category, filters.categoryId());
        assertTrue(filters.errors().isEmpty());
        assertEquals(RequestFilters.empty(), new RequestFilters(null, null, null, null, null));
    }

    @Test
    void reqSh009InclusiveDatesEndBeforeNextSingaporeDay() {
        var filters = new RequestFilters("", "", "", "2026-10-16", "2026-10-16");
        assertTrue(filters.errors().isEmpty());
        assertEquals(OffsetDateTime.parse("2026-10-15T16:00:00Z"), filters.fromUtc());
        assertEquals(OffsetDateTime.parse("2026-10-16T16:00:00Z"), filters.beforeUtc());
        assertNull(RequestFilters.empty().fromUtc());
        assertNull(RequestFilters.empty().beforeUtc());
        assertTrue(new RequestFilters("", "", "", "2024-02-29", "2024-02-29").errors().isEmpty());
        assertTrue(new RequestFilters("", "", "", "0001-01-01", "9999-12-31").errors().isEmpty());
    }

    @Test
    void reqRq003InvalidDatesEnumsAndIdentifiersHaveSpecificErrors() {
        var errors = new RequestFilters("bad\0search", "ADMIN", "bad-id", "2026-02-29", "2026-13-01").errors();
        assertEquals(5, errors.size());
        assertTrue(new RequestFilters("", "", "", "2026-10-17", "2026-10-16").errors().containsKey("createdTo"));
        for (String date : new String[]{"2026-1-01", "+999999999-12-31", "0000-01-01", "not-a-date"}) {
            assertTrue(new RequestFilters("", "", "", date, "").errors().containsKey("createdFrom"));
        }
    }
}
