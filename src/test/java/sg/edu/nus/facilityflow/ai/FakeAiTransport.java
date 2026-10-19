package sg.edu.nus.facilityflow.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic, local-only REQ-AI-006 adapter; never connects to a provider. */
public class FakeAiTransport implements AiTransport {
    public static final String VALID = """
            {"draftTitle":"Flickering light","suggestedCategory":"Electrical","suggestedUrgency":"Normal",
             "missingInformationQuestions":["Which room is affected?"],"rationale":"Clarify the affected room."}
            """;
    @FunctionalInterface
    public interface Behavior { Response complete(String system, String user, Duration remaining) throws IOException, InterruptedException; }
    public final AtomicInteger calls = new AtomicInteger();
    public volatile String system;
    public volatile String user;
    public volatile boolean enabled = true;
    public volatile Behavior behavior = (system, user, remaining) -> success(VALID);
    public boolean available() { return enabled; }
    public String model() { return "synthetic-test-model"; }
    public Set<String> protectedValues() { return Set.of("UNAUTHORIZED_PRIVATE_CANARY_791"); }
    public Response complete(String system, String user, int maximum, Duration remaining) throws IOException, InterruptedException {
        calls.incrementAndGet();
        this.system = system;
        this.user = user;
        if (maximum > 800 || remaining.compareTo(Duration.ofSeconds(30)) > 0) throw new AssertionError("Unbounded call");
        return behavior.complete(system, user, remaining);
    }
    public static Response success(String json) { return new Response(200, json, 10L, 20L, 30L, null); }
    public void reset() {
        calls.set(0);
        system = null;
        user = null;
        enabled = true;
        behavior = (system, user, remaining) -> success(VALID);
    }
}
