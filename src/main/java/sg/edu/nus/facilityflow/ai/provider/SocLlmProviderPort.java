package sg.edu.nus.facilityflow.ai.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Provider-only asynchronous seam used by the SoCLaaS core.
 *
 * <p>Role modules must use {@link SocLlmProviderCore}; they must never call this
 * port directly (REQ-AI-005, AI-SEC-005).
 */
interface SocLlmProviderPort {

    CompletableFuture<ProviderModels> listModels();

    CompletableFuture<ProviderCompletion> complete(ProviderChatRequest request);

    /** A fully decoded model-discovery response. */
    final class ProviderModels {
        private final Set<String> modelIds;

        ProviderModels(Set<String> modelIds) {
            this.modelIds = Set.copyOf(Objects.requireNonNull(modelIds, "modelIds"));
            if (this.modelIds.size() > 1_000
                    || this.modelIds.stream().anyMatch(id -> id == null || id.isEmpty())) {
                throw new IllegalArgumentException("Model identifiers must be non-empty.");
            }
        }

        Set<String> modelIds() {
            return modelIds;
        }

        @Override
        public String toString() {
            return "ProviderModels[count=" + modelIds.size() + "]";
        }
    }

    /**
     * Provider request assembled by the core. Model and output limits never come
     * from the feature caller.
     */
    final class ProviderChatRequest {
        private final String systemMessage;
        private final String userDataMessage;
        private final String model;
        private final int maxOutputTokens;
        private final Instant absoluteDeadline;

        ProviderChatRequest(
                String systemMessage,
                String userDataMessage,
                String model,
                int maxOutputTokens,
                Instant absoluteDeadline) {
            this.systemMessage = requireMessage(systemMessage);
            this.userDataMessage = requireMessage(userDataMessage);
            this.model = Objects.requireNonNull(model, "model");
            this.absoluteDeadline = Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
            if (model.isEmpty() || maxOutputTokens <= 0) {
                throw new IllegalArgumentException("Provider request configuration is invalid.");
            }
            this.maxOutputTokens = maxOutputTokens;
        }

        String systemMessage() {
            return systemMessage;
        }

        String userDataMessage() {
            return userDataMessage;
        }

        String model() {
            return model;
        }

        int maxOutputTokens() {
            return maxOutputTokens;
        }

        Instant absoluteDeadline() {
            return absoluteDeadline;
        }

        @Override
        public String toString() {
            return "ProviderChatRequest[systemMessage=[REDACTED], userDataMessage=[REDACTED], "
                    + "model=[PRESENT], maxOutputTokens=" + maxOutputTokens
                    + ", absoluteDeadline=[PRESENT]]";
        }
    }

    /** A strictly decoded completion response. */
    final class ProviderCompletion {
        private final String content;
        private final Optional<ProviderUsage> usage;

        ProviderCompletion(String content, Optional<ProviderUsage> usage) {
            this.content = requireMessage(content);
            this.usage = Objects.requireNonNull(usage, "usage");
        }

        String content() {
            return content;
        }

        Optional<ProviderUsage> usage() {
            return usage;
        }

        @Override
        public String toString() {
            return "ProviderCompletion[content=[REDACTED], usage="
                    + (usage.isPresent() ? "[PRESENT]" : "[ABSENT]") + "]";
        }
    }

    /** Optional token counts supplied by the provider. Null means not supplied. */
    record ProviderUsage(Long promptTokens, Long completionTokens, Long totalTokens) {
        public ProviderUsage {
            if (promptTokens == null && completionTokens == null && totalTokens == null) {
                throw new IllegalArgumentException("At least one usage value is required.");
            }
            requireNonNegative(promptTokens);
            requireNonNegative(completionTokens);
            requireNonNegative(totalTokens);
            if (promptTokens != null && completionTokens != null && totalTokens != null) {
                long expected;
                try {
                    expected = Math.addExact(promptTokens, completionTokens);
                } catch (ArithmeticException ignored) {
                    throw new IllegalArgumentException("Usage values are inconsistent.");
                }
                if (expected != totalTokens) {
                    throw new IllegalArgumentException("Usage values are inconsistent.");
                }
            }
        }

        private static void requireNonNegative(Long value) {
            if (value != null && value < 0) {
                throw new IllegalArgumentException("Usage values must be non-negative.");
            }
        }
    }

    private static String requireMessage(String value) {
        Objects.requireNonNull(value, "message");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Messages must be non-empty.");
        }
        return value;
    }
}
