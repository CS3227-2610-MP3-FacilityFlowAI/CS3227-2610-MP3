package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;

class SocLlmHttpsProviderAdapterTest {
    private static final URI MODELS_URI = URI.create(
            "https://soclaas-api.comp.nus.edu.sg/v1/models");
    private static final URI COMPLETIONS_URI = URI.create(
            "https://soclaas-api.comp.nus.edu.sg/v1/chat/completions");
    private static final String KEY_CANARY = "adapter-secret-canary";

    @Test
    void reqAi001UsesCanonicalModelsUriGetAndBearerHeaders() {
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200, "{\"data\":[{\"id\":\"approved-model\"}]}", Map.of()));
        ProviderTestControls.MutableClock clock = new ProviderTestControls.MutableClock();

        ProviderModels models = newAdapter(transport, clock).listModels().join();

        assertEquals(java.util.Set.of("approved-model"), models.modelIds());
        HttpRequest request = transport.request;
        assertEquals("GET", request.method());
        assertEquals(MODELS_URI, request.uri());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElseThrow());
        assertEquals("Bearer test-only-key", request.headers().firstValue("Authorization").orElseThrow());
        assertNotNull(request.timeout());
        assertEquals(Duration.ofSeconds(30), request.timeout().orElseThrow());
    }

    @Test
    void reqAi002BuildsNonStreamingNonReasoningCompletionPostWithEscapedMessagesAndConfiguredLimits() {
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                        + "\"finish_reason\":\"stop\"}]}", Map.of()));
        ProviderTestControls.MutableClock clock = new ProviderTestControls.MutableClock();
        ProviderChatRequest request = request(
                "system \"line\n\t\\", "user \"line\n\t\\", clock.instant().plusSeconds(20));

        ProviderCompletion result = newAdapter(transport, clock).complete(request).join();

        assertEquals("ok", result.content());
        assertEquals("POST", transport.request.method());
        assertEquals(COMPLETIONS_URI, transport.request.uri());
        assertEquals("application/json", transport.request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("Bearer test-only-key", transport.request.headers().firstValue("Authorization").orElseThrow());
        String body = transport.body();
        assertEquals(
                "{\"model\":\"approved-model\",\"messages\":[{\"role\":\"system\",\"content\":"
                        + "\"system \\\"line\\n\\t\\\\\"},{\"role\":\"user\",\"content\":"
                        + "\"user \\\"line\\n\\t\\\\\"}],\"reasoning_effort\":\"none\","
                        + "\"max_tokens\":321,\"stream\":false}",
                body);
        assertFalse(body.contains(KEY_CANARY));
    }

    @Test
    void reqAi006DecodesSuccessfulCompletionAndClosesBody() {
        TrackingInputStream body = new TrackingInputStream(
                ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"answer\"},"
                        + "\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":4}}")
                        .getBytes(StandardCharsets.UTF_8));
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200, body, Map.of()));
        ProviderTestControls.MutableClock clock = new ProviderTestControls.MutableClock();

        ProviderCompletion result = newAdapter(transport, clock).complete(request(
                "system", "user", clock.instant().plusSeconds(20))).join();

        assertEquals("answer", result.content());
        assertTrue(result.usage().isPresent());
        assertEquals(4L, result.usage().orElseThrow().totalTokens());
        assertTrue(body.closed);
    }

    @Test
    void reqAi001RejectsRedirectResponseAndClosesBody() {
        TrackingInputStream body = new TrackingInputStream("redirect".getBytes(StandardCharsets.UTF_8));
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200, body, Map.of(), URI.create("https://redirect.invalid/v1/models")));

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.REDIRECT_REJECTED, failure);
        assertTrue(body.closed);
    }

    @Test
    void reqAi001RejectsUnsupportedContentEncodingAndClosesBody() {
        TrackingInputStream body = new TrackingInputStream("{}".getBytes(StandardCharsets.UTF_8));
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200, body, Map.of("Content-Encoding", List.of("gzip"))));

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.UNSUPPORTED_CONTENT_ENCODING, failure);
        assertTrue(body.closed);
    }

    @Test
    void reqAi006MapsSpecifiedHttpStatusesAndClosesNonSuccessfulBodies() {
        for (Map.Entry<Integer, ProviderFailure.Code> expected : Map.of(
                400, ProviderFailure.Code.HTTP_BAD_REQUEST,
                401, ProviderFailure.Code.HTTP_UNAUTHORIZED,
                403, ProviderFailure.Code.HTTP_FORBIDDEN,
                429, ProviderFailure.Code.HTTP_RATE_LIMITED,
                503, ProviderFailure.Code.HTTP_SERVICE_UNAVAILABLE,
                500, ProviderFailure.Code.HTTP_UNEXPECTED).entrySet()) {
            TrackingInputStream body = new TrackingInputStream(new byte[] {'x'});
            FakeTransport transport = new FakeTransport();
            transport.completeWith(response(expected.getKey(), body, Map.of()));

            ProviderFailure failure = failureOf(newAdapter(transport).listModels());

            assertCode(expected.getValue(), failure);
            assertTrue(body.closed, "body must close for status " + expected.getKey());
        }
    }

    @Test
    void reqAi001MapsRedirectStatusAsRedirectRejected() {
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(307, new TrackingInputStream(new byte[] {'x'}), Map.of()));

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.REDIRECT_REJECTED, failure);
    }

    @Test
    void reqAi001AcceptsRetryAfterDeltaOnlyWithinTwoSeconds() {
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(429, new TrackingInputStream(new byte[] {'x'}),
                Map.of("Retry-After", List.of("2"))));

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.HTTP_RATE_LIMITED, failure);
        assertEquals(Duration.ofSeconds(2), failure.retryAfter());
    }

    @Test
    void reqAi001RejectsRetryAfterDeltaAboveTwoSecondsAndMalformedDate() {
        for (String value : List.of("3", "not-a-date")) {
            FakeTransport transport = new FakeTransport();
            transport.completeWith(response(429, new TrackingInputStream(new byte[] {'x'}),
                    Map.of("Retry-After", List.of(value))));

            ProviderFailure failure = failureOf(newAdapter(transport).listModels());

            assertCode(ProviderFailure.Code.HTTP_RATE_LIMITED, failure);
            assertNull(failure.retryAfter());
        }
    }

    @Test
    void reqAi001ParsesRfc1123RetryAfterRelativeToInjectedClock() {
        ProviderTestControls.MutableClock clock = new ProviderTestControls.MutableClock();
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(429, new TrackingInputStream(new byte[] {'x'}),
                Map.of("Retry-After", List.of("Fri, 16 Oct 2026 00:00:02 GMT"))));

        ProviderFailure failure = failureOf(newAdapter(transport, clock).listModels());

        assertEquals(Duration.ofSeconds(2), failure.retryAfter());
    }

    @Test
    void reqAi001RejectsIdentityHeaderWithBlankOrNonIdentityValue() {
        for (String value : List.of("", "gzip", "identity, gzip")) {
            FakeTransport transport = new FakeTransport();
            transport.completeWith(response(200, new TrackingInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                    Map.of("Content-Encoding", List.of(value))));

            ProviderFailure failure = failureOf(newAdapter(transport).listModels());

            assertCode(ProviderFailure.Code.UNSUPPORTED_CONTENT_ENCODING, failure);
        }
    }

    @Test
    void reqAi003UsesProviderUriWithoutDuplicatingV1Path() {
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200, "{\"data\":[]}", Map.of()));

        newAdapter(transport).listModels().join();

        assertEquals("https://soclaas-api.comp.nus.edu.sg/v1/models", transport.request.uri().toString());
        assertFalse(transport.request.uri().toString().contains("/v1/v1/"));
    }

    @Test
    void reqAi004RedactsCredentialThroughConfigurationDiagnostics() {
        AiConfiguration.Available configuration = ProviderTestControls.availableConfiguration();

        assertFalse(configuration.toString().contains("test-only-key"));
        assertFalse(configuration.toString().contains(KEY_CANARY));
        assertTrue(configuration.toString().contains("[REDACTED]"));
        assertTrue(configuration.toString().contains("[PRESENT]"));
    }

    @Test
    void reqAi004RejectsInvalidCredentialCapabilityWithoutApplyingOrExposingKey() {
        AiConfiguration.Available configuration = ProviderTestControls.availableConfiguration();
        HttpRequest.Builder builder = HttpRequest.newBuilder(MODELS_URI);

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> configuration.applyAuthorization(builder, null));

        HttpRequest request = builder.build();
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        assertFalse(failure.getMessage().contains("test-only-key"));
        assertFalse(failure.toString().contains("test-only-key"));
    }

    @Test
    void reqAi006MapsTransportExceptionsToSafeFailuresWithoutProviderText() {
        FakeTransport transport = new FakeTransport();
        transport.failure = new IOException(KEY_CANARY);

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.NETWORK_FAILURE, failure);
        assertFalse(diagnostics(failure).contains(KEY_CANARY));
        assertNull(failure.getCause());
    }

    @Test
    void reqAi006MapsTransportTimeoutAndCancellation() {
        for (Throwable problem : List.of(new java.util.concurrent.TimeoutException(KEY_CANARY),
                new java.util.concurrent.CancellationException(KEY_CANARY))) {
            FakeTransport transport = new FakeTransport();
            transport.failure = problem;

            ProviderFailure failure = failureOf(newAdapter(transport).listModels());

            assertCode(problem instanceof java.util.concurrent.TimeoutException
                    ? ProviderFailure.Code.TIMEOUT : ProviderFailure.Code.CANCELLED, failure);
            assertFalse(diagnostics(failure).contains(KEY_CANARY));
        }
    }

    @Test
    void reqAi006JdkHttpTimeoutsRemainTimeoutsThroughAsyncWrappers() {
        for (Throwable timeout : List.of(
                new java.net.http.HttpTimeoutException(KEY_CANARY),
                new java.net.http.HttpConnectTimeoutException(KEY_CANARY),
                new java.util.concurrent.CompletionException(
                        new java.net.http.HttpTimeoutException(KEY_CANARY)))) {
            FakeTransport transport = new FakeTransport();
            transport.failure = timeout;

            ProviderFailure failure = failureOf(newAdapter(transport).listModels());

            assertCode(ProviderFailure.Code.TIMEOUT, failure);
            assertFalse(diagnostics(failure).contains(KEY_CANARY));
            assertNull(failure.getCause());
        }
    }

    @Test
    void reqAi006CallerCancellationCancelsTransportStageAndClosesLateResponseBody() {
        FakeTransport transport = new FakeTransport();
        CompletableFuture<HttpResponse<InputStream>> stage = transport.pending();
        CompletableFuture<ProviderModels> result = newAdapter(transport).listModels();
        TrackingInputStream body = new TrackingInputStream("{\"data\":[]}".getBytes(StandardCharsets.UTF_8));

        assertTrue(result.cancel(true));
        assertTrue(((TransportFuture) stage).cancelRequested);
        stage.complete(response(200, body, Map.of()));
        assertTrue(body.closed);
    }

    @Test
    void reqAi006ExpiredCompletionDeadlineMakesZeroTransportCall() {
        FakeTransport transport = new FakeTransport();
        ProviderTestControls.MutableClock clock = new ProviderTestControls.MutableClock();

        CompletableFuture<ProviderCompletion> result = newAdapter(transport, clock).complete(request(
                "system", "user", clock.instant()));

        ProviderFailure failure = failureOf(result);
        assertCode(ProviderFailure.Code.TIMEOUT, failure);
        assertEquals(0, transport.sendCalls);
    }

    @Test
    void reqAi006RejectsMalformedSurrogateBeforeTransportCall() {
        FakeTransport transport = new FakeTransport();
        ProviderTestControls.MutableClock clock = new ProviderTestControls.MutableClock();
        ProviderChatRequest request = request("system\uD83D", "user", clock.instant().plusSeconds(20));

        ProviderFailure failure = failureOf(newAdapter(transport, clock).complete(request));

        assertCode(ProviderFailure.Code.REQUEST_BUILD_FAILED, failure);
        assertEquals(0, transport.sendCalls);
    }

    @Test
    void reqAi006ClosesBodyWhenEnvelopeIsMalformedOrUnreadable() {
        TrackingInputStream malformed = new TrackingInputStream("not-json".getBytes(StandardCharsets.UTF_8));
        FakeTransport malformedTransport = new FakeTransport();
        malformedTransport.completeWith(response(200, malformed, Map.of()));

        ProviderFailure malformedFailure = failureOf(newAdapter(malformedTransport).listModels());

        assertCode(ProviderFailure.Code.MALFORMED_JSON, malformedFailure);
        assertTrue(malformed.closed);

        TrackingInputStream unreadable = new TrackingInputStream(new byte[] {'x'});
        unreadable.readFailure = true;
        FakeTransport unreadableTransport = new FakeTransport();
        unreadableTransport.completeWith(response(200, unreadable, Map.of()));

        ProviderFailure unreadableFailure = failureOf(newAdapter(unreadableTransport).listModels());

        assertCode(ProviderFailure.Code.BODY_UNREADABLE, unreadableFailure);
        assertTrue(unreadable.closed);
        assertFalse(diagnostics(unreadableFailure).contains(KEY_CANARY));
    }

    @Test
    void reqAi006ClosesBodyWhenTransportResponseHasNullHeaders() {
        TrackingInputStream body = new TrackingInputStream("{\"data\":[]}".getBytes(StandardCharsets.UTF_8));
        FakeTransport transport = new FakeTransport();
        transport.completeWith(new FakeResponse(200, body, null, null));

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.BODY_UNREADABLE, failure);
        assertTrue(body.closed);
        assertFalse(diagnostics(failure).contains(KEY_CANARY));
    }

    @Test
    void reqAi006RejectsOversizedEnvelopeThroughBoundedDecoderAndClosesBody() {
        byte[] oversized = new byte[ProviderEnvelopeDecoder.MAX_BODY_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'x');
        TrackingInputStream body = new TrackingInputStream(oversized);
        FakeTransport transport = new FakeTransport();
        transport.completeWith(response(200, body, Map.of()));

        ProviderFailure failure = failureOf(newAdapter(transport).listModels());

        assertCode(ProviderFailure.Code.BODY_TOO_LARGE, failure);
        assertTrue(body.closed);
    }

    @Test
    void reqAi001DefaultJdkTransportDisablesRedirects() throws Exception {
        java.lang.reflect.Constructor<JdkProviderHttpTransport> constructor =
                JdkProviderHttpTransport.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        JdkProviderHttpTransport transport = constructor.newInstance();
        java.lang.reflect.Field field = JdkProviderHttpTransport.class.getDeclaredField("client");
        field.setAccessible(true);
        HttpClient client = (HttpClient) field.get(transport);

        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    }

    private static SocLlmHttpsProviderAdapter newAdapter(FakeTransport transport) {
        return new SocLlmHttpsProviderAdapter(ProviderTestControls.availableConfiguration(), transport);
    }

    private static SocLlmHttpsProviderAdapter newAdapter(
            FakeTransport transport, ProviderTestControls.MutableClock clock) {
        return new SocLlmHttpsProviderAdapter(
                ProviderTestControls.availableConfiguration(), transport, clock);
    }

    private static ProviderChatRequest request(String system, String user, Instant deadline) {
        return new ProviderChatRequest(system, user, ProviderTestControls.MODEL, 321, deadline);
    }

    private static ProviderFailure failureOf(CompletableFuture<?> result) {
        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertTrue(failure.getCause() instanceof ProviderFailure,
                () -> "Expected ProviderFailure but got " + failure.getCause());
        return (ProviderFailure) failure.getCause();
    }

    private static void assertCode(ProviderFailure.Code expected, ProviderFailure actual) {
        assertEquals(expected, actual.code());
    }

    private static String diagnostics(Throwable failure) {
        StringBuilder output = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            output.append(current.getClass().getName()).append(' ').append(current.getMessage()).append(' ');
        }
        return output.toString();
    }

    private static HttpResponse<InputStream> response(int status, String body, Map<String, List<String>> headers) {
        return response(status, new TrackingInputStream(body.getBytes(StandardCharsets.UTF_8)), headers);
    }

    private static HttpResponse<InputStream> response(
            int status, InputStream body, Map<String, List<String>> headers) {
        return response(status, body, headers, null);
    }

    private static HttpResponse<InputStream> response(
            int status, InputStream body, Map<String, List<String>> headers, URI uri) {
        return new FakeResponse(status, body, HttpHeaders.of(headers, (name, value) -> true), uri);
    }

    private static final class FakeTransport implements ProviderHttpTransport {
        private HttpRequest request;
        private String requestBody;
        private CompletableFuture<HttpResponse<InputStream>> nextStage;
        private Throwable failure;
        private int sendCalls;

        @Override
        public CompletableFuture<HttpResponse<InputStream>> send(HttpRequest request) {
            this.request = request;
            this.requestBody = readBody(request);
            sendCalls++;
            if (failure != null) {
                CompletableFuture<HttpResponse<InputStream>> failed = new CompletableFuture<>();
                failed.completeExceptionally(failure);
                return failed;
            }
            return nextStage == null ? new CompletableFuture<>() : nextStage;
        }

        void completeWith(HttpResponse<InputStream> response) {
            nextStage = CompletableFuture.completedFuture(response);
        }

        CompletableFuture<HttpResponse<InputStream>> pending() {
            nextStage = new TransportFuture();
            return nextStage;
        }

        String body() {
            return requestBody == null ? "" : requestBody;
        }

        private static String readBody(HttpRequest request) {
            if (request.bodyPublisher().isEmpty()) {
                return "";
            }
            CompletableFuture<String> result = new CompletableFuture<>();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] chunk = new byte[item.remaining()];
                    item.get(chunk);
                    bytes.writeBytes(chunk);
                }

                @Override
                public void onError(Throwable failure) {
                    result.completeExceptionally(failure);
                }

                @Override
                public void onComplete() {
                    result.complete(bytes.toString(StandardCharsets.UTF_8));
                }
            });
            return result.join();
        }
    }

    private static final class TransportFuture extends CompletableFuture<HttpResponse<InputStream>> {
        private boolean cancelRequested;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelRequested = true;
            return true;
        }
    }

    private static final class FakeResponse implements HttpResponse<InputStream> {
        private final int status;
        private final InputStream body;
        private final HttpHeaders headers;
        private final URI uri;

        private FakeResponse(int status, InputStream body, HttpHeaders headers, URI uri) {
            this.status = status;
            this.body = body;
            this.headers = headers;
            this.uri = uri;
        }

        @Override public int statusCode() { return status; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return headers; }
        @Override public InputStream body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return uri; }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;
        private boolean readFailure;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            if (readFailure) {
                throw new IllegalStateException(KEY_CANARY);
            }
            return super.read(buffer, offset, length);
        }

        @Override
        public synchronized int read() {
            if (readFailure) {
                throw new IllegalStateException(KEY_CANARY);
            }
            return super.read();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
