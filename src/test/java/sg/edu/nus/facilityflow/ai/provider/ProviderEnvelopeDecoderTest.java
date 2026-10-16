package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderUsage;

class ProviderEnvelopeDecoderTest {
    private static final String CANARY = "sensitive-provider-canary";
    private final ProviderEnvelopeDecoder decoder = new ProviderEnvelopeDecoder();

    @Test
    void reqAi006AndAiSec020DecodeValidModelsWithPermittedInertMetadata() {
        String json = """
                {"object":{"ignored":true},"data":[
                  {"id":"model-a","object":[1],"created":false,"owned_by":null},
                  {"id":"model-b"}
                ]}
                """;

        ProviderModels result = decodeModels(json);

        assertEquals(Set.of("model-a", "model-b"), result.modelIds());
        assertEquals("ProviderModels[count=2]", result.toString());
    }

    @Test
    void reqAi002AndAiSec010DecodeOneStoppedAssistantCompletionAndUsage() {
        String json = """
                {"id":null,"object":{"ignored":true},"created":[],"model":false,
                 "system_fingerprint":{},"service_tier":123,
                 "choices":[{"index":{},"message":{"role":"assistant","content":"safe answer"},
                 "finish_reason":"stop","logprobs":[1]}],
                 "usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}}
                """;

        ProviderCompletion result = decodeCompletion(json);

        assertEquals("safe answer", result.content());
        assertEquals(Optional.of(new ProviderUsage(2L, 3L, 5L)), result.usage());
        assertFalse(result.toString().contains("safe answer"));
    }

    @Test
    void aiSec010AcceptsExactly256KiBPostDecompression() {
        byte[] body = exactSizeValidModelsBody(ProviderEnvelopeDecoder.MAX_BODY_BYTES);

        ProviderModels result = decoder.decodeModels(DecompressedBody.of(body));

        assertEquals(Set.of(), result.modelIds());
    }

    @Test
    void aiSec010RejectsByte262145BeforeJsonParsing() {
        byte[] body = exactSizeValidModelsBody(ProviderEnvelopeDecoder.MAX_BODY_BYTES + 1);

        ProviderFailure failure = assertThrows(
                ProviderFailure.class, () -> decoder.decodeModels(DecompressedBody.of(body)));

        assertEquals(ProviderFailure.Code.BODY_TOO_LARGE, failure.code());
    }

    @Test
    void aiSec010StopsReadingAtTheFirstOverLimitByteAndClosesTheStream() {
        CountingInputStream input = new CountingInputStream(ProviderEnvelopeDecoder.MAX_BODY_BYTES + 50, false);

        ProviderFailure failure = assertThrows(
                ProviderFailure.class,
                () -> decoder.decodeModels(new DecompressedBody(input)));

        assertEquals(ProviderFailure.Code.BODY_TOO_LARGE, failure.code());
        assertEquals(ProviderEnvelopeDecoder.MAX_BODY_BYTES + 1, input.bytesRead);
        assertTrue(input.closed);
    }

    @Test
    void aiSec010RejectsMalformedUtf8AndClosesTheStream() {
        byte[] invalid = new byte[] {'{', '"', 'd', 'a', 't', 'a', '"', ':', '[', ']', ',', (byte) 0xC3, '}'};
        TrackingInputStream input = new TrackingInputStream(invalid);

        ProviderFailure failure = assertThrows(
                ProviderFailure.class,
                () -> decoder.decodeModels(new DecompressedBody(input)));

        assertEquals(ProviderFailure.Code.INVALID_UTF8, failure.code());
        assertTrue(input.closed);
    }

    @ParameterizedTest
    @MethodSource("malformedJsonBodies")
    void aiSec010RejectsDuplicateTrailingAndStructurallyOverLimitJson(String json) {
        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeModels(json));

