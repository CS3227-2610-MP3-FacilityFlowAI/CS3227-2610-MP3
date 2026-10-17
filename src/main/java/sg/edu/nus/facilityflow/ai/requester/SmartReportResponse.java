package sg.edu.nus.facilityflow.ai.requester;

import java.util.Objects;

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
        }
    }

    static Failure failure(SmartReportFailureCode code) {
        return new Failure(code);
    }
}
