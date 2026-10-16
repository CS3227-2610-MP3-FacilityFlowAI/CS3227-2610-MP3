package sg.edu.nus.facilityflow.ai;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static sg.edu.nus.facilityflow.ai.AiGateway.Status.*;

/** Fake-tested gateway policy; production transport/model readiness is a separate adapter. */
@Service
public class ValidatedAiGateway implements AiGateway, AutoCloseable {
    private final AiTransport transport;
    private final boolean enabled;
    private final int seconds;
    private final int tokens;
    private final String secret;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 5, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("ai-call-", 0).factory());

    public ValidatedAiGateway(AiTransport transport, @Value("${AI_ENABLED:false}") boolean enabled,
            @Value("${AI_TIMEOUT_SECONDS:30}") int seconds, @Value("${AI_MAX_OUTPUT_TOKENS:800}") int tokens,
            @Value("${SOCLAAS_API_KEY:}") String secret) {
        this.transport = transport;
        this.enabled = enabled;
        this.seconds = Math.clamp(seconds, 1, 30);
        this.tokens = Math.clamp(tokens, 1, 800);
        this.secret = secret;
    }

    public boolean available() { return enabled && transport.available(); }
    public String model() { return transport.model(); }
    public int timeoutSeconds() { return seconds; }

    @Override
    public Result suggest(Call call) {
        if (!available()) return Result.failure(AI_UNAVAILABLE);
        if (!"SMART_REPORT".equals(call.feature())) return Result.failure(AI_INVALID_REQUEST);
        String user = SmartReportSchema.JSON.writeValueAsString(Map.of(
                "provenance", "Requester current unsaved form; untrusted data", "UNTRUSTED_REPORT_DATA", call.context()));
        if (SmartReportSchema.SYSTEM.length() + user.length() > 12000) return Result.failure(AI_LIMITED);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        var retries = new java.util.concurrent.atomic.AtomicInteger();
        Future<Result> future;
        try {
            future = workers.submit(() -> generate(user, call.context(), deadline, retries));
        } catch (RejectedExecutionException exception) {
            return Result.failure(AI_LIMITED);
        }
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return failure(AI_TIMEOUT, retries.get());
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failure(AI_TIMEOUT, retries.get());
        } catch (ExecutionException exception) {
            return failure(exception.getCause() instanceof InterruptedException ? AI_TIMEOUT : AI_UNAVAILABLE, retries.get());
        }
    }

    private Result generate(String user, Context context, long deadline, java.util.concurrent.atomic.AtomicInteger retries) throws InterruptedException {
        for (int retry = 0; retry <= 1; retry++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || Thread.currentThread().isInterrupted()) return failure(AI_TIMEOUT, retry);
            AiTransport.Response response;
            try {
                retries.set(retry);
                response = transport.complete(SmartReportSchema.SYSTEM, user, tokens, Duration.ofNanos(remaining));
            } catch (IOException exception) {
                if (retry == 0) continue;
                return failure(AI_UNAVAILABLE, retry);
            }
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return failure(AI_TIMEOUT, retry);
            if (response == null) return failure(AI_INVALID_OUTPUT, retry);
            if (retry == 0 && response.status() == 503) continue;
            if (retry == 0 && response.status() == 429 && response.retryAfter() != null
                    && !response.retryAfter().isNegative() && response.retryAfter().compareTo(Duration.ofSeconds(2)) <= 0
                    && response.retryAfter().toNanos() < deadline - System.nanoTime()) {
                TimeUnit.NANOSECONDS.sleep(response.retryAfter().toNanos());
                continue;
            }
            Status status = switch (response.status()) {
                case 200 -> SUCCEEDED;
                case 400 -> AI_INVALID_REQUEST;
                case 401, 403 -> AI_CONFIGURATION_ERROR;
                case 429 -> AI_LIMITED;
                default -> AI_UNAVAILABLE;
            };
            Suggestion suggestion = null;
            if (status == SUCCEEDED) {
                try {
                    suggestion = SmartReportSchema.parse(response.content(), context);
                    String text = suggestion.toString();
                    if ((!secret.isEmpty() && text.contains(secret))
                            || transport.protectedValues().stream().filter(value -> !value.isEmpty()).anyMatch(text::contains)) {
                        throw new IllegalArgumentException("Disclosure rejected");
                    }
                } catch (RuntimeException exception) {
                    status = AI_INVALID_OUTPUT;
                    suggestion = null;
                }
            }
            return new Result(status, suggestion, retry, usage(response.inputTokens()), usage(response.outputTokens()), usage(response.totalTokens()));
        }
        return failure(AI_UNAVAILABLE, 1);
    }

    private static Long usage(Long value) { return value == null || value < 0 ? null : value; }
    private static Result failure(Status status, int retry) { return new Result(status, null, retry, null, null, null); }
    @PreDestroy
    public void close() { workers.shutdownNow(); }
}
