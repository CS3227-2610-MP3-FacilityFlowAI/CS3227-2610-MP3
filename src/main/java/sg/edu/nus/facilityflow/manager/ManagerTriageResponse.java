package sg.edu.nus.facilityflow.manager;

import java.util.Objects;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import sg.edu.nus.facilityflow.ai.AiFailureDiagnostics;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;

/** Application-level advisory outcome; it never assigns a Technician or mutates a Request. */
public sealed interface ManagerTriageResponse
        permits ManagerTriageResponse.Success, ManagerTriageResponse.Failure {
    record Success(TriageResult result, TriageRankingResult ranking, ReviewContext review) implements ManagerTriageResponse {
        public Success(TriageResult result, TriageRankingResult ranking) {
            this(result, ranking, null);
        }

        public Success {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(ranking, "ranking");
        }
    }

    record ReviewContext(long requestVersion, String status, String snapshotFingerprint,
                         List<ReviewOption> categories, List<ReviewOption> technicians) {
        public ReviewContext {
            if (requestVersion < 0) throw new IllegalArgumentException("requestVersion cannot be negative");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(snapshotFingerprint, "snapshotFingerprint");
            categories = List.copyOf(categories);
            technicians = List.copyOf(technicians);
        }
    }

    record ReviewOption(UUID id, String label) {
        public ReviewOption {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
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
