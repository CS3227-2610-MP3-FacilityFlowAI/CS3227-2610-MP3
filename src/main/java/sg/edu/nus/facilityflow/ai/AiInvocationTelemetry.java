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

    /** Mutable server-side handle used to carry attempt and usage metadata across async seams. */
    final class Invocation {
        private final UUID id;
        private final Instant startedAt;
        private int retryCount;
        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;

        public Invocation(UUID id, Instant startedAt) {
            this.id = Objects.requireNonNull(id, "id");
            this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        }

        public UUID id() { return id; }

        public Instant startedAt() { return startedAt; }

        public synchronized void recordRetry() {
            if (retryCount >= 1) {
                throw new IllegalStateException("Only one provider retry is permitted");
            }
            retryCount++;
        }

        public synchronized int retryCount() { return retryCount; }

        public synchronized void recordUsage(Long inputTokens, Long outputTokens, Long totalTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
        }

        public synchronized Long inputTokens() { return inputTokens; }

        public synchronized Long outputTokens() { return outputTokens; }

        public synchronized Long totalTokens() { return totalTokens; }
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

    /** Binds one invocation to the synchronous provider-dispatch seam. */
    static Scope bind(Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        Invocation previous = Holder.ACTIVE.get();
        Holder.ACTIVE.set(invocation);
        return () -> {
            if (previous == null) {
                Holder.ACTIVE.remove();
            } else {
                Holder.ACTIVE.set(previous);
            }
        };
    }

    /** Returns the invocation bound by the current server-side request thread. */
    static Invocation current() {
        return Holder.ACTIVE.get();
    }

    interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    final class Holder {
        private static final ThreadLocal<Invocation> ACTIVE = new ThreadLocal<>();

        private Holder() {}
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
