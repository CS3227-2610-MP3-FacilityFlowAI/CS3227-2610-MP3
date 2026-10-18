package sg.edu.nus.facilityflow.ai.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Validates deployment configuration without throwing on untrusted raw values. */
final class AiConfigurationValidator {
    private static final String OFFICIAL_HOST = "soclaas-api.comp.nus.edu.sg";

    private AiConfigurationValidator() {}

    static AiConfiguration validate(SocLlmProperties raw) {
        Boolean enabled = parseBoolean(raw.getEnabled());
        if (Boolean.FALSE.equals(enabled)) {
            return new AiConfiguration.Disabled();
        }
        if (enabled == null) {
            return new AiConfiguration.Unavailable(Set.of(AiConfiguration.Issue.ENABLED_INVALID));
        }

        EnumSet<AiConfiguration.Issue> issues = EnumSet.noneOf(AiConfiguration.Issue.class);
        URI baseUrl = validateBaseUrl(raw.getBaseUrl(), issues);
        String apiKey = requiredNonPlaceholder(
                raw.getApiKey(), AiConfiguration.Issue.API_KEY_MISSING_OR_PLACEHOLDER, issues);
        String model = requiredNonPlaceholder(
                raw.getModel(), AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER, issues);
        Integer timeoutSeconds = positiveAtMost(
                raw.getTimeoutSeconds(), 30, AiConfiguration.Issue.TIMEOUT_SECONDS_INVALID, issues);
        Integer maxOutputTokens = positiveAtMost(
                raw.getMaxOutputTokens(), 800, AiConfiguration.Issue.MAX_OUTPUT_TOKENS_INVALID, issues);
        Integer maxInFlight = positiveAtMost(
                raw.getMaxInFlight(), 5, AiConfiguration.Issue.MAX_IN_FLIGHT_INVALID, issues);
        Integer ratePerMinute = positiveAtMost(
                raw.getRatePerMinute(), 5, AiConfiguration.Issue.RATE_PER_MINUTE_INVALID, issues);
        Integer ratePerHour = positiveAtMost(
                raw.getRatePerHour(), 30, AiConfiguration.Issue.RATE_PER_HOUR_INVALID, issues);

        if (!issues.isEmpty()) {
            return new AiConfiguration.Unavailable(issues);
        }
        return new AiConfiguration.Available(
                baseUrl,
                apiKey,
                model,
                timeoutSeconds,
                maxOutputTokens,
                maxInFlight,
                ratePerMinute,
                ratePerHour);
    }

    private static Boolean parseBoolean(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static URI validateBaseUrl(
            String value, EnumSet<AiConfiguration.Issue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(AiConfiguration.Issue.BASE_URL_INVALID);
            return null;
        }
        try {
            URI candidate = new URI(value.trim());
            boolean valid = "https".equalsIgnoreCase(candidate.getScheme())
                    && OFFICIAL_HOST.equalsIgnoreCase(candidate.getHost())
                    && candidate.getRawUserInfo() == null
                    && (candidate.getPort() == -1 || candidate.getPort() == 443)
                    && candidate.getRawQuery() == null
                    && candidate.getRawFragment() == null
                    && ("/v1".equals(candidate.getRawPath()) || "/v1/".equals(candidate.getRawPath()));
            if (valid) {
                return AiConfiguration.CANONICAL_BASE_URL;
            }
        } catch (URISyntaxException ignored) {
            // Report only a stable issue code; raw URLs may contain credentials.
        }
        issues.add(AiConfiguration.Issue.BASE_URL_INVALID);
        return null;
    }

    private static String requiredNonPlaceholder(
            String value,
            AiConfiguration.Issue issue,
            EnumSet<AiConfiguration.Issue> issues) {
        if (value == null || hasInvalidScalarContent(value)) {
            issues.add(issue);
            return null;
        }
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty() || isPlaceholder(candidate)) {
            issues.add(issue);
            return null;
        }
        return candidate;
    }

    private static boolean hasInvalidScalarContent(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                int codePoint = Character.toCodePoint(current, value.charAt(++index));
                if (Character.isISOControl(codePoint)) {
                    return true;
                }
            } else if (Character.isLowSurrogate(current) || Character.isISOControl(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("placeholder")
                || normalized.equals("dummy")
                || normalized.equals("dummy-key")
                || normalized.equals("dummy_key")
                || normalized.equals("test-key")
                || normalized.equals("test_key")
                || normalized.equals("undefined")
                || normalized.equals("null")
                || normalized.equals("not-set")
                || normalized.equals("not_set")
                || normalized.equals("tbd")
                || normalized.equals("change-me")
                || normalized.equals("changeme")
                || normalized.equals("replace-me")
                || normalized.equals("replaceme")
                || normalized.equals("todo")
                || normalized.equals("example")
                || normalized.startsWith("your_")
                || normalized.startsWith("your-")
                || normalized.startsWith("your ")
                || normalized.endsWith("_here")
                || normalized.endsWith("-here")
                || (normalized.startsWith("<") && normalized.endsWith(">"))
                || (normalized.startsWith("${") && normalized.endsWith("}"));
    }

    private static Integer positiveAtMost(
            String value,
            int maximum,
            AiConfiguration.Issue issue,
            EnumSet<AiConfiguration.Issue> issues) {
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0 && parsed <= maximum) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Report a stable issue code without attacker-controlled input.
            }
        }
        issues.add(issue);
        return null;
    }
}
