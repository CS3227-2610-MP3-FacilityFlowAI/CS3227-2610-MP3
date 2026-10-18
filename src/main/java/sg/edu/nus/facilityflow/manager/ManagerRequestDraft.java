package sg.edu.nus.facilityflow.manager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** REQ-MGR-003: bounded fields for a Manager-recorded Request. */
public record ManagerRequestDraft(
        String requesterId,
        String title,
        String description,
        String location,
        String categoryId,
        String reportedUrgency) {

    private static final Set<String> URGENCIES = Set.of("LOW", "NORMAL", "HIGH", "EMERGENCY");

    public ManagerRequestDraft {
        requesterId = normalize(requesterId);
        title = normalize(title);
        description = normalize(description);
        location = normalize(location);
        categoryId = normalize(categoryId);
        reportedUrgency = normalize(reportedUrgency).toUpperCase(java.util.Locale.ROOT);
    }

    public static ManagerRequestDraft empty() {
        return new ManagerRequestDraft("", "", "", "", "", "NORMAL");
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        uuid(errors, "requesterId", requesterId, "Choose an active Requester.");
        text(errors, "title", title, "Title", 5, 100);
        text(errors, "description", description, "Description", 10, 2000);
        text(errors, "location", location, "Location", 2, 120);
        uuid(errors, "categoryId", categoryId, "Choose an active category.");
        if (!URGENCIES.contains(reportedUrgency)) {
            errors.put("reportedUrgency", "Choose a listed urgency.");
        }
        return errors;
    }

    private static void text(Map<String, String> errors, String field, String value,
            String label, int minimum, int maximum) {
        int length = value.codePointCount(0, value.length());
        if (length < minimum || length > maximum) {
            errors.put(field, label + " must contain " + minimum + "–" + maximum + " characters.");
        }
    }

    private static void uuid(Map<String, String> errors, String field, String value, String message) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            errors.put(field, message);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
