package sg.edu.nus.facilityflow.ai;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.requester.*;
import tools.jackson.databind.json.JsonMapper;

/** Test-only shared feature port; never opens a provider connection. */
public class FakeSmartReportAssistant implements SmartReportAssistant {
    public static final String VALID = """
            {"draftTitle":"Flickering light","suggestedCategory":"Electrical","suggestedUrgency":"Normal",
             "missingInformationQuestions":["Which room is affected?"],"rationale":"Clarify the affected room."}
            """;
    public record Response(String content) { }
    @FunctionalInterface
    public interface Behavior { Response complete(String system, String user, Duration remaining) throws IOException, InterruptedException; }
    public final AtomicInteger calls = new AtomicInteger();
    public volatile String user;
    public volatile boolean enabled = true;
    public volatile Behavior behavior = (system, user, remaining) -> success(VALID);
    public AiProviderLifecycle lifecycle() {
        return new AiProviderLifecycle() {
            public boolean enabled() { return enabled; }
            public State state() { return enabled ? State.READY : State.DISABLED; }
            public CompletionStage<State> readiness() { return CompletableFuture.completedFuture(state()); }
            public void close() { }
        };
    }
    public CompletionStage<SmartReportResponse> request(SmartReportContext context) {
        return request(context, Instant.now().plusSeconds(30));
    }
    public CompletionStage<SmartReportResponse> request(SmartReportContext context, Instant deadline) {
        calls.incrementAndGet();
        var json = JsonMapper.builder().build();
        user = json.writeValueAsString(context);
        SmartReportResponse result;
        try {
            var response = behavior.complete("", user, Duration.between(Instant.now(), deadline));
            var invocation = AiInvocationTelemetry.current();
            if (invocation != null) invocation.recordUsage(10L, 20L, 30L);
            var value = json.readValue(response.content(), SmartReportResult.class);
            result = new SmartReportResponse.Success(value);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            result = SmartReportResponse.failure(SmartReportFailureCode.AI_TIMEOUT);
        } catch (IOException failure) {
            result = SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE);
        } catch (RuntimeException invalid) {
            result = SmartReportResponse.failure(SmartReportFailureCode.AI_INVALID_OUTPUT);
        }
        return CompletableFuture.completedFuture(result);
    }
    public static Response success(String json) { return new Response(json); }
    public void reset() {
        calls.set(0); user = null; enabled = true;
        behavior = (system, user, remaining) -> success(VALID);
    }
}
