package sg.edu.nus.facilityflow.ai.provider;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provider-only stage diagnostics with a closed vocabulary and no raw exception logging. */
final class ProviderDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderDiagnostics.class);

    private ProviderDiagnostics() {}

    enum Stage { TRANSPORT, READINESS, COMPLETION, SMART_REPORT_INPUT, SMART_REPORT_OUTPUT,
        WORK_PLAN_INPUT, WORK_PLAN_OUTPUT, TRIAGE_INPUT, TRIAGE_OUTPUT }

    enum Reason { MODEL_NOT_FOUND, EMPTY_RESPONSE, DEADLINE_EXCEEDED, SCHEDULER_REJECTED,
        EXECUTOR_REJECTED, INVALID_INPUT, INVALID_OUTPUT, DISCLOSURE_REJECTED,
        TELEMETRY_RECORDING_FAILED, INTERNAL_FAILURE, DNS_FAILURE, TLS_FAILURE,
        CONNECTION_FAILURE, HTTP_IO_FAILURE }

    static void report(Stage stage, Reason reason) {
        reportCode(stage, reason.name());
    }

    static void reportTransportFailure(Throwable failure) {
        Reason reason = Reason.INTERNAL_FAILURE;
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof javax.net.ssl.SSLException) {
                report(Stage.TRANSPORT, Reason.TLS_FAILURE);
                return;
            }
            if (failure instanceof java.net.UnknownHostException
                    || failure instanceof java.nio.channels.UnresolvedAddressException) {
                report(Stage.TRANSPORT, Reason.DNS_FAILURE);
                return;
            }
            if (failure instanceof java.net.ConnectException) {
                reason = Reason.CONNECTION_FAILURE;
            } else if (failure instanceof java.io.IOException && reason != Reason.CONNECTION_FAILURE) {
                reason = Reason.HTTP_IO_FAILURE;
            }
        }
        report(Stage.TRANSPORT, reason);
    }

    static void reportFailure(Stage stage, Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; depth < 8
                && (cause instanceof CompletionException || cause instanceof ExecutionException); depth++) {
            cause = cause.getCause();
        }
        if (cause instanceof ProviderFailure providerFailure) {
            reportCode(stage, providerFailure.code().name());
        } else {
            report(stage, Reason.INTERNAL_FAILURE);
        }
    }

    private static void reportCode(Stage stage, String code) {
        LOGGER.atWarn().addKeyValue("event", "AI_PROVIDER_FAILURE")
                .addKeyValue("stage", stage.name()).addKeyValue("reason", code)
                .log("SoCLaaS gateway stage={} reason={}", stage.name(), code);
    }
}
