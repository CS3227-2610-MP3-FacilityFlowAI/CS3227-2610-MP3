package sg.edu.nus.facilityflow.manager;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** REQ-MGR-011: bounded, read-only audit query inputs. */
public record ManagerAuditFilters(
        String request,
        String actor,
        String action,
        String occurredFrom,
        String occurredTo) {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    public ManagerAuditFilters {
        request = normalize(request);
        actor = normalize(actor);
        action = normalize(action);
        occurredFrom = normalize(occurredFrom);
        occurredTo = normalize(occurredTo);
    }

    public static ManagerAuditFilters empty() {
        return new ManagerAuditFilters("", "", "", "", "");
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        bounded(errors, "request", request, 20);
        bounded(errors, "actor", actor, 80);
        bounded(errors, "action", action, 100);
        LocalDate from = date(errors, "occurredFrom", occurredFrom);
        LocalDate to = date(errors, "occurredTo", occurredTo);
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("occurredTo", "The end date must not be before the start date.");
        }
        return errors;
    }

    public Instant fromInstant() {
        return occurredFrom.isEmpty() ? null : LocalDate.parse(occurredFrom).atStartOfDay(SINGAPORE).toInstant();
    }

    public Instant toExclusiveInstant() {
        return occurredTo.isEmpty() ? null : LocalDate.parse(occurredTo).plusDays(1).atStartOfDay(SINGAPORE).toInstant();
    }

    private static void bounded(Map<String, String> errors, String field, String value, int maximum) {
        if (value.codePointCount(0, value.length()) > maximum) {
            errors.put(field, "Use at most " + maximum + " characters.");
        }
    }

    private static LocalDate date(Map<String, String> errors, String field, String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            errors.put(field, "Enter a valid date.");
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
