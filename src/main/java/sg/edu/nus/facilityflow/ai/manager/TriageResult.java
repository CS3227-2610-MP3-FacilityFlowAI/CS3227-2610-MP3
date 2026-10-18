package sg.edu.nus.facilityflow.ai.manager;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Typed triage facts; ranking and assignment remain deterministic application work. */
public record TriageResult(
        String suggestedCategory,
        String suggestedPriority,
        List<String> clarificationQuestions,
        List<String> requiredSkillTags,
        List<String> optionalSkillTags,
        String rationale) {

    private static final Set<String> PRIORITIES = Set.of("Low", "Medium", "High", "Critical");
    private static final Pattern HTML_OR_MARKDOWN = Pattern.compile(
            "<[^>]*>|!\\[[^]]*\\]\\([^)]*\\)|\\[[^]]+\\]\\([^)]*\\)");
    private static final Pattern FORBIDDEN_IDENTIFIER = Pattern.compile(
            "(?i)(?:\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b|\\bFF-[0-9]{6}\\b|<\\/?request-data>)");

    public TriageResult {
        suggestedCategory = required(suggestedCategory, "suggestedCategory", 50);
        suggestedPriority = required(suggestedPriority, "suggestedPriority", 20);
        if (!PRIORITIES.contains(suggestedPriority)) {
            throw new IllegalArgumentException("suggestedPriority is invalid.");
        }
        clarificationQuestions = bounded(clarificationQuestions, "clarificationQuestions", 5, 200);
        requiredSkillTags = boundedUnique(requiredSkillTags, "requiredSkillTags", 8, 50);
        optionalSkillTags = boundedUnique(optionalSkillTags, "optionalSkillTags", 8, 50);
        if (new HashSet<>(requiredSkillTags).stream().anyMatch(optionalSkillTags::contains)) {
            throw new IllegalArgumentException("A skill tag cannot be both required and optional.");
        }
        rationale = required(rationale, "rationale", 500);
    }

    private static List<String> bounded(List<String> values, String name, int maxCount, int maxLength) {
        Objects.requireNonNull(values, name);
        if (values.size() > maxCount || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        return List.copyOf(values.stream().map(value -> required(value, name + " item", maxLength)).toList());
    }

    private static List<String> boundedUnique(List<String> values, String name, int maxCount, int maxLength) {
        List<String> normalized = bounded(values, name, maxCount, maxLength);
        if (normalized.size() != new HashSet<>(normalized).size()) {
            throw new IllegalArgumentException(name + " must contain unique values.");
        }
        return normalized;
    }

    private static String required(String value, String name, int max) {
        Objects.requireNonNull(value, name);
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > max
                || normalized.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                        && codePoint != '\n' && codePoint != '\t')) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        if (HTML_OR_MARKDOWN.matcher(normalized).find() || FORBIDDEN_IDENTIFIER.matcher(normalized).find()) {
            throw new IllegalArgumentException(name + " contains disallowed markup or identifiers.");
        }
        return normalized;
    }
}
