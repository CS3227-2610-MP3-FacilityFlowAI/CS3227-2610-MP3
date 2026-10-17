package sg.edu.nus.facilityflow.manager;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import sg.edu.nus.facilityflow.ai.AiFailureDiagnostics;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;

/** Application-level advisory outcome; it never assigns a Technician or mutates a Request. */
public sealed interface ManagerTriageResponse
        permits ManagerTriageResponse.Success, ManagerTriageResponse.Failure {
    record Success(TriageResult result, TriageRankingResult ranking) implements ManagerTriageResponse {
        public Success {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(ranking, "ranking");
        }
    }

    record Failure(TriageFailureCode code) implements ManagerTriageResponse {
        public Failure {
            Objects.requireNonNull(code, "code");
            AiFailureDiagnostics.report(AiFailureDiagnostics.Feature.TRIAGE, code);
        }

        @JsonProperty("message")
        public String message() {
            return AiFailureDiagnostics.message(code);
        }
    }

    static Failure failure(TriageFailureCode code) {
        return new Failure(code);
    }
}
