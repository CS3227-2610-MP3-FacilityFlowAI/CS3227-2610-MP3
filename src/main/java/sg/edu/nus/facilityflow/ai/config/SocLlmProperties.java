package sg.edu.nus.facilityflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Raw server-side AI configuration.
 *
 * <p>Every field deliberately remains a string. Parsing deployment input during
 * Spring binding would turn a malformed value into an application-startup
 * failure, while REQ-AI-004 requires only the optional AI capability to become
 * unavailable. Validation is performed by {@link AiConfigurationValidator}.
 */
@ConfigurationProperties("facilityflow.ai")
final class SocLlmProperties {
    private String enabled = "false";
    private String baseUrl = AiConfiguration.CANONICAL_BASE_URL.toString();
    private String apiKey = "";
    private String model = "";
    private String timeoutSeconds = "30";
    private String maxOutputTokens = "800";
    private String maxInFlight = "5";
    private String ratePerMinute = "5";
    private String ratePerHour = "30";

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(String timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(String maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(String maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    public String getRatePerMinute() {
        return ratePerMinute;
    }

    public void setRatePerMinute(String ratePerMinute) {
        this.ratePerMinute = ratePerMinute;
    }

    public String getRatePerHour() {
        return ratePerHour;
    }

    public void setRatePerHour(String ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    /** Do not reveal credentials or attacker-controlled configuration in diagnostics. */
    @Override
    public String toString() {
        return "SocLlmProperties[enabled=" + presence(enabled)
                + ", baseUrl=" + presence(baseUrl)
                + ", apiKey=[REDACTED]"
                + ", model=" + presence(model)
                + ", timeoutSeconds=" + presence(timeoutSeconds)
                + ", maxOutputTokens=" + presence(maxOutputTokens)
                + ", maxInFlight=" + presence(maxInFlight)
                + ", ratePerMinute=" + presence(ratePerMinute)
                + ", ratePerHour=" + presence(ratePerHour) + "]";
    }

    private static String presence(String value) {
        return value == null || value.isBlank() ? "[MISSING]" : "[PRESENT]";
    }
}
