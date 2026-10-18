package sg.edu.nus.facilityflow.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Requirement-derived service tests; submitted credentials exist only in local call arguments. */
class PasswordChangeServiceTest {
    private static final CurrentAccountSnapshot ACTOR = new CurrentAccountSnapshot(
            UUID.fromString("75000000-0000-0000-0000-000000000001"), AccountRole.TECHNICIAN, 4);

    @Test
    @DisplayName("REQ-SH-002: change is bound to the captured authenticated account snapshot")
    void passesOnlyCapturedActorAndCredentialsToRepository() {
        AtomicReference<CurrentAccountSnapshot> actor = new AtomicReference<>();
        AtomicReference<String> current = new AtomicReference<>();
        AtomicReference<String> replacement = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        PasswordChangeService service = new PasswordChangeService(provider(), (a, c, n) -> {
            calls.incrementAndGet(); actor.set(a); current.set(c); replacement.set(n);
        });

        service.changeOwnPassword("old", "new-value", "new-value");

        assertEquals(1, calls.get());
        assertEquals(ACTOR, actor.get());
        assertEquals("old", current.get());
        assertEquals("new-value", replacement.get());
    }

    @Test
    @DisplayName("REQ-SH-010: password length counts Unicode code points and allows valid whitespace")
    void acceptsLengthBoundariesSupplementaryCharactersAndWhitespaceWithoutTrimming() {
        RecordingRepository repo = new RecordingRepository();
        PasswordChangeService service = service(repo);
        String eightPoints = "😀😀😀😀😀😀😀😀";
        String twentyFourPoints = "a".repeat(24);
        String whitespace = "        ";

        service.changeOwnPassword("old", eightPoints, eightPoints);
        service.changeOwnPassword("old", twentyFourPoints, twentyFourPoints);
        service.changeOwnPassword("old", whitespace, whitespace);

        assertEquals(3, repo.calls.get());
        assertEquals(whitespace, repo.newPassword.get());
    }

    @Test
    @DisplayName("REQ-SH-010: identical current and new password is allowed")
    void allowsIdenticalPasswordReplacement() {
        RecordingRepository repo = new RecordingRepository();
        service(repo).changeOwnPassword("same-password", "same-password", "same-password");
        assertEquals(1, repo.calls.get());
        assertEquals("same-password", repo.newPassword.get());
    }

    @Test
    @DisplayName("REQ-SH-010: reject new passwords below 8 or above 24 code points before persistence")
    void rejectsCodePointLengthOutsideInclusiveBounds() {
        RecordingRepository repo = new RecordingRepository();
        PasswordChangeService service = service(repo);

        assertCode(PasswordChangeException.Code.INVALID_INPUT, () -> service.changeOwnPassword("old", "😀😀😀😀😀😀😀", "😀😀😀😀😀😀😀"));
        String over = "😀".repeat(25);
        assertCode(PasswordChangeException.Code.INVALID_INPUT, () -> service.changeOwnPassword("old", over, over));
        assertEquals(0, repo.calls.get());
    }

    @Test
    @DisplayName("REQ-SH-010: reject empty, oversized current password and mismatched confirmation")
    void rejectsCurrentAndConfirmationErrorsWithoutRepositoryCall() {
        RecordingRepository repo = new RecordingRepository();
        PasswordChangeService service = service(repo);
        assertCode(PasswordChangeException.Code.INVALID_INPUT, () -> service.changeOwnPassword("", "valid-123", "valid-123"));
        assertCode(PasswordChangeException.Code.INVALID_INPUT, () -> service.changeOwnPassword("x".repeat(25), "valid-123", "valid-123"));
        assertCode(PasswordChangeException.Code.INVALID_INPUT, () -> service.changeOwnPassword("old", "valid-123", "valid-124"));
        assertCode(PasswordChangeException.Code.INVALID_INPUT, () -> service.changeOwnPassword("old", "valid-123", null));
        assertEquals(0, repo.calls.get());
    }

    @Test
    @DisplayName("NFR-SEC-002: repository errors map to safe UNAVAILABLE with no operational cause")
    void mapsRepositoryFailureWithoutCauseOrCredentialEcho() {
        PasswordChangeService service = new PasswordChangeService(provider(), (a, c, n) -> {
            throw new IllegalStateException("synthetic database detail");
        });

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword("old-secret", "new-secret", "new-secret"));
        assertEquals(PasswordChangeException.Code.UNAVAILABLE, failure.code());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("old-secret"));
        assertFalse(failure.toString().contains("new-secret"));
        assertFalse(failure.toString().contains("synthetic database detail"));
    }

    @Test
    @DisplayName("REQ-SH-002: unavailable or invalid account snapshot never reaches persistence")
    void rejectsUnsupportedSnapshotBeforePersistence() {
        RecordingRepository repo = new RecordingRepository();
        CurrentAccountProvider negativeVersion = new CurrentAccountProvider() {
            public CurrentAccount requireCurrentAccount() { return new CurrentAccount(ACTOR.id(), "tech", "Tech", ACTOR.role()); }
            public CurrentAccountSnapshot requireCurrentAccountSnapshot() {
                return new CurrentAccountSnapshot(ACTOR.id(), ACTOR.role(), -1);
            }
        };
        assertCode(PasswordChangeException.Code.ACCESS_CHANGED, () -> new PasswordChangeService(negativeVersion, repo)
                .changeOwnPassword("old", "valid-123", "valid-123"));
        assertEquals(0, repo.calls.get());
    }

    private static PasswordChangeService service(RecordingRepository repo) {
        return new PasswordChangeService(provider(), repo);
    }

    private static CurrentAccountProvider provider() {
        return new CurrentAccountProvider() {
            public CurrentAccount requireCurrentAccount() { return new CurrentAccount(ACTOR.id(), "tech", "Tech", ACTOR.role()); }
            public CurrentAccountSnapshot requireCurrentAccountSnapshot() { return ACTOR; }
        };
    }

    private static void assertCode(PasswordChangeException.Code code, org.junit.jupiter.api.function.Executable action) {
        PasswordChangeException failure = assertThrows(PasswordChangeException.class, action);
        assertEquals(code, failure.code());
    }

    private static final class RecordingRepository implements PasswordChangeRepository {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> newPassword = new AtomicReference<>();
        public void changeOwnPassword(CurrentAccountSnapshot actor, String currentPassword, String newPassword) {
            calls.incrementAndGet(); this.newPassword.set(newPassword);
        }
    }
}
