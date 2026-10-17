package sg.edu.nus.facilityflow.manager;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable, read-only Technician facts used by Manager candidate ranking. */
public record TechnicianCandidate(
        UUID accountId,
        String username,
        boolean active,
        Set<String> skillTags,
        int workload) {

    public TechnicianCandidate {
        accountId = Objects.requireNonNull(accountId, "accountId");
        username = requiredUsername(username);
        if (workload < 0) {
            throw new IllegalArgumentException("workload cannot be negative.");
        }
        Objects.requireNonNull(skillTags, "skillTags");
        skillTags = Set.copyOf(skillTags.stream()
                .map(tag -> requiredTag(tag))
                .toList());
    }

    private static String requiredUsername(String value) {
        Objects.requireNonNull(value, "username");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 100
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("username is invalid.");
        }
        return normalized;
    }

    private static String requiredTag(String value) {
        Objects.requireNonNull(value, "skillTag");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 50
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("skillTag is invalid.");
        }
        return normalized;
    }
}
