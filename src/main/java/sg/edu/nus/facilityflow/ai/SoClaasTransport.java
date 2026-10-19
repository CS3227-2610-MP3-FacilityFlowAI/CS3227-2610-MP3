package sg.edu.nus.facilityflow.ai;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.JsonNode;

/** REQ-AI-001–006 / AI-SEC-019/020: HTTPS-only SoCLaaS with bounded first-use model discovery. */
public final class SoClaasTransport implements AiTransport, AutoCloseable {
    private final URI base;
    private final String key;
    private final String configuredModel;
    private final HttpClient client;
    private final AtomicBoolean probing = new AtomicBoolean();
    private volatile boolean ready;
    private volatile long nextProbe;

    public SoClaasTransport(String baseUrl, String key, String model) {
        this(allowedBase(baseUrl), key, model, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build());
    }

    // Package-private injection is used only by local HTTP contract tests; runtime has no insecure-URL switch.
    SoClaasTransport(URI base, String key, String model, HttpClient client) {
        this.base = base;
        this.key = key == null ? "" : key.strip();
        configuredModel = model == null ? "" : model.strip();
        this.client = client;
    }

    private static URI allowedBase(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip().replaceAll("/+$", ""));
            return "https".equals(uri.getScheme()) && "soclaas-api.comp.nus.edu.sg".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && "/v1".equals(uri.getPath())
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null ? uri : null;
        } catch (IllegalArgumentException exception) { return null; }
    }

    public boolean available() {
        return base != null && !key.isBlank() && key.chars().allMatch(c -> c > 32 && c < 127)
                && !configuredModel.isBlank() && configuredModel.length() <= 200
                && !configuredModel.contains(key)
                && configuredModel.codePoints().noneMatch(Character::isISOControl);
    }
    public String model() { return available() ? configuredModel : null; }

    @Override
    public Response complete(String system, String user, int maximum, Duration remaining) throws IOException, InterruptedException {
        if (!available()) throw new UnavailableException();
        long deadline = System.nanoTime() + remaining.toNanos();
        if (!modelReady(remaining)) throw new UnavailableException();
        String payload = SmartReportSchema.JSON.writeValueAsString(Map.of("model", configuredModel, "stream", false,
                "reasoning_effort", "none", "max_tokens", Math.clamp(maximum, 1, 800), "messages", List.of(
                        Map.of("role", "system", "content", system), Map.of("role", "user", "content", user))));
        var response = send("/chat/completions", payload, left(deadline));
        if (response.statusCode() != 200) {
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
                ready = false;
                nextProbe = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            }
            return new Response(response.statusCode(), null, null, null, null,
                    response.headers().firstValue("Retry-After").map(SoClaasTransport::retryAfter).orElse(null));
        }
        try {
            JsonNode root = SmartReportSchema.JSON.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() != 1) throw new InvalidResponseException();
            JsonNode choice = choices.get(0);
            JsonNode message = choice.get("message");
            if (!"stop".equals(choice.path("finish_reason").asString()) || message == null
                    || !"assistant".equals(message.path("role").asString()) || !message.path("content").isString()
                    || message.path("content").asString().isBlank()
                    || (message.has("tool_calls") && !message.get("tool_calls").isNull())
                    || (message.has("function_call") && !message.get("function_call").isNull())
                    || (message.has("refusal") && !message.get("refusal").isNull())) throw new InvalidResponseException();
            return new Response(200, message.get("content").asString(), usage(root.path("usage").get("prompt_tokens")),
                    usage(root.path("usage").get("completion_tokens")), usage(root.path("usage").get("total_tokens")), null);
        } catch (RuntimeException exception) { throw new InvalidResponseException(); }
    }

    private boolean modelReady(Duration remaining) throws InterruptedException {
        if (System.nanoTime() < nextProbe) return ready;
        if (!probing.compareAndSet(false, true)) return false;
        ready = false;
        try {
            var response = send("/models", null, remaining.compareTo(Duration.ofSeconds(2)) < 0 ? remaining : Duration.ofSeconds(2));
            ready = false;
            if (response.statusCode() == 200) {
                JsonNode data = SmartReportSchema.JSON.readTree(response.body()).get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode item : data) if (item.path("id").isString() && configuredModel.equals(item.get("id").asString())) ready = true;
                }
            }
        } catch (IOException | RuntimeException exception) { ready = false; }
        finally {
            nextProbe = System.nanoTime() + TimeUnit.SECONDS.toNanos(ready ? 300 : 60);
            probing.set(false);
        }
        return ready;
    }

    private HttpResponse<byte[]> send(String path, String payload, Duration remaining) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(base.toString() + path)).timeout(remaining)
                .header("Authorization", "Bearer " + key).header("Accept", "application/json");
        if (payload == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        var pending = client.sendAsync(builder.build(), info -> new BoundedBody());
        try {
            return pending.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new HttpTimeoutException("AI deadline exceeded");
        } catch (InterruptedException exception) {
            pending.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException failure) throw failure;
            throw new IOException("AI connection unavailable");
        }
    }

    private static Duration left(long deadline) throws HttpTimeoutException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new HttpTimeoutException("AI deadline exceeded");
        return Duration.ofNanos(nanos);
    }
    private static Duration retryAfter(String value) {
        try {
            int seconds = Integer.parseInt(value);
            return seconds >= 0 && seconds <= 2 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException exception) { return null; }
    }
    private static Long usage(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0 ? value.asLong() : null;
    }

    /** Typed overflow failure avoids retrying an oversized provider body as a network error. */
    private static class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> body = HttpResponse.BodySubscribers.ofByteArray();
        private java.util.concurrent.Flow.Subscription subscription;
        private long size;
        public CompletionStage<byte[]> getBody() { return body.getBody(); }
        public void onSubscribe(java.util.concurrent.Flow.Subscription value) { subscription = value; body.onSubscribe(value); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) size += buffer.remaining();
            if (size > 65536) { subscription.cancel(); body.onError(new InvalidResponseException()); }
            else body.onNext(buffers);
        }
        public void onError(Throwable error) { body.onError(error); }
        public void onComplete() { body.onComplete(); }
    }

    @PreDestroy
    public void close() { client.shutdownNow(); }
}
