package sg.edu.nus.facilityflow.ai.provider;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;

/**
 * Production SoCLaaS HTTPS adapter. The adapter owns HTTP details and exposes
 * only decoded provider values through the package-private provider port.
 */
final class SocLlmHttpsProviderAdapter implements SocLlmProviderPort {
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ACCEPT_ENCODING = "gzip, identity";
    private static final String IDENTITY_ENCODING = "identity";
    private static final String GZIP_ENCODING = "gzip";

    private final AiConfiguration.Available configuration;
    private final ProviderHttpTransport transport;
    private final Clock clock;
    private final Duration configuredTimeout;
    private final ProviderEnvelopeDecoder decoder = new ProviderEnvelopeDecoder();
    private final ProviderCredentialCapability credentialCapability = ProviderCredentialCapability.create();

    SocLlmHttpsProviderAdapter(AiConfiguration.Available configuration) {
        this(configuration, new JdkProviderHttpTransport(), Clock.systemUTC());
    }

    SocLlmHttpsProviderAdapter(
            AiConfiguration.Available configuration,
            ProviderHttpTransport transport) {
        this(configuration, transport, Clock.systemUTC());
    }

    SocLlmHttpsProviderAdapter(
            AiConfiguration.Available configuration,
            ProviderHttpTransport transport,
            Clock clock) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.configuredTimeout = Duration.ofSeconds(configuration.timeoutSeconds());
    }

    @Override
    public CompletableFuture<ProviderModels> listModels() {
        Instant deadline = safeDeadline(clock.instant(), configuredTimeout);
        return send("GET", endpoint("models"), null, deadline, this::decodeModels);
    }

    @Override
    public CompletableFuture<ProviderCompletion> complete(ProviderChatRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        if (!now.isBefore(request.absoluteDeadline())) {
            return failed(new ProviderFailure(ProviderFailure.Code.TIMEOUT));
        }
        Instant deadline = request.absoluteDeadline().isBefore(safeDeadline(now, configuredTimeout))
                ? request.absoluteDeadline()
                : safeDeadline(now, configuredTimeout);
        String body;
        try {
            body = completionJson(request);
        } catch (RuntimeException ignored) {
            return failed(new ProviderFailure(ProviderFailure.Code.REQUEST_BUILD_FAILED));
        }
        return send("POST", endpoint("chat/completions"), body, deadline, this::decodeCompletion);
    }

    private <T> CompletableFuture<T> send(
            String method,
            URI uri,
            String body,
            Instant absoluteDeadline,
            Decoder<T> responseDecoder) {
        Instant now = clock.instant();
        Duration timeout = remainingTimeout(now, absoluteDeadline);
        if (timeout.isZero() || timeout.isNegative()) {
            return failed(new ProviderFailure(ProviderFailure.Code.TIMEOUT));
        }

        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", JSON_CONTENT_TYPE)
                    .header("Accept-Encoding", ACCEPT_ENCODING);
            configuration.applyAuthorization(builder, credentialCapability);
            if ("POST".equals(method)) {
                builder.header("Content-Type", JSON_CONTENT_TYPE)
                        .POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.GET();
            }
            request = builder.build();
        } catch (RuntimeException ignored) {
            return failed(new ProviderFailure(ProviderFailure.Code.REQUEST_BUILD_FAILED));
        }

        CancellableFuture<T> result = new CancellableFuture<>();
        AtomicReference<CompletableFuture<HttpResponse<InputStream>>> transportStage = new AtomicReference<>();
        result.onCancel(() -> {
            CompletableFuture<HttpResponse<InputStream>> stage = transportStage.get();
            if (stage != null) {
                stage.cancel(true);
            }
        });

        CompletableFuture<HttpResponse<InputStream>> stage;
        try {
            stage = transport.send(request);
        } catch (RuntimeException ignored) {
            result.completeExceptionally(new ProviderFailure(ProviderFailure.Code.NETWORK_FAILURE));
            return result;
        }
        if (stage == null) {
            result.completeExceptionally(new ProviderFailure(ProviderFailure.Code.NETWORK_FAILURE));
            return result;
        }
        transportStage.set(stage);
        if (result.isCancelled()) {
            stage.cancel(true);
        }
        stage.whenComplete((response, failure) -> {
            if (result.isCancelled()) {
                closeBody(response);
                return;
            }
            if (failure != null) {
                result.completeExceptionally(mapTransportFailure(failure));
                return;
            }
            try {
                result.complete(processResponse(request, response, responseDecoder));
            } catch (ProviderFailure safe) {
                result.completeExceptionally(safe);
            } catch (RuntimeException ignored) {
                result.completeExceptionally(new ProviderFailure(ProviderFailure.Code.INVALID_ENVELOPE));
            }
        });
        return result;
    }

    private <T> T processResponse(
            HttpRequest request,
            HttpResponse<InputStream> response,
            Decoder<T> responseDecoder) {
        if (response == null) {
            throw new ProviderFailure(ProviderFailure.Code.NETWORK_FAILURE);
        }
        boolean decoderOwnsBody = false;
        try {
            URI responseUri = response.uri();
            if (responseUri != null && !request.uri().equals(responseUri)) {
                throw new ProviderFailure(ProviderFailure.Code.REDIRECT_REJECTED);
            }
            int status = response.statusCode();
            HttpHeaders headers = response.headers();
            if (headers == null) {
                throw new ProviderFailure(ProviderFailure.Code.BODY_UNREADABLE);
            }
            if (status != 200) {
                throw new ProviderFailure(statusFailure(status), retryAfter(headers));
            }
            String encoding = responseEncoding(headers);
            if (encoding == null) {
                throw new ProviderFailure(ProviderFailure.Code.UNSUPPORTED_CONTENT_ENCODING);
            }
            InputStream body = response.body();
            if (body == null) {
                throw new ProviderFailure(ProviderFailure.Code.BODY_UNREADABLE);
            }
            // ProviderEnvelopeDecoder owns and closes this body on every path
            // after the optional decompressor has been constructed.
            InputStream decoded = GZIP_ENCODING.equals(encoding)
                    ? CompressedProviderBody.gzip(body)
                    : body;
            decoderOwnsBody = true;
            return responseDecoder.decode(new DecompressedBody(decoded));
        } catch (ProviderFailure safe) {
            if (!decoderOwnsBody) {
                closeBody(response);
            }
            throw safe;
        } catch (java.io.IOException ignored) {
            if (!decoderOwnsBody) {
                closeBody(response);
            }
            throw new ProviderFailure(ProviderFailure.Code.BODY_UNREADABLE);
        } catch (RuntimeException ignored) {
            if (!decoderOwnsBody) {
                closeBody(response);
            }
            throw new ProviderFailure(ProviderFailure.Code.INVALID_ENVELOPE);
        }
    }

    private ProviderModels decodeModels(DecompressedBody body) {
        return decoder.decodeModels(body);
    }

    private ProviderCompletion decodeCompletion(DecompressedBody body) {
        return decoder.decodeCompletion(body);
    }

    private URI endpoint(String relativePath) {
        String base = configuration.baseUrl().toString();
        return URI.create(base.endsWith("/") ? base + relativePath : base + "/" + relativePath);
    }

    private static ProviderFailure.Code statusFailure(int status) {
        return switch (status) {
            case 400 -> ProviderFailure.Code.HTTP_BAD_REQUEST;
            case 401 -> ProviderFailure.Code.HTTP_UNAUTHORIZED;
            case 403 -> ProviderFailure.Code.HTTP_FORBIDDEN;
            case 429 -> ProviderFailure.Code.HTTP_RATE_LIMITED;
            case 503 -> ProviderFailure.Code.HTTP_SERVICE_UNAVAILABLE;
            case 300, 301, 302, 303, 304, 305, 307, 308 -> ProviderFailure.Code.REDIRECT_REJECTED;
            default -> ProviderFailure.Code.HTTP_UNEXPECTED;
        };
    }

    private static String responseEncoding(HttpHeaders headers) {
        List<String> values = headers.allValues("Content-Encoding");
        if (values.isEmpty()) {
            return IDENTITY_ENCODING;
        }
        if (values.size() != 1 || values.get(0) == null) {
            return null;
        }
        String value = values.get(0).trim();
        if (IDENTITY_ENCODING.equalsIgnoreCase(value)) {
            return IDENTITY_ENCODING;
        }
        if (GZIP_ENCODING.equalsIgnoreCase(value)) {
            return GZIP_ENCODING;
        }
        return null;
    }

    private Duration retryAfter(HttpHeaders headers) {
        List<String> values = headers.allValues("Retry-After");
        if (values.size() != 1 || values.get(0) == null) {
            return null;
        }
        String value = values.get(0).trim();
        if (value.matches("[0-9]+")) {
            try {
                long seconds = Long.parseLong(value);
                return seconds <= 2 ? Duration.ofSeconds(seconds) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            Instant target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delta = Duration.between(clock.instant(), target);
            return !delta.isNegative() && delta.compareTo(Duration.ofSeconds(2)) <= 0 ? delta : null;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Duration remainingTimeout(Instant now, Instant deadline) {
        try {
            return Duration.between(now, deadline);
        } catch (ArithmeticException ignored) {
            return Duration.ZERO;
        }
    }

    private static Instant safeDeadline(Instant now, Duration timeout) {
        try {
            return now.plus(timeout);
        } catch (ArithmeticException ignored) {
            return Instant.MAX;
        }
    }

    private static ProviderFailure mapTransportFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        if (current instanceof CancellationException) {
            return new ProviderFailure(ProviderFailure.Code.CANCELLED);
        }
        if (current instanceof TimeoutException) {
            return new ProviderFailure(ProviderFailure.Code.TIMEOUT);
        }
        return new ProviderFailure(ProviderFailure.Code.NETWORK_FAILURE);
    }

    private static void closeBody(HttpResponse<InputStream> response) {
        if (response == null) {
            return;
        }
        closeBody(response.body());
    }

    private static void closeBody(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (Exception ignored) {
            // Close failures never replace or decorate the primary outcome.
        }
    }

    private static String completionJson(ProviderChatRequest request) {
        StringBuilder json = new StringBuilder(256 + request.systemMessage().length() + request.userDataMessage().length());
        json.append("{\"model\":");
        appendJsonString(json, request.model());
        json.append(",\"messages\":[{\"role\":\"system\",\"content\":");
        appendJsonString(json, request.systemMessage());
        json.append("},{\"role\":\"user\",\"content\":");
        appendJsonString(json, request.userDataMessage());
        json.append("}],\"max_tokens\":").append(request.maxOutputTokens())
                .append(",\"stream\":false}");
        return json.toString();
    }

    private static void appendJsonString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new IllegalArgumentException("Malformed text");
                        }
                        output.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new IllegalArgumentException("Malformed text");
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static <T> CompletableFuture<T> failed(ProviderFailure failure) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return result;
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(DecompressedBody body);
    }

    private static final class CancellableFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<Runnable> cancellation = new AtomicReference<>();

        void onCancel(Runnable action) {
            cancellation.set(Objects.requireNonNull(action, "action"));
            if (isCancelled()) {
                action.run();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                Runnable action = cancellation.get();
                if (action != null) {
                    action.run();
                }
            }
            return cancelled;
        }
    }
}
