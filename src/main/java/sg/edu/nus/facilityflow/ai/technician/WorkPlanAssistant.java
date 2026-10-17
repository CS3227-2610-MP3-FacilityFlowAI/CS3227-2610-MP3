package sg.edu.nus.facilityflow.ai.technician;

import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Deep feature seam for the read-only Technician Work Plan advisory (REQ-TECH-012–017, REQ-AI-005, AI-SEC-008/013/016). */
public interface WorkPlanAssistant {
    CompletionStage<WorkPlanResponse> request(WorkPlanContext context);

    /**
     * Requests using the absolute deadline created by the role use case.
     * Implementations that do not need provider work retain the simple seam.
     * AI-SEC-008 requires adapters never to reset this budget.
     */
    default CompletionStage<WorkPlanResponse> request(WorkPlanContext context, Instant absoluteDeadline) {
        Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
        return request(context);
    }

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

        @Override
        public CompletionStage<WorkPlanResponse> request(
                WorkPlanContext context, Instant absoluteDeadline) {
            return delegate.request(context, absoluteDeadline);
        }
    }
}
