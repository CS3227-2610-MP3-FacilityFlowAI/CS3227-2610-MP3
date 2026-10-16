package sg.edu.nus.facilityflow.ai.config;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;

/** Combined application runtime seam used by lifecycle wiring and feature assistants. */
public interface AiProviderRuntime extends AiProviderLifecycle, WorkPlanAssistant {

    static AiProviderRuntime inert(State state) {
        return new Inert(Objects.requireNonNull(state, "state"));
    }

    final class Inert extends AiProviderLifecycle.Inert implements AiProviderRuntime {
        private Inert(State state) {
            super(state);
        }

        @Override
        public CompletionStage<WorkPlanResponse> request(WorkPlanContext context) {
            Objects.requireNonNull(context, "context");
            WorkPlanResponse response = WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE);
            return CompletableFuture.<WorkPlanResponse>completedFuture(response).minimalCompletionStage();
        }

        @Override
        public void close() {
            // No provider core or resource exists in an inert runtime.
        }
    }
}
