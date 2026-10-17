package sg.edu.nus.facilityflow.ai.manager;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Feature-specific seam for Manager triage recommendations. */
public interface TriageAssistant {
    CompletionStage<TriageResponse> request(TriageContext context);

    default CompletionStage<TriageResponse> request(TriageContext context, Instant absoluteDeadline) {
        Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
        return request(context);
    }

    static TriageAssistant unavailable() {
        return new Inert();
    }

    static TriageAssistant delegating(TriageAssistant delegate) {
        return new Delegating(Objects.requireNonNull(delegate, "delegate"));
    }

    final class Inert implements TriageAssistant {
        private Inert() {}

        @Override
        public CompletionStage<TriageResponse> request(TriageContext context) {
            Objects.requireNonNull(context, "context");
            return CompletableFuture.<TriageResponse>completedFuture(
                    TriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE)).minimalCompletionStage();
        }
    }

    final class Delegating implements TriageAssistant {
        private final TriageAssistant delegate;

        private Delegating(TriageAssistant delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<TriageResponse> request(TriageContext context) {
            return delegate.request(context);
        }
    }
}
