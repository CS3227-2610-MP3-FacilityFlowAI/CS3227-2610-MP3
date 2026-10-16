package sg.edu.nus.facilityflow.requestcore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** REQ-RQ-002 / PRODUCT-SPEC fields: the only report fields accepted from clients. */
public record RequestDraft(String title, String description, String location,
        String categoryId, String reportedUrgency) {
    public RequestDraft {
        title = trim(title);
        description = trim(description);
        location = trim(location);
        categoryId = trim(categoryId);
        reportedUrgency = trim(reportedUrgency);
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        length(errors, "title", "Title", title, 5, 100);
        length(errors, "description", "Description", description, 10, 2000);
        length(errors, "location", "Location", location, 2, 120);
        if (!validUuid(categoryId)) errors.put("categoryId", "Choose an active category.");
        if (!Set.of("LOW", "NORMAL", "HIGH", "EMERGENCY").contains(reportedUrgency)) {
            errors.put("reportedUrgency", "Choose Low, Normal, High or Emergency.");
        }
        return errors;
    }

    public static boolean validUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static void length(Map<String, String> errors, String field, String label,
            String value, int minimum, int maximum) {
        int length = value.codePointCount(0, value.length());
        if (value.indexOf('\0') >= 0) {
            errors.put(field, label + " contains an unsupported character. Remove it and try again.");
        } else if (value.isBlank() || length < minimum || length > maximum) {
            errors.put(field, label + " must contain " + minimum + "–" + maximum + " characters.");
        }
    }
}
