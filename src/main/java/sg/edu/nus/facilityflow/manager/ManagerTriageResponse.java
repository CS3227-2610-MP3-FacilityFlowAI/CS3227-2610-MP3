package sg.edu.nus.facilityflow.manager;

import java.util.Objects;
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
        }
    }

    static Failure failure(TriageFailureCode code) {
        return new Failure(code);
    }
}
