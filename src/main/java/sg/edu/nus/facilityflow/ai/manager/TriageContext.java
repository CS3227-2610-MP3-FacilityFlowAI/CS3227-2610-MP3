package sg.edu.nus.facilityflow.ai.manager;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;

/** Identifier-free, catalogue-bounded context for a Manager triage suggestion. */
public record TriageContext(
        String title,
        String description,
        String location,
        String currentCategory,
        String reportedUrgency,
        String managerPriority,
        String status,
        List<String> requesterUpdates,
        List<String> activeCategoryLabels,
        List<String> activeSkillLabels) {

    public TriageContext {
        title = required(title, "title", 100);
        description = required(description, "description", 2_000);
        location = required(location, "location", 120);
        currentCategory = required(currentCategory, "currentCategory", 50);
        reportedUrgency = required(reportedUrgency, "reportedUrgency", 20);
        managerPriority = optional(managerPriority, "managerPriority", 20);
        status = required(status, "status", 20);
        if ("CANCELLED".equals(status) || "CLOSED".equals(status)) {
            throw new IllegalArgumentException("Triage requires a non-terminal Request.");
        }
        requesterUpdates = boundedList(requesterUpdates, "requesterUpdates", 10, 2_000);
        activeCategoryLabels = boundedList(activeCategoryLabels, "activeCategoryLabels", 1_000, 50);
        activeSkillLabels = boundedList(activeSkillLabels, "activeSkillLabels", 1_000, 50);
    }

    private static List<String> boundedList(List<String> values, String name, int maxCount, int maxLength) {
        Objects.requireNonNull(values, name);
        if (values.size() > maxCount || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        return List.copyOf(values.stream().map(value -> required(value, name + " item", maxLength)).toList());
    }

    private static String required(String value, String name, int max) {
        Objects.requireNonNull(value, name);
        String normalized = Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'),
                Normalizer.Form.NFC).trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > max
                || normalized.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                        && codePoint != '\n' && codePoint != '\t')) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        return normalized;
    }

    private static String optional(String value, String name, int max) {
        if (value == null) {
            return null;
        }
        return required(value, name, max);
    }
}
