package sg.edu.nus.facilityflow.manager;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** REQ-MGR-001: immutable Manager queue filters with strict validation. */
public record ManagerRequestFilters(
        String search,
        String status,
        String categoryId,
        String priority,
        String technicianId,
        String createdFrom,
        String createdTo) {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private static final Set<String> STATUSES = Set.of(
            "OPEN", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public ManagerRequestFilters {
        search = normalize(search);
        status = normalize(status).toUpperCase(java.util.Locale.ROOT);
        categoryId = normalize(categoryId);
        priority = normalize(priority).toUpperCase(java.util.Locale.ROOT);
        technicianId = normalize(technicianId);
        createdFrom = normalize(createdFrom);
        createdTo = normalize(createdTo);
    }

    public static ManagerRequestFilters empty() {
        return new ManagerRequestFilters("", "", "", "", "", "", "");
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        if (search.codePointCount(0, search.length()) > 120) {
            errors.put("search", "Search must contain at most 120 characters.");
        }
        allowed(errors, "status", status, STATUSES, "Choose a listed status.");
        allowed(errors, "priority", priority, PRIORITIES, "Choose a listed priority.");
        optionalUuid(errors, "categoryId", categoryId, "Choose a listed category.");
        optionalUuid(errors, "technicianId", technicianId, "Choose a listed Technician.");
        LocalDate from = date(errors, "createdFrom", createdFrom);
        LocalDate to = date(errors, "createdTo", createdTo);
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("createdTo", "The end date must not be before the start date.");
        }
        return errors;
    }

    public Instant fromInstant() {
        return createdFrom.isEmpty() ? null : LocalDate.parse(createdFrom).atStartOfDay(SINGAPORE).toInstant();
    }

    public Instant toExclusiveInstant() {
        return createdTo.isEmpty() ? null : LocalDate.parse(createdTo).plusDays(1).atStartOfDay(SINGAPORE).toInstant();
    }

    private static void allowed(Map<String, String> errors, String field, String value,
            Set<String> allowed, String message) {
        if (!value.isEmpty() && !allowed.contains(value)) {
            errors.put(field, message);
        }
    }

    private static void optionalUuid(Map<String, String> errors, String field, String value, String message) {
        if (value.isEmpty()) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            errors.put(field, message);
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
