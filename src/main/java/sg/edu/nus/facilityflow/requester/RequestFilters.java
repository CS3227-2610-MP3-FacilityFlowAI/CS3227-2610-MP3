package sg.edu.nus.facilityflow.requester;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

/** REQ-RQ-003 / REQ-SH-008/009: validated optional discovery fields and local-day boundaries. */
public record RequestFilters(String search, String status, String categoryId, String createdFrom, String createdTo) {
    public RequestFilters {
        search = trim(search);
        status = trim(status);
        categoryId = trim(categoryId).toLowerCase(Locale.ROOT);
        createdFrom = trim(createdFrom);
        createdTo = trim(createdTo);
    }

    public static RequestFilters empty() {
        return new RequestFilters("", "", "", "", "");
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        if (search.indexOf('\0') >= 0) errors.put("search", "Search contains an unsupported character. Remove it and try again.");
        if (!status.isEmpty() && !RequesterRequests.STATUSES.contains(status)) errors.put("status", "Choose a listed status.");
        if (!categoryId.isEmpty() && !RequestDraft.validUuid(categoryId)) errors.put("categoryId", "Choose a listed category.");
        LocalDate from = date(createdFrom, "createdFrom", errors);
        LocalDate to = date(createdTo, "createdTo", errors);
        if (from != null && to != null && to.isBefore(from)) {
            errors.put("createdTo", "End date must be on or after the start date.");
        }
        return errors;
    }

    public OffsetDateTime fromUtc() {
        return createdFrom.isEmpty() ? null : LocalDate.parse(createdFrom).atStartOfDay(RequesterRequests.DISPLAY_ZONE)
                .toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    public OffsetDateTime beforeUtc() {
        return createdTo.isEmpty() ? null : LocalDate.parse(createdTo).plusDays(1).atStartOfDay(RequesterRequests.DISPLAY_ZONE)
                .toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static LocalDate date(String value, String field, Map<String, String> errors) {
        if (value.isEmpty()) return null;
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && !value.startsWith("0000")) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException exception) {
                // Fall through to the same safe correction for malformed and impossible dates.
            }
        }
        errors.put(field, "Enter a valid date in YYYY-MM-DD format (years 0001–9999).");
        return null;
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }
}