        assertEquals(ProviderFailure.Code.MALFORMED_JSON, failure.code());
    }

    private static Stream<String> malformedJsonBodies() {
        String tooDeep = "{\"object\":" + "[".repeat(64) + "0" + "]".repeat(64) + ",\"data\":[]}";
        String tokenLimit = tokenBoundModelsJson(8_185);
        String numberTooLong = "{\"data\":[{\"id\":\"m\",\"created\":123456789012345678901}]}";
        return Stream.of(
                "{\"data\":[],\"data\":[]}",
                "{\"data\":[]}{}",
                tooDeep,
                tokenLimit,
                numberTooLong);
    }

    @Test
    void aiSec010AcceptsThe8192TokenAnd20DigitNumberBoundaries() {
        ProviderModels tokenBoundary = decodeModels(tokenBoundModelsJson(8_184));
        ProviderModels numberBoundary = decodeModels(
                "{\"data\":[{\"id\":\"m\",\"created\":12345678901234567890}]}");

        assertEquals(Set.of(), tokenBoundary.modelIds());
        assertEquals(Set.of("m"), numberBoundary.modelIds());
    }

    @Test
    void aiSec010AcceptsExactly64LevelsOfJsonNesting() {
        String json = "{\"object\":" + "[".repeat(63) + "0" + "]".repeat(63) + ",\"data\":[]}";

        ProviderModels result = decodeModels(json);

        assertEquals(Set.of(), result.modelIds());
    }

    @Test
    void aiSec010EffectiveBodyBoundRejectsAJsonStringAboveTheConfiguredStringLimit() {
        String json = "{\"object\":\"" + "x".repeat(262_145) + "\",\"data\":[]}";

        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeModels(json));

        assertEquals(ProviderFailure.Code.BODY_TOO_LARGE, failure.code());
    }

    @Test
    void aiSec010Accepts1000ModelEntries() {
        ProviderModels result = decodeModels(modelsJson(1_000));

        assertEquals(1_000, result.modelIds().size());
    }

    @Test
    void aiSec010Rejects1001ModelEntries() {
        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeModels(modelsJson(1_001)));

        assertEquals(ProviderFailure.Code.INVALID_ENVELOPE, failure.code());
    }

    @ParameterizedTest
    @MethodSource("invalidModelEnvelopes")
    void aiSec010RejectsMalformedModelEnvelopes(String json) {
        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeModels(json));

        assertEquals(ProviderFailure.Code.INVALID_ENVELOPE, failure.code());
    }

    private static Stream<String> invalidModelEnvelopes() {
        return Stream.of(
                "[]",
                "{}",
                "{\"data\":null}",
                "{\"data\":{}}",
                "{\"data\":[null]}",
                "{\"data\":[[]]}",
                "{\"data\":[{}]}",
                "{\"data\":[{\"id\":null}]}",
                "{\"data\":[{\"id\":1}]}",
                "{\"data\":[{\"id\":\"\"}]}",
                "{\"data\":[{\"id\":\"same\"},{\"id\":\"same\"}]}",
                "{\"data\":[],\"error\":{}}",
                "{\"data\":[],\"unknown\":true}",
                "{\"data\":[{\"id\":\"m\",\"unknown\":true}]}");
    }

    @ParameterizedTest
    @MethodSource("invalidCompletionEnvelopes")
    void reqAi002AndAiSec010RejectInvalidCompletionShapes(String json) {
        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeCompletion(json));

        assertEquals(ProviderFailure.Code.INVALID_ENVELOPE, failure.code());
    }

    private static Stream<String> invalidCompletionEnvelopes() {
        return Stream.of(
                "[]",
                "{}",
                "{\"choices\":null}",
                "{\"choices\":[]}",
                "{\"choices\":[" + choice("assistant", "a", "stop") + "," + choice("assistant", "b", "stop") + "]}",
                "{\"choices\":[null]}",
                "{\"choices\":[{}]}",
                completionWithChoice("{\"message\":null,\"finish_reason\":\"stop\"}"),
                completionWithChoice("{\"message\":{\"role\":\"user\",\"content\":\"x\"},\"finish_reason\":\"stop\"}"),
                completionWithChoice("{\"message\":{\"role\":\"assistant\"},\"finish_reason\":\"stop\"}"),
                completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":null},\"finish_reason\":\"stop\"}"),
                completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":1},\"finish_reason\":\"stop\"}"),
                completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":\"stop\"}"),
                completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":\"x\"},\"finish_reason\":\"length\"}"),
                completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":\"x\"}}"),
                "{\"choices\":[" + choice("assistant", "x", "stop") + "],\"error\":{}}",
                "{\"choices\":[" + choice("assistant", "x", "stop") + "],\"unknown\":true}",
                completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":\"x\"},\"finish_reason\":\"stop\",\"unknown\":true}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refusal", "audio", "name", "tool_calls", "function_call", "unknown"})
    void aiSec005RejectsEveryUnlistedMessageMemberEvenWhenNull(String member) {
        String json = completionWithChoice("{\"message\":{\"role\":\"assistant\",\"content\":\"x\",\""
                + member + "\":null},\"finish_reason\":\"stop\"}");

        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeCompletion(json));

        assertEquals(ProviderFailure.Code.INVALID_ENVELOPE, failure.code());
    }

    @Test
    void aiSec010AllowsUsageToBeAbsent() {
        ProviderCompletion result = decodeCompletion("{\"choices\":[" + choice("assistant", "x", "stop") + "]}");

        assertEquals(Optional.empty(), result.usage());
    }

    @ParameterizedTest
    @MethodSource("validUsage")
    void aiSec010AcceptsPartialAndZeroUsage(String usage, ProviderUsage expected) {
        ProviderCompletion result = decodeCompletion(completionWithUsage(usage));

        assertEquals(Optional.of(expected), result.usage());
    }

    private static Stream<Arguments> validUsage() {
        return Stream.of(
                Arguments.of("{\"prompt_tokens\":0}", new ProviderUsage(0L, null, null)),
                Arguments.of("{\"completion_tokens\":2}", new ProviderUsage(null, 2L, null)),
                Arguments.of("{\"total_tokens\":3}", new ProviderUsage(null, null, 3L)),
                Arguments.of("{\"prompt_tokens\":1,\"total_tokens\":3}", new ProviderUsage(1L, null, 3L)),
                Arguments.of(
                        "{\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}",
                        new ProviderUsage(0L, 0L, 0L)));
    }

    @ParameterizedTest
    @MethodSource("invalidUsage")
    void aiSec010RejectsNullEmptyAliasedAndInvalidUsage(String usage) {
        ProviderFailure failure = assertThrows(
                ProviderFailure.class, () -> decodeCompletion(completionWithUsage(usage)));

        assertEquals(ProviderFailure.Code.INVALID_ENVELOPE, failure.code());
    }

    private static Stream<String> invalidUsage() {
        return Stream.of(
                "null",
                "{}",
                "{\"input_tokens\":1}",
                "{\"prompt_tokens\":null}",
                "{\"prompt_tokens\":-1}",
                "{\"prompt_tokens\":1.5}",
                "{\"prompt_tokens\":9223372036854775808}",
                "{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":6}",
                "{\"prompt_tokens\":9223372036854775807,\"completion_tokens\":1,\"total_tokens\":0}");
    }

    @Test
    void aiSec016ClosesUnreadableBodyAndDoesNotRetainDiagnosticCanary() {
        FailingInputStream input = new FailingInputStream();

        ProviderFailure failure = assertThrows(
                ProviderFailure.class,
                () -> decoder.decodeModels(new DecompressedBody(input)));

        assertEquals(ProviderFailure.Code.BODY_UNREADABLE, failure.code());
        assertTrue(input.closed);
        assertNull(failure.getCause());
        assertDiagnosticChainExcludes(failure, CANARY);
    }

    @Test
    void aiSec016MalformedProviderContentNeverAppearsInExceptionDiagnostics() {
        String body = "{\"data\":[{\"id\":\"" + CANARY + "\"}], trailing-" + CANARY;

        ProviderFailure failure = assertThrows(ProviderFailure.class, () -> decodeModels(body));

        assertDiagnosticChainExcludes(failure, CANARY, body, "line", "column");
    }

    @Test
    void aiSec016UncheckedReadFailureMapsToBodyUnreadableWithoutCanaryOrCause() {
        UncheckedFailingInputStream input = new UncheckedFailingInputStream();

        ProviderFailure failure = assertThrows(
                ProviderFailure.class,
                () -> decoder.decodeModels(new DecompressedBody(input)));

        assertEquals(ProviderFailure.Code.BODY_UNREADABLE, failure.code());
        assertTrue(input.closed);
        assertNull(failure.getCause());
        assertDiagnosticChainExcludes(failure, CANARY);
    }

    @Test
    void aiSec016SafeProviderFailureThrownByBodyReadRetainsItsClosedCode() {
        SafeProviderFailureInputStream input = new SafeProviderFailureInputStream();

        ProviderFailure failure = assertThrows(
                ProviderFailure.class,
                () -> decoder.decodeModels(new DecompressedBody(input)));

        assertEquals(ProviderFailure.Code.BODY_TOO_LARGE, failure.code());
        assertTrue(input.closed);
        assertNull(failure.getCause());
    }

    @Test
    void aiSec016UncheckedCloseFailureCannotReplaceSuccessfulDecode() {
        UncheckedCloseFailingInputStream input = new UncheckedCloseFailingInputStream(
                "{\"data\":[]}".getBytes(StandardCharsets.UTF_8));

        ProviderModels result = decoder.decodeModels(new DecompressedBody(input));

        assertEquals(Set.of(), result.modelIds());
        assertTrue(input.closeAttempted);
    }

    @Test
    void aiSec016UncheckedCloseFailureCannotReplacePrimaryDecoderFailureOrLeakCanary() {
        UncheckedCloseFailingInputStream input = new UncheckedCloseFailingInputStream(
                new byte[] {(byte) 0xC3});

        ProviderFailure failure = assertThrows(
                ProviderFailure.class,
                () -> decoder.decodeModels(new DecompressedBody(input)));

        assertEquals(ProviderFailure.Code.INVALID_UTF8, failure.code());
        assertTrue(input.closeAttempted);
        assertDiagnosticChainExcludes(failure, CANARY);
    }

    private ProviderModels decodeModels(String json) {
        return decoder.decodeModels(DecompressedBody.of(json.getBytes(StandardCharsets.UTF_8)));
    }

    private ProviderCompletion decodeCompletion(String json) {
        return decoder.decodeCompletion(DecompressedBody.of(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] exactSizeValidModelsBody(int size) {
        byte[] prefix = "{\"object\":\"".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "\",\"data\":[]}".getBytes(StandardCharsets.UTF_8);
        int padding = size - prefix.length - suffix.length;
        if (padding < 0) {
            throw new AssertionError("Requested body too small");
        }
        return (new String(prefix, StandardCharsets.UTF_8)
                        + "x".repeat(padding)
                        + new String(suffix, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String tokenBoundModelsJson(int inertNumbers) {
        String values = IntStream.range(0, inertNumbers).mapToObj(ignored -> "0").reduce((a, b) -> a + "," + b).orElse("");
        return "{\"object\":[" + values + "],\"data\":[]}";
    }

    private static String modelsJson(int count) {
        String entries = IntStream.range(0, count)
                .mapToObj(index -> "{\"id\":\"m" + index + "\"}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return "{\"data\":[" + entries + "]}";
    }

    private static String choice(String role, String content, String finishReason) {
        return "{\"message\":{\"role\":\"" + role + "\",\"content\":\"" + content
                + "\"},\"finish_reason\":\"" + finishReason + "\"}";
    }

    private static String completionWithChoice(String choice) {
        return "{\"choices\":[" + choice + "]}";
    }

    private static String completionWithUsage(String usage) {
        return "{\"choices\":[" + choice("assistant", "x", "stop") + "],\"usage\":" + usage + "}";
    }

    private static void assertDiagnosticChainExcludes(Throwable failure, String... forbidden) {
        Throwable current = failure;
        while (current != null) {
            String diagnostics = current.getClass().getName() + " " + current.getMessage() + " " + current;
            for (String value : forbidden) {
                assertFalse(diagnostics.contains(value), "Diagnostic leaked forbidden provider content: " + value);
            }
            current = current.getCause();
        }
    }

    private static class TrackingInputStream extends InputStream {
        private final byte[] bytes;
        private int index;
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() {
            return index == bytes.length ? -1 : Byte.toUnsignedInt(bytes[index++]);
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (index == bytes.length) {
                return -1;
            }
            int count = Math.min(length, bytes.length - index);
            System.arraycopy(bytes, index, target, offset, count);
            index += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CountingInputStream extends InputStream {
        private final int size;
        private final boolean returnZero;
        private int bytesRead;
        private boolean closed;

        private CountingInputStream(int size, boolean returnZero) {
            this.size = size;
            this.returnZero = returnZero;
        }

        @Override
        public int read() {
            if (bytesRead == size) {
                return -1;
            }
            bytesRead++;
            return 'x';
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (returnZero) {
                return 0;
            }
            if (bytesRead == size) {
                return -1;
            }
            int count = Math.min(length, size - bytesRead);
            for (int index = 0; index < count; index++) {
                target[offset + index] = 'x';
            }
            bytesRead += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FailingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException(CANARY);
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            throw new IOException(CANARY);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            throw new IOException("close-" + CANARY);
        }
    }

    private static final class UncheckedFailingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() {
            throw new IllegalStateException(CANARY);
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            throw new IllegalStateException(CANARY);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class SafeProviderFailureInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() {
            throw new ProviderFailure(ProviderFailure.Code.BODY_TOO_LARGE);
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            throw new ProviderFailure(ProviderFailure.Code.BODY_TOO_LARGE);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class UncheckedCloseFailingInputStream extends TrackingInputStream {
        private boolean closeAttempted;

        private UncheckedCloseFailingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closeAttempted = true;
            throw new IllegalStateException("close-" + CANARY);
        }
    }
}
