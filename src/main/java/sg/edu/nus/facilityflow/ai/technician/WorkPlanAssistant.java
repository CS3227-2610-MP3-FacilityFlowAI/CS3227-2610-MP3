package sg.edu.nus.facilityflow.ai.technician;

import java.util.concurrent.CompletionStage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Deep feature seam for the read-only Technician Work Plan advisory. */
public interface WorkPlanAssistant {
    CompletionStage<WorkPlanResponse> request(WorkPlanContext context);

    static WorkPlanAssistant unavailable() {
        return new Inert();
    }

    /**
     * Exposes an existing implementation through this feature seam without
     * also registering it as an {@code AiProviderLifecycle} bean.
     */
    static WorkPlanAssistant delegating(WorkPlanAssistant delegate) {
        return new Delegating(Objects.requireNonNull(delegate, "delegate"));
    }

    final class Inert implements WorkPlanAssistant {
        private Inert() {}

        @Override
        public CompletionStage<WorkPlanResponse> request(WorkPlanContext context) {
            Objects.requireNonNull(context, "context");
            WorkPlanResponse response = WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE);
            return CompletableFuture.<WorkPlanResponse>completedFuture(response).minimalCompletionStage();
        }
    }

    final class Delegating implements WorkPlanAssistant {
        private final WorkPlanAssistant delegate;

        private Delegating(WorkPlanAssistant delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<WorkPlanResponse> request(WorkPlanContext context) {
            return delegate.request(context);
        }
    }
}
