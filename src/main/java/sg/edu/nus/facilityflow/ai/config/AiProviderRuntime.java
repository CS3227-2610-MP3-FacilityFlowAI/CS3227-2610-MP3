package sg.edu.nus.facilityflow.ai.config;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import sg.edu.nus.facilityflow.ai.manager.TriageAssistant;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportAssistant;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;

/** Combined application runtime seam used by lifecycle wiring and feature assistants. */
public interface AiProviderRuntime extends AiProviderLifecycle, SmartReportAssistant, WorkPlanAssistant,
        TriageAssistant {

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
        public CompletionStage<SmartReportResponse> request(SmartReportContext context) {
            Objects.requireNonNull(context, "context");
            return CompletableFuture.<SmartReportResponse>completedFuture(
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE)).minimalCompletionStage();
        }

        @Override
        public CompletionStage<TriageResponse> request(TriageContext context) {
            Objects.requireNonNull(context, "context");
            return CompletableFuture.<TriageResponse>completedFuture(
                    TriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE)).minimalCompletionStage();
        }

        @Override
        public void close() {
            // No provider core or resource exists in an inert runtime.
        }
    }
}
