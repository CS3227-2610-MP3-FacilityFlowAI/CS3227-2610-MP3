package sg.edu.nus.facilityflow.ai;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable lifecycle telemetry seam for one admitted logical AI invocation. */
public interface AiInvocationTelemetry {
    Invocation start(StartRequest request);

    void finish(Invocation invocation, Terminal terminal);

    record StartRequest(UUID actorId, UUID correlationId, Feature feature, String model, String templateVersion) {
        public StartRequest {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(feature, "feature");
            Objects.requireNonNull(templateVersion, "templateVersion");
            if (templateVersion.isBlank()) {
                throw new IllegalArgumentException("templateVersion cannot be blank");
            }
        }
    }

    record Invocation(UUID id, Instant startedAt) {
        public Invocation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(startedAt, "startedAt");
        }
    }

    record Terminal(Status status, long latencyMs, int retryCount,
                   Long inputTokens, Long outputTokens, Long totalTokens) {
        public Terminal {
            Objects.requireNonNull(status, "status");
            if (latencyMs < 0) {
                throw new IllegalArgumentException("latencyMs cannot be negative");
            }
            if (retryCount < 0 || retryCount > 1) {
                throw new IllegalArgumentException("retryCount must be between 0 and 1");
            }
            validateTokens(inputTokens, "inputTokens");
            validateTokens(outputTokens, "outputTokens");
            validateTokens(totalTokens, "totalTokens");
        }

        private static void validateTokens(Long value, String field) {
            if (value != null && value < 0) {
                throw new IllegalArgumentException(field + " cannot be negative");
            }
        }
    }

    enum Feature { SMART_REPORT, WORK_PLAN, TRIAGE }

    enum Status {
        SUCCEEDED, AI_UNAVAILABLE, AI_LIMITED, AI_INVALID_REQUEST,
        AI_CONFIGURATION_ERROR, AI_TIMEOUT, AI_INVALID_OUTPUT, DISCARDED_ACCESS_CHANGED
    }

    /** No-op adapter used by direct feature tests that do not construct a database. */
    static AiInvocationTelemetry noop() {
        return new AiInvocationTelemetry() {
            @Override
            public Invocation start(StartRequest request) {
                Objects.requireNonNull(request, "request");
                return new Invocation(UUID.randomUUID(), Instant.now());
            }

            @Override
            public void finish(Invocation invocation, Terminal terminal) {
                Objects.requireNonNull(invocation, "invocation");
                Objects.requireNonNull(terminal, "terminal");
            }
        };
    }
}
