package sg.edu.nus.facilityflow.ai.requester;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Caller-provided, identifier-free context for a Requester draft suggestion. */
public record SmartReportContext(
        String roughTitle,
        String roughDescription,
        String roughLocation,
        String selectedCategory,
        String selectedUrgency,
        List<String> activeCategoryLabels) {

    private static final Set<String> URGENCIES = Set.of("Low", "Normal", "High", "Emergency");

    public SmartReportContext {
        roughTitle = optional(roughTitle, "roughTitle", 100);
        roughDescription = optional(roughDescription, "roughDescription", 2_000);
        roughLocation = optional(roughLocation, "roughLocation", 120);
        selectedCategory = optional(selectedCategory, "selectedCategory", 50);
        selectedUrgency = optional(selectedUrgency, "selectedUrgency", 20);
        if (selectedUrgency != null && !URGENCIES.contains(selectedUrgency)) {
            throw new IllegalArgumentException("selectedUrgency is invalid.");
        }
        activeCategoryLabels = boundedLabels(activeCategoryLabels);
        if (roughTitle == null && roughDescription == null && roughLocation == null) {
            throw new IllegalArgumentException("At least one rough report field is required.");
        }
    }

    private static List<String> boundedLabels(List<String> labels) {
        Objects.requireNonNull(labels, "activeCategoryLabels");
        return List.copyOf(labels.stream().map(label -> required(label, "category label", 50)).toList());
    }

    private static String optional(String value, String name, int max) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > max || hasDisallowedControl(normalized)) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        return normalized;
    }

    private static String required(String value, String name, int max) {
        String normalized = optional(value, name, max);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC)
                .trim();
    }

    private static boolean hasDisallowedControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\t');
    }
}
