package sg.edu.nus.facilityflow.ai.requester;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated advisory fields; callers still perform ordinary Request validation. */
public record SmartReportResult(
        String draftTitle,
        String draftDescription,
        String draftLocation,
        String suggestedCategory,
        String suggestedUrgency,
        List<String> missingInformationQuestions,
        String rationale) {

    private static final Set<String> URGENCIES = Set.of("Low", "Normal", "High", "Emergency");
    private static final Pattern HTML_OR_MARKDOWN = Pattern.compile(
            "<[^>]*>|!\\[[^]]*\\]\\([^)]*\\)|\\[[^]]+\\]\\([^)]*\\)");
    private static final Pattern FORBIDDEN_IDENTIFIER = Pattern.compile(
            "(?i)(?:\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b|\\bFF-[0-9]{6}\\b|<\\/?request-data>)");

    public SmartReportResult {
        draftTitle = optional(draftTitle, "draftTitle", 5, 100);
        draftDescription = optional(draftDescription, "draftDescription", 10, 2_000);
        draftLocation = optional(draftLocation, "draftLocation", 2, 120);
        suggestedCategory = optional(suggestedCategory, "suggestedCategory", 1, 50);
        suggestedUrgency = optional(suggestedUrgency, "suggestedUrgency", 1, 20);
        if (suggestedUrgency != null && !URGENCIES.contains(suggestedUrgency)) {
            throw new IllegalArgumentException("suggestedUrgency is invalid.");
        }
        missingInformationQuestions = boundedQuestions(missingInformationQuestions);
        rationale = required(rationale, "rationale", 1, 500);
        if (draftTitle == null && draftDescription == null && draftLocation == null
                && suggestedCategory == null && suggestedUrgency == null
                && missingInformationQuestions.isEmpty()) {
            throw new IllegalArgumentException("Smart Report result contains no suggestion.");
        }
    }

    private static List<String> boundedQuestions(List<String> values) {
        Objects.requireNonNull(values, "missingInformationQuestions");
        if (values.size() > 5 || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Too many missing-information questions.");
        }
        return List.copyOf(values.stream().map(value -> required(value, "question", 1, 200)).toList());
    }

    private static String optional(String value, String name, int min, int max) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) < min
                || normalized.codePointCount(0, normalized.length()) > max || hasDisallowedControl(normalized)) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        if (HTML_OR_MARKDOWN.matcher(normalized).find() || FORBIDDEN_IDENTIFIER.matcher(normalized).find()) {
            throw new IllegalArgumentException(name + " contains disallowed markup or identifiers.");
        }
        return normalized;
    }

    private static String required(String value, String name, int min, int max) {
        String normalized = optional(value, name, min, max);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).trim();
    }

    private static boolean hasDisallowedControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\t');
    }
}
