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
        LOGGER.atWarn().addKeyValue("event", "AI_FEATURE_FAILURE")
                .addKeyValue("feature", feature.name()).addKeyValue("outcome", code.name())
                .log("SoCLaaS feature failure");
    }

    public static void persistenceFailed(AiInvocationTelemetry.Invocation invocation) {
        LOGGER.atWarn().addKeyValue("event", "AI_TELEMETRY_PERSISTENCE_FAILED")
                .addKeyValue("invocation_id", invocation == null ? null : invocation.id())
                .addKeyValue("correlation_id", invocation == null
                        ? sg.edu.nus.facilityflow.operations.RequestCorrelationFilter.currentId() : invocation.correlationId())
                .log("AI telemetry persistence failed");
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
