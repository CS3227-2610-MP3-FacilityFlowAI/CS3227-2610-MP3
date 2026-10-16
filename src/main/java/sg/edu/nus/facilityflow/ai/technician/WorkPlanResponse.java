package sg.edu.nus.facilityflow.ai.technician;

import java.util.Objects;

/** Read-only advisory result; it never authorizes or performs a business write. */
public sealed interface WorkPlanResponse permits WorkPlanResponse.Success, WorkPlanResponse.Failure {

    record Success(WorkPlanResult plan) implements WorkPlanResponse {
        public Success {
            Objects.requireNonNull(plan, "plan");
        }
    }

    record Failure(WorkPlanFailureCode code) implements WorkPlanResponse {
        public Failure {
            Objects.requireNonNull(code, "code");
        }
    }

    static Failure failure(WorkPlanFailureCode code) {
        return new Failure(code);
    }
}
