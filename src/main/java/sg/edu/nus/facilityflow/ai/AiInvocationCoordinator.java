package sg.edu.nus.facilityflow.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;

/**
 * Shared admission control for AI invocations (AI-SEC-008/021/022).
 *
 * <p>This is deliberately a small coordinator seam. It owns the permit
 * lifecycle while provider adapters remain unaware of Accounts and role
 * context. Production wiring uses the PostgreSQL-backed lease/bucket adapter;
 * deterministic tests and explicitly supplied local instances use the
 * process-local rolling-window fallback.
 */
@Component
public final class AiInvocationCoordinator {
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private final int maxInFlight;
    private final int ratePerMinute;
    private final int ratePerHour;
    private final Clock clock;
    private final AiAdmissionBackend durableBackend;
    private final Object monitor = new Object();
    private final Map<UUID, Deque<Instant>> accountInvocations = new HashMap<>();
    private int inFlight;

    @Autowired
    AiInvocationCoordinator(
            AiConfiguration configuration, ObjectProvider<AiAdmissionBackend> durableBackend) {
        this(configuration instanceof AiConfiguration.Available available
                ? available.maxInFlight()
                : 0,
                configuration instanceof AiConfiguration.Available available
                        ? available.ratePerMinute()
                        : 0,
                configuration instanceof AiConfiguration.Available available
                        ? available.ratePerHour()
                        : 0,
                Clock.systemUTC(),
                durableBackend.getIfAvailable());
    }

    public AiInvocationCoordinator(AiConfiguration configuration) {
        this(configuration, null);
    }

    public AiInvocationCoordinator(int maxInFlight, int ratePerMinute, int ratePerHour, Clock clock) {
        this(maxInFlight, ratePerMinute, ratePerHour, clock, null);
    }

    private AiInvocationCoordinator(
            int maxInFlight,
            int ratePerMinute,
            int ratePerHour,
            Clock clock,
            AiAdmissionBackend durableBackend) {
        if (maxInFlight < 0 || ratePerMinute < 0 || ratePerHour < 0) {
            throw new IllegalArgumentException("Admission limits cannot be negative");
        }
        this.maxInFlight = maxInFlight;
        this.ratePerMinute = ratePerMinute;
        this.ratePerHour = ratePerHour;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.durableBackend = durableBackend;
    }

    /** Attempts one logical invocation admission for an account. */
    public Optional<Permit> tryAcquire(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        if (durableBackend != null) {
            return durableBackend.tryAcquire(accountId)
                    .map(lease -> new Permit(lease));
        }
        Instant now = clock.instant();
        synchronized (monitor) {
            if (inFlight >= maxInFlight) {
                return Optional.empty();
            }
            Deque<Instant> calls = accountInvocations.computeIfAbsent(accountId, ignored -> new ArrayDeque<>());
            prune(calls, now);
            if (countSince(calls, now.minus(MINUTE)) >= ratePerMinute
                    || calls.size() >= ratePerHour) {
                return Optional.empty();
            }
            calls.addLast(now);
            inFlight++;
            return Optional.of(new Permit(accountId));
        }
    }

    int inFlightForTests() {
        synchronized (monitor) {
            return inFlight;
        }
    }

    private void prune(Deque<Instant> calls, Instant now) {
        Instant cutoff = now.minus(HOUR);
        while (!calls.isEmpty() && calls.peekFirst().isBefore(cutoff)) {
            calls.removeFirst();
        }
    }

    private static int countSince(Deque<Instant> calls, Instant cutoff) {
        int count = 0;
        for (Instant call : calls) {
            if (!call.isBefore(cutoff)) {
                count++;
            }
        }
        return count;
    }

    /** Idempotent permit that releases the active local or durable admission. */
    public final class Permit implements AutoCloseable {
        private final UUID accountId;
        private final AiAdmissionBackend.Lease lease;
        private boolean released;

        private Permit(UUID accountId) {
            this.accountId = accountId;
            this.lease = null;
        }

        private Permit(AiAdmissionBackend.Lease lease) {
            this.accountId = null;
            this.lease = Objects.requireNonNull(lease, "lease");
        }

        public void release() {
            if (lease != null) {
                synchronized (monitor) {
                    if (released) {
                        return;
                    }
                    released = true;
                }
                lease.release();
                return;
            }
            synchronized (monitor) {
                if (released) {
                    return;
                }
                released = true;
                inFlight--;
                if (inFlight < 0) {
                    throw new IllegalStateException("Admission permit released without acquisition");
                }
                Deque<Instant> calls = accountInvocations.get(accountId);
                if (calls != null && calls.isEmpty()) {
                    accountInvocations.remove(accountId);
                }
            }
        }

        /** Returns false once a durable fenced lease has been lost. */
        public boolean valid() {
            return !released && (lease == null || lease.valid());
        }

        @Override
        public void close() {
            release();
        }
    }
}
