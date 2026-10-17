package sg.edu.nus.facilityflow.ai.requester;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Feature-specific seam for temporary Requester report drafting. */
public interface SmartReportAssistant {
    CompletionStage<SmartReportResponse> request(SmartReportContext context);

    default CompletionStage<SmartReportResponse> request(
            SmartReportContext context, Instant absoluteDeadline) {
        Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
        return request(context);
    }

    static SmartReportAssistant unavailable() {
        return new Inert();
    }

    static SmartReportAssistant delegating(SmartReportAssistant delegate) {
        return new Delegating(Objects.requireNonNull(delegate, "delegate"));
    }

    final class Inert implements SmartReportAssistant {
        private Inert() {}

        @Override
        public CompletionStage<SmartReportResponse> request(SmartReportContext context) {
            Objects.requireNonNull(context, "context");
            return CompletableFuture.<SmartReportResponse>completedFuture(
                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE)).minimalCompletionStage();
        }
    }

    final class Delegating implements SmartReportAssistant {
        private final SmartReportAssistant delegate;

        private Delegating(SmartReportAssistant delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<SmartReportResponse> request(SmartReportContext context) {
            return delegate.request(context);
        }

        @Override
        public CompletionStage<SmartReportResponse> request(
                SmartReportContext context, Instant absoluteDeadline) {
            return delegate.request(context, absoluteDeadline);
        }
    }
}
