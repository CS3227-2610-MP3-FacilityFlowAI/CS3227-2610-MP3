package sg.edu.nus.facilityflow.ai.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderUsage;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict bounded decoder shared by production and fake provider adapters. */
final class ProviderEnvelopeDecoder {
    static final int MAX_BODY_BYTES = 262_144;
    static final int MAX_JSON_TOKENS = 8_192;
    static final int MAX_MODELS = 1_000;

    private static final Set<String> MODELS_ROOT_FIELDS = Set.of("object", "data");
    private static final Set<String> MODEL_FIELDS = Set.of(
            "id",
            "object",
            "created",
            "owned_by",
            "context_length",
            "context_window",
            "max_context_length",
            "max_model_len",
            "soclaas");
    private static final Set<String> CHAT_ROOT_FIELDS = Set.of(
            "id", "object", "created", "model", "system_fingerprint", "service_tier", "choices", "usage",
            "prompt_logprobs", "prompt_token_ids", "prompt_text", "kv_transfer_params", "ec_transfer_params",
            "metrics");
    private static final Set<String> CHOICE_FIELDS = Set.of(
            "index", "message", "finish_reason", "logprobs", "stop_reason", "token_ids", "routed_experts");
    private static final Set<String> MESSAGE_FIELDS = Set.of(
            "role", "content", "refusal", "annotations", "audio", "function_call", "reasoning");
    private static final Set<String> USAGE_FIELDS = Set.of(
            "prompt_tokens", "completion_tokens", "total_tokens", "prompt_tokens_details");

    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(64)
                            .maxStringLength(262_144)
                            .maxNumberLength(20)
                            .maxTokenCount(MAX_JSON_TOKENS)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    ProviderModels decodeModels(DecompressedBody body) {
        JsonNode root = parse(body);
        requireObjectWithFields(root, MODELS_ROOT_FIELDS);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.size() > MAX_MODELS) {
            throw invalidEnvelope();
        }

        Set<String> modelIds = new HashSet<>();
        for (JsonNode entry : data) {
            requireObjectWithFields(entry, MODEL_FIELDS);
            JsonNode id = entry.get("id");
            if (id == null || !id.isString()) {
                throw invalidEnvelope();
            }
            String modelId = id.stringValue();
            if (modelId.isEmpty() || !modelIds.add(modelId)) {
                throw invalidEnvelope();
            }
        }
        return new ProviderModels(modelIds);
    }

    ProviderCompletion decodeCompletion(DecompressedBody body) {
        JsonNode root = parse(body);
        requireObjectWithFields(root, CHAT_ROOT_FIELDS);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() != 1) {
            throw invalidEnvelope();
        }

        JsonNode choice = choices.get(0);
        requireObjectWithFields(choice, CHOICE_FIELDS);
        JsonNode finishReason = choice.get("finish_reason");
        if (finishReason == null || !finishReason.isString() || !"stop".equals(finishReason.stringValue())) {
            throw invalidEnvelope();
        }

        JsonNode message = choice.get("message");
        requireObjectWithFields(message, MESSAGE_FIELDS);
        JsonNode role = message.get("role");
        JsonNode content = message.get("content");
        if (role == null || !role.isString() || !"assistant".equals(role.stringValue())
                || content == null || !content.isString() || content.stringValue().isEmpty()) {
            throw invalidEnvelope();
        }

        Optional<ProviderUsage> usage = root.has("usage")
                ? Optional.of(decodeUsage(root.get("usage")))
                : Optional.empty();
        return new ProviderCompletion(content.stringValue(), usage);
    }

    private static ProviderUsage decodeUsage(JsonNode usage) {
        requireObjectWithFields(usage, USAGE_FIELDS);
        if (usage.isEmpty()) {
            throw invalidEnvelope();
        }
        Long prompt = optionalCount(usage, "prompt_tokens");
        Long completion = optionalCount(usage, "completion_tokens");
        Long total = optionalCount(usage, "total_tokens");
        try {
            return new ProviderUsage(prompt, completion, total);
        } catch (IllegalArgumentException ignored) {
            throw invalidEnvelope();
        }
    }

    private static Long optionalCount(JsonNode usage, String field) {
        JsonNode value = usage.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            if (value == null) {
                return null;
            }
            throw invalidEnvelope();
        }
        long count = value.longValue();
        if (count < 0) {
            throw invalidEnvelope();
        }
        return count;
    }

    private static JsonNode parse(DecompressedBody body) {
        if (body == null) {
            throw new ProviderFailure(ProviderFailure.Code.BODY_UNREADABLE);
        }
        byte[] bytes;
        try {
            bytes = readBounded(body);
        } catch (ProviderFailure safe) {
            throw safe;
        } catch (IOException ignored) {
            throw new ProviderFailure(ProviderFailure.Code.BODY_UNREADABLE);
        } catch (RuntimeException ignored) {
            throw new ProviderFailure(ProviderFailure.Code.BODY_UNREADABLE);
        } finally {
            try {
                body.close();
            } catch (IOException | RuntimeException ignored) {
                // The body is closed on every path; close diagnostics are deliberately suppressed.
            }
        }

        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            throw new ProviderFailure(ProviderFailure.Code.INVALID_UTF8);
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null) {
                throw invalidEnvelope();
            }
            return root;
        } catch (ProviderFailure safe) {
            throw safe;
        } catch (RuntimeException ignored) {
            throw new ProviderFailure(ProviderFailure.Code.MALFORMED_JSON);
        }
    }

    private static byte[] readBounded(DecompressedBody body) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_BODY_BYTES, 8_192));
        byte[] buffer = new byte[8_192];
        int total = 0;
        while (true) {
            int allowed = Math.min(buffer.length, MAX_BODY_BYTES + 1 - total);
            int read = body.read(buffer, 0, allowed);
            if (read == -1) {
                return output.toByteArray();
            }
            if (read == 0) {
                int single = body.read();
                if (single == -1) {
                    return output.toByteArray();
                }
                total++;
                if (total > MAX_BODY_BYTES) {
                    throw new ProviderFailure(ProviderFailure.Code.BODY_TOO_LARGE);
                }
                output.write(single);
                continue;
            }
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw new ProviderFailure(ProviderFailure.Code.BODY_TOO_LARGE);
            }
            output.write(buffer, 0, read);
        }
    }

    private static void requireObjectWithFields(JsonNode node, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw invalidEnvelope();
        }
        for (String field : node.propertyNames()) {
            if (!allowedFields.contains(field)) {
                throw invalidEnvelope();
            }
        }
    }

    private static ProviderFailure invalidEnvelope() {
        return new ProviderFailure(ProviderFailure.Code.INVALID_ENVELOPE);
    }
}
