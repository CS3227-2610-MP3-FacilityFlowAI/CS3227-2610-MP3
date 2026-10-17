package sg.edu.nus.facilityflow.ai.provider;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provider-only stage diagnostics with a closed vocabulary and no raw exception logging. */
final class ProviderDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderDiagnostics.class);

    private ProviderDiagnostics() {}

    enum Stage { READINESS, COMPLETION, SMART_REPORT_INPUT, SMART_REPORT_OUTPUT,
        WORK_PLAN_INPUT, WORK_PLAN_OUTPUT, TRIAGE_INPUT, TRIAGE_OUTPUT }

    enum Reason { MODEL_NOT_FOUND, EMPTY_RESPONSE, DEADLINE_EXCEEDED, SCHEDULER_REJECTED,
        EXECUTOR_REJECTED, INVALID_INPUT, INVALID_OUTPUT, DISCLOSURE_REJECTED, INTERNAL_FAILURE }

    static void report(Stage stage, Reason reason) {
        LOGGER.warn("SoCLaaS gateway stage={} reason={}", stage.name(), reason.name());
    }

    static void reportFailure(Stage stage, Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; depth < 8
                && (cause instanceof CompletionException || cause instanceof ExecutionException); depth++) {
            cause = cause.getCause();
        }
        if (cause instanceof ProviderFailure providerFailure) {
            LOGGER.warn("SoCLaaS gateway stage={} reason={}", stage.name(), providerFailure.code().name());
        } else {
            report(stage, Reason.INTERNAL_FAILURE);
        }
    }
}
