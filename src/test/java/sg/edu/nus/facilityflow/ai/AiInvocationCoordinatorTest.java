package sg.edu.nus.facilityflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiInvocationCoordinatorTest {
    private static final UUID ACCOUNT = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ACCOUNT = UUID.fromString("21000000-0000-0000-0000-000000000002");

    @Test
    void maxInFlightIsSharedAcrossAccountsAndPermitReleaseIsIdempotent() {
        AiInvocationCoordinator coordinator = coordinator(1, 5, 30);
        Optional<AiInvocationCoordinator.Permit> first = coordinator.tryAcquire(ACCOUNT);
        assertTrue(first.isPresent());
        assertFalse(coordinator.tryAcquire(OTHER_ACCOUNT).isPresent());
        first.get().release();
        first.get().release();
        assertEquals(0, coordinator.inFlightForTests());
        assertTrue(coordinator.tryAcquire(OTHER_ACCOUNT).isPresent());
    }

    @Test
    void minuteAndHourWindowsAreAccountScopedAndExpireWithClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-16T10:00:00Z"));
        AiInvocationCoordinator coordinator = new AiInvocationCoordinator(5, 2, 3, clock);
        AiInvocationCoordinator.Permit first = coordinator.tryAcquire(ACCOUNT).orElseThrow();
        first.release();
        AiInvocationCoordinator.Permit second = coordinator.tryAcquire(ACCOUNT).orElseThrow();
        second.release();
        assertFalse(coordinator.tryAcquire(ACCOUNT).isPresent());
        assertTrue(coordinator.tryAcquire(OTHER_ACCOUNT).isPresent());
        clock.advanceSeconds(61);
        assertTrue(coordinator.tryAcquire(ACCOUNT).isPresent());
    }

    @Test
    void oneHourWindowStillAppliesAfterMinuteWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-16T10:00:00Z"));
        AiInvocationCoordinator coordinator = new AiInvocationCoordinator(5, 1, 2, clock);
        coordinator.tryAcquire(ACCOUNT).orElseThrow().release();
        clock.advanceSeconds(61);
        coordinator.tryAcquire(ACCOUNT).orElseThrow().release();
        clock.advanceSeconds(61);
        assertFalse(coordinator.tryAcquire(ACCOUNT).isPresent());
        clock.advanceSeconds(3600);
        assertTrue(coordinator.tryAcquire(ACCOUNT).isPresent());
    }

    @Test
    void lowerHourlyLimitIsAcceptedAndActsAsTheTighterLimit() {
        AiInvocationCoordinator coordinator = coordinator(5, 5, 1);
        coordinator.tryAcquire(ACCOUNT).orElseThrow().release();
        assertFalse(coordinator.tryAcquire(ACCOUNT).isPresent());
    }

    private static AiInvocationCoordinator coordinator(int maxInFlight, int perMinute, int perHour) {
        return new AiInvocationCoordinator(
                maxInFlight,
                perMinute,
                perHour,
                Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
