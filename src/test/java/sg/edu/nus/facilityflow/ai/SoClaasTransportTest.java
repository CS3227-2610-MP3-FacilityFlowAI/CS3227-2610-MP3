package sg.edu.nus.facilityflow.ai;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static sg.edu.nus.facilityflow.ai.AiGateway.Status.*;

class SoClaasTransportTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService workers;
    private SoClaasTransport transport;
    private URI base;
    private final AtomicInteger models = new AtomicInteger();
    private final AtomicInteger completions = new AtomicInteger();
    private final AtomicInteger traps = new AtomicInteger();
    private volatile int modelStatus = 200;
    private volatile String modelBody = "{\"data\":[{\"id\":\"synthetic-configured-model\"}]}";
    private volatile int completionStatus = 200;
    private volatile String completionBody;
    private volatile String retryAfter;
    private volatile boolean stallBody;
    private volatile String sentBody;
    private volatile String sentAuthorization;
    private volatile String modelAuthorization;
    private static final String KEY = "synthetic-provider-key";
    private final AiGateway.Context context = new AiGateway.Context("Flickering light", "", "", "", null, null, List.of("Electrical"));

    @BeforeEach
    void localProvider() throws Exception {
        completionBody = envelope(FakeAiTransport.VALID);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());
        server.setExecutor(workers);
        server.createContext("/v1/models", exchange -> {
            models.incrementAndGet();
            modelAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (modelStatus == 302) exchange.getResponseHeaders().set("Location", base + "/trap");
            byte[] bytes = modelBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(modelStatus, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.createContext("/v1/chat/completions", exchange -> {
            completions.incrementAndGet();
            sentBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sentAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
            if (completionStatus == 302) exchange.getResponseHeaders().set("Location", base + "/trap");
            byte[] bytes = completionBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(completionStatus, bytes.length);
            try (var stream = exchange.getResponseBody()) {
                if (stallBody) {
                    stream.write(bytes, 0, 1);
                    stream.flush();
                    try { Thread.sleep(3000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                }
                stream.write(bytes, stallBody ? 1 : 0, bytes.length - (stallBody ? 1 : 0));
            } catch (java.io.IOException ignored) { /* Cancellation may close the local test connection. */ }
        });
        server.createContext("/v1/trap", exchange -> { traps.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        transport = localTransport();
    }

    @AfterEach
    void close() {
        if (transport != null) transport.close();
        if (server != null) server.stop(0);
        if (workers != null) workers.shutdownNow();
    }

    private SoClaasTransport localTransport() {
        return new SoClaasTransport(base, KEY, "synthetic-configured-model",
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }
    private AiGateway.Result suggest() {
        return suggest(5);
    }
    private AiGateway.Result suggest(int timeoutSeconds) {
        try (var gateway = new ValidatedAiGateway(transport, true, timeoutSeconds, 800, KEY)) {
            return gateway.suggest(new AiGateway.Call("SMART_REPORT", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), context));
        }
    }
    private static String envelope(String text) {
        return SmartReportSchema.JSON.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                "message", Map.of("role", "assistant", "content", text))),
                "usage", Map.of("prompt_tokens", 11, "completion_tokens", 22, "total_tokens", 33)));
    }

    @Test
    void reqAi001003FirstUseDiscoveryAndBearerNonStreamingStatelessPayload() {
        assertEquals(0, models.get());
        assertTrue(transport.available());
        assertEquals(0, models.get());
        var result = suggest();
        assertEquals(SUCCEEDED, result.status());
        assertEquals(11L, result.inputTokens());
        assertEquals(22L, result.outputTokens());
        assertEquals(33L, result.totalTokens());
        assertEquals("Bearer " + KEY, sentAuthorization);
        assertEquals(sentAuthorization, modelAuthorization);
        var body = SmartReportSchema.JSON.readTree(sentBody);
        assertEquals(java.util.Set.of("model", "stream", "reasoning_effort", "max_tokens", "messages"), body.propertyNames());
        assertEquals("none", body.get("reasoning_effort").asString());
        assertEquals("synthetic-configured-model", body.get("model").asString());
        assertFalse(body.get("stream").asBoolean());
        assertEquals(800, body.get("max_tokens").asInt());
        assertEquals(2, body.get("messages").size());
        assertEquals("system", body.get("messages").get(0).get("role").asString());
        assertEquals("user", body.get("messages").get(1).get("role").asString());
        assertFalse(sentBody.contains(KEY));
        assertEquals(SUCCEEDED, suggest().status());
        assertEquals(1, models.get());
        assertEquals(2, completions.get());
    }

    @Test
    void aiSec019RejectsUnsafeUrlsAndMissingConfigWithoutNetwork() {
        for (String url : List.of("http://soclaas-api.comp.nus.edu.sg/v1", base.toString(),
                "https://evil.invalid/v1", "https://soclaas-api.comp.nus.edu.sg.evil.invalid/v1",
                "https://user@soclaas-api.comp.nus.edu.sg/v1", "https://soclaas-api.comp.nus.edu.sg/v1?key=x",
                "https://soclaas-api.comp.nus.edu.sg/v1#x", "https://soclaas-api.comp.nus.edu.sg:444/v1", "bad-url")) {
            try (var invalid = new SoClaasTransport(url, KEY, "model")) { assertFalse(invalid.available()); }
        }
        try (var missing = new SoClaasTransport("https://soclaas-api.comp.nus.edu.sg/v1", "", "")) { assertFalse(missing.available()); }
        try (var secretModel = new SoClaasTransport("https://soclaas-api.comp.nus.edu.sg/v1", KEY, "model-" + KEY)) {
            assertFalse(secretModel.available());
            assertNull(secretModel.model());
        }
        try (var valid = new SoClaasTransport("https://soclaas-api.comp.nus.edu.sg/v1/", KEY, "model")) { assertTrue(valid.available()); }
        assertEquals(0, models.get());
        assertEquals(0, completions.get());
    }

    @Test
    void aiSec020MissingMalformedAndFailedDiscoveryAreUnavailableWithBoundedBackoff() {
        for (String body : List.of("{\"data\":[{\"id\":\"different-model\"}]}", "broken", "{}")) {
            transport.close(); transport = localTransport();
            modelBody = body;
            int before = models.get();
            var result = suggest();
            assertEquals(AI_UNAVAILABLE, result.status());
            assertEquals(0, result.retryCount());
            assertEquals(AI_UNAVAILABLE, suggest().status());
            assertEquals(before + 1, models.get());
            assertEquals(0, completions.get());
        }
        for (int code : List.of(401, 403, 429, 503)) {
            transport.close(); transport = localTransport();
            modelStatus = code;
            assertEquals(AI_UNAVAILABLE, suggest().status());
            assertEquals(0, completions.get());
        }
    }

    @Test
    void reqAi006HttpFailuresMapWithoutExposingBodiesAndRespectRetryCeiling() {
        for (int code : List.of(400, 401, 403, 429, 503)) {
            transport.close(); transport = localTransport();
            completionStatus = code;
            completionBody = "SYNTHETIC_SECRET_ERROR_BODY";
            retryAfter = code == 429 ? "0" : null;
            int before = completions.get();
            var result = suggest();
            assertEquals(switch (code) { case 400 -> AI_INVALID_REQUEST; case 401, 403 -> AI_CONFIGURATION_ERROR;
                case 429 -> AI_LIMITED; default -> AI_UNAVAILABLE; }, result.status());
            assertEquals(before + (code == 429 || code == 503 ? 2 : 1), completions.get());
            assertFalse(result.toString().contains("SYNTHETIC_SECRET_ERROR_BODY"));
        }
    }

    @Test
    void aiSec010MalformedTruncatedToolAndOversizedEnvelopesRejectWithoutRetry() {
        int caseNumber = 0;
        for (String body : List.of("bad-json", "{}", "{\"choices\":[]}", envelope(FakeAiTransport.VALID).replace("\"stop\"", "\"length\""),
                envelope(FakeAiTransport.VALID).replace("\"assistant\"", "\"system\""),
                envelope(FakeAiTransport.VALID).replace("\"role\":", "\"tool_calls\":[],\"role\":"), "x".repeat(65537))) {
            completionBody = body;
            int before = completions.get();
            assertEquals(AI_INVALID_OUTPUT, suggest().status(), "Envelope case " + ++caseNumber);
            assertEquals(before + 1, completions.get());
        }
        String valid = envelope(FakeAiTransport.VALID);
        completionBody = valid + " ".repeat(65536 - valid.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(SUCCEEDED, suggest().status());
    }

    @Test
    void aiSec008DelayedBodyIsCancelledWithinOriginalDeadline() {
        stallBody = true;
        var result = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> suggest(1));
        assertEquals(AI_TIMEOUT, result.status());
        assertEquals(1, completions.get());
        assertNull(result.suggestion());
    }

    @Test
    void aiSec019RedirectsNeverForwardBearerCredentials() {
        modelStatus = 302;
        assertEquals(AI_UNAVAILABLE, suggest().status());
        assertEquals(0, traps.get());
        assertEquals(0, completions.get());
        modelStatus = 200;
        transport.close(); transport = localTransport();
        completionStatus = 302;
        assertEquals(AI_UNAVAILABLE, suggest().status());
        assertEquals(0, traps.get());
    }

    @Test
    void reqAi007MissingOrInvalidUsageIsNeverEstimated() {
        completionBody = envelope(FakeAiTransport.VALID).replace("\"usage\"", "\"ignored_usage\"");
        var result = suggest();
        assertEquals(SUCCEEDED, result.status());
        assertNull(result.inputTokens()); assertNull(result.outputTokens()); assertNull(result.totalTokens());
        completionBody = envelope(FakeAiTransport.VALID).replace("\"prompt_tokens\":11", "\"prompt_tokens\":-1")
                .replace("\"completion_tokens\":22", "\"completion_tokens\":\"22\"");
        result = suggest();
        assertNull(result.inputTokens()); assertNull(result.outputTokens()); assertEquals(33L, result.totalTokens());
    }
}
