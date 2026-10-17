package sg.edu.nus.facilityflow.ai.manager;

import java.util.Objects;

/** Typed advisory outcome; it never assigns a Technician or mutates a Request. */
public sealed interface TriageResponse permits TriageResponse.Success, TriageResponse.Failure {
    record Success(TriageResult result) implements TriageResponse {
        public Success {
            Objects.requireNonNull(result, "result");
        }
    }

    record Failure(TriageFailureCode code) implements TriageResponse {
        public Failure {
            Objects.requireNonNull(code, "code");
        }
    }

    static Failure failure(TriageFailureCode code) {
        return new Failure(code);
    }
}
