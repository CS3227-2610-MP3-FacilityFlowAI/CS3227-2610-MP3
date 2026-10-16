package sg.edu.nus.facilityflow.ai;

import java.io.IOException;
import java.time.Duration;

/** REQ-AI-006: server-only adapter seam. Implementations must honor interruption and remaining deadline. */
public interface AiTransport {
    record Response(int status, String content, Long inputTokens, Long outputTokens, Long totalTokens, Duration retryAfter) { }
    boolean available();
    String model();
    default java.util.Set<String> protectedValues() { return java.util.Set.of(); }
    Response complete(String systemMessage, String userData, int maxOutputTokens, Duration remaining) throws IOException, InterruptedException;
}
