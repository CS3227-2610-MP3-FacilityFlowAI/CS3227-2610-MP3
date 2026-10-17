package sg.edu.nus.facilityflow.ai.requester;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import sg.edu.nus.facilityflow.ai.AiFailureDiagnostics;

/** Typed, non-persisting Smart Report outcome. */
public sealed interface SmartReportResponse permits SmartReportResponse.Success, SmartReportResponse.Failure {
    record Success(SmartReportResult result) implements SmartReportResponse {
        public Success {
            Objects.requireNonNull(result, "result");
        }
    }

    record Failure(SmartReportFailureCode code) implements SmartReportResponse {
        public Failure {
            Objects.requireNonNull(code, "code");
            AiFailureDiagnostics.report(AiFailureDiagnostics.Feature.SMART_REPORT, code);
        }

        @JsonProperty("message")
        public String message() {
            return AiFailureDiagnostics.message(code);
        }
    }

    static Failure failure(SmartReportFailureCode code) {
        return new Failure(code);
    }
}
