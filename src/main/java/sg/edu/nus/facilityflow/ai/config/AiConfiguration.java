package sg.edu.nus.facilityflow.ai.config;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Startup-safe result of validating the optional SoCLaaS capability.
 *
 * <p>Callers must handle disabled and unavailable states without attempting to
 * load protected AI context (REQ-AI-004, AI-SEC-019).
 */
public sealed interface AiConfiguration
        permits AiConfiguration.Disabled, AiConfiguration.Unavailable, AiConfiguration.Available {

    URI CANONICAL_BASE_URL = URI.create("https://soclaas-api.comp.nus.edu.sg/v1");

    /** AI was deliberately not enabled. */
    record Disabled() implements AiConfiguration {}

    /** AI was enabled, but one or more settings failed closed validation. */
    record Unavailable(Set<Issue> issues) implements AiConfiguration {
        public Unavailable {
            issues = Set.copyOf(Objects.requireNonNull(issues, "issues"));
            if (issues.isEmpty()) {
                throw new IllegalArgumentException("At least one configuration issue is required.");
            }
        }
    }

    /** Safe, non-secret configuration issue codes suitable for diagnostics. */
    enum Issue {
        ENABLED_INVALID,
        BASE_URL_INVALID,
        API_KEY_MISSING_OR_PLACEHOLDER,
        MODEL_MISSING_OR_PLACEHOLDER,
        TIMEOUT_SECONDS_INVALID,
        MAX_OUTPUT_TOKENS_INVALID,
        MAX_IN_FLIGHT_INVALID,
        RATE_PER_MINUTE_INVALID,
        RATE_PER_HOUR_INVALID
    }

    /**
     * Fully validated settings. The key is accessible only for a later
     * server-side provider adapter and is always redacted from diagnostics.
     */
    final class Available implements AiConfiguration {
        private final URI baseUrl;
        private final String apiKey;
        private final String model;
        private final int timeoutSeconds;
        private final int maxOutputTokens;
        private final int maxInFlight;
        private final int ratePerMinute;
        private final int ratePerHour;

        Available(
                URI baseUrl,
                String apiKey,
                String model,
                int timeoutSeconds,
                int maxOutputTokens,
                int maxInFlight,
                int ratePerMinute,
                int ratePerHour) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.model = Objects.requireNonNull(model, "model");
            this.timeoutSeconds = timeoutSeconds;
            this.maxOutputTokens = maxOutputTokens;
            this.maxInFlight = maxInFlight;
            this.ratePerMinute = ratePerMinute;
            this.ratePerHour = ratePerHour;
        }

        public URI baseUrl() {
            return baseUrl;
        }

        String apiKey() {
            return apiKey;
        }

        public String model() {
            return model;
        }

        public int timeoutSeconds() {
            return timeoutSeconds;
        }

        public int maxOutputTokens() {
            return maxOutputTokens;
        }

        public int maxInFlight() {
            return maxInFlight;
        }

        public int ratePerMinute() {
            return ratePerMinute;
        }

        public int ratePerHour() {
            return ratePerHour;
        }

        @Override
        public String toString() {
            return "AiConfiguration.Available[baseUrl=" + baseUrl
                    + ", apiKey=[REDACTED]"
                    + ", model=[PRESENT]"
                    + ", timeoutSeconds=" + timeoutSeconds
                    + ", maxOutputTokens=" + maxOutputTokens
                    + ", maxInFlight=" + maxInFlight
                    + ", ratePerMinute=" + ratePerMinute
                    + ", ratePerHour=" + ratePerHour + "]";
        }
    }
}
