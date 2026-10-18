package sg.edu.nus.facilityflow.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stable public failure explanations and metadata-only diagnostics (AI-SEC-017/019). */
public final class AiFailureDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiFailureDiagnostics.class);

    private AiFailureDiagnostics() {}

    public enum Feature { SMART_REPORT, WORK_PLAN, TRIAGE }

    /** Logs only source-controlled feature and enum names; never exception or user content. */
    public static void report(Feature feature, Enum<?> code) {
        LOGGER.warn("SoCLaaS gateway failure feature={} code={}", feature.name(), code.name());
    }

    public static String message(Enum<?> code) {
        return switch (code.name()) {
            case "AI_UNAVAILABLE" -> "AI is unavailable. It may be disabled, not ready, or temporarily unreachable.";
            case "AI_LIMITED" -> "An AI rate or concurrency limit was reached. Try again later.";
            case "AI_INVALID_REQUEST" -> "The AI request failed input validation or was rejected by the provider.";
            case "AI_CONFIGURATION_ERROR" -> "The AI provider rejected authentication or access. Contact the administrator.";
            case "AI_TIMEOUT" -> "The AI request timed out or was cancelled. Try again.";
            case "AI_INVALID_OUTPUT" -> "The AI response had an invalid format or failed output validation.";
            case "DISCARDED_ACCESS_CHANGED" -> "The AI result was discarded because access or source data changed. Refresh and retry.";
            default -> "The AI request could not be completed.";
        };
    }
}
