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
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;

/**
 * Shared process-local admission control for AI invocations.
 *
 * <p>This is deliberately a small coordinator seam. It owns the concurrency
 * permit and rolling account windows, while provider adapters remain unaware of
 * accounts and role context. The durable PostgreSQL lease/bucket coordinator is
 * the next increment for multi-instance deployments.
 */
@Component
public final class AiInvocationCoordinator {
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private final int maxInFlight;
    private final int ratePerMinute;
    private final int ratePerHour;
    private final Clock clock;
    private final Object monitor = new Object();
    private final Map<UUID, Deque<Instant>> accountInvocations = new HashMap<>();
    private int inFlight;

    @Autowired
    public AiInvocationCoordinator(AiConfiguration configuration) {
        this(configuration instanceof AiConfiguration.Available available
                ? available.maxInFlight()
                : 0,
                configuration instanceof AiConfiguration.Available available
                        ? available.ratePerMinute()
                        : 0,
                configuration instanceof AiConfiguration.Available available
                        ? available.ratePerHour()
                        : 0,
                Clock.systemUTC());
    }

    public AiInvocationCoordinator(int maxInFlight, int ratePerMinute, int ratePerHour, Clock clock) {
        if (maxInFlight < 0 || ratePerMinute < 0 || ratePerHour < 0) {
            throw new IllegalArgumentException("Admission limits cannot be negative");
        }
        this.maxInFlight = maxInFlight;
        this.ratePerMinute = ratePerMinute;
        this.ratePerHour = ratePerHour;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Attempts one logical invocation admission for an account. */
    public Optional<Permit> tryAcquire(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
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

    /** Idempotent permit that releases only the process-local concurrency slot. */
    public final class Permit implements AutoCloseable {
        private final UUID accountId;
        private boolean released;

        private Permit(UUID accountId) {
            this.accountId = accountId;
        }

        public void release() {
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

        @Override
        public void close() {
            release();
        }
    }
}
