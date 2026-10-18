package sg.edu.nus.facilityflow.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Real isolated PostgreSQL checks for atomic own-password mutation and audit. */
class JdbcPasswordChangeRepositoryTest {
    private static PasswordChangeFixtures db;
    private JdbcPasswordChangeRepository repository;
    private static final PasswordEncoder ENCODER = new SyntheticPasswordEncoder();

    @BeforeAll static void startPostgres() throws Exception { db = PasswordChangeFixtures.start(); }
    @AfterAll static void stopPostgres() throws Exception { db.close(); }

    @BeforeEach void reset() {
        db.reset();
        repository = new JdbcPasswordChangeRepository(PasswordChangeFixtures.dataSource(db.postgres, "facilityflow_app"), ENCODER);
    }

    @Test
    @DisplayName("REQ-SH-002/006: each supported role changes only password, increments version, and writes one safe UTC audit")
    void updatesPasswordAndAppendsOneSafeAuditForEachRole() {
        for (String role : new String[]{"REQUESTER", "TECHNICIAN", "FACILITIES_MANAGER"}) {
            db.reset();
            db.account(role, true, 4, "legacy$old-password");
            repository.changeOwnPassword(snapshot(role, 4), "old-password", "new-password");

            assertEquals("encoded$new-password", value("password_hash"));
            assertEquals(1L, value("version"));
            assertEquals(4L, value("session_version"), "own-password retains existing sessions");
            assertEquals("password-test", db.app.queryForObject("SELECT username FROM facilityflow.user_account WHERE id = ?", String.class, PasswordChangeFixtures.ACCOUNT));
            assertEquals(1, db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
            assertEquals("ACCOUNT_PASSWORD_CHANGED", db.app.queryForObject("SELECT action FROM facilityflow.audit_event", String.class));
            assertEquals("ACCOUNT", db.app.queryForObject("SELECT target_type FROM facilityflow.audit_event", String.class));
            assertEquals(PasswordChangeFixtures.ACCOUNT,
                    db.app.queryForObject("SELECT target_id FROM facilityflow.audit_event", UUID.class));
            assertEquals("{\"changeType\": \"OWN\"}", db.app.queryForObject("SELECT details::text FROM facilityflow.audit_event", String.class));
            assertNotNull(db.app.queryForObject("SELECT occurred_at FROM facilityflow.audit_event WHERE actor_id = ?", java.time.OffsetDateTime.class, PasswordChangeFixtures.ACCOUNT));
            String audit = db.app.queryForObject("SELECT details::text FROM facilityflow.audit_event", String.class);
            assertFalse(audit.contains("password")); assertFalse(audit.contains("old-password")); assertFalse(audit.contains("new-password"));
        }
    }

    @Test
    @DisplayName("REQ-SH-002: inactive account, role mismatch, and stale session version are rejected without password read/write or audit")
    void rejectsChangedPersistedAuthorizationAtomically() {
        db.account("TECHNICIAN", false, 4, "legacy$old-password");
        assertCode(PasswordChangeException.Code.ACCESS_CHANGED,
                () -> repository.changeOwnPassword(snapshot("TECHNICIAN", 4), "old-password", "new-password"));
        db.account("REQUESTER", true, 4, "legacy$old-password");
        assertCode(PasswordChangeException.Code.ACCESS_CHANGED,
                () -> repository.changeOwnPassword(snapshot("TECHNICIAN", 4), "old-password", "new-password"));
        db.account("TECHNICIAN", true, 5, "legacy$old-password");
        assertCode(PasswordChangeException.Code.ACCESS_CHANGED,
                () -> repository.changeOwnPassword(snapshot("TECHNICIAN", 4), "old-password", "new-password"));
        assertEquals("legacy$old-password", value("password_hash"));
        assertEquals(0, db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
    }

    @Test
    @DisplayName("REQ-SH-002: wrong current password leaves hash, versions, and audit unchanged")
    void incorrectCurrentPasswordMakesNoMutation() {
        assertCode(PasswordChangeException.Code.INCORRECT_CURRENT_PASSWORD,
                () -> repository.changeOwnPassword(snapshot("REQUESTER", 4), "wrong-password", "new-password"));
        assertEquals("{noop}old-password", value("password_hash"));
        assertEquals(0L, value("version"));
        assertEquals(0, db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
    }

    @Test
    @DisplayName("REQ-SH-014/NFR-SEC-002: audit failure rolls the password replacement back and exposes no cause")
    void auditFailureRollsBackPasswordAndVersions() {
        db.admin.execute("""
                CREATE FUNCTION facilityflow.fail_password_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic audit failure'; END $$
                """);
        db.admin.execute("CREATE TRIGGER fail_password_audit BEFORE INSERT ON facilityflow.audit_event "
                + "FOR EACH ROW EXECUTE FUNCTION facilityflow.fail_password_audit()");

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> repository.changeOwnPassword(snapshot("REQUESTER", 4), "old-password", "new-password"));

        assertEquals(PasswordChangeException.Code.UNAVAILABLE, failure.code());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("audit"));
        assertEquals("{noop}old-password", value("password_hash"));
        assertEquals(0L, value("version"));
        assertEquals(0, db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
    }

    @Test
    @DisplayName("REQ-SH-010: direct persistence seam rejects invalid lengths before encoder or database writes")
    void validatesBeforeHashing() {
        AtomicInteger encodes = new AtomicInteger();
        var counting = new PasswordEncoder() {
            public String encode(CharSequence raw) { encodes.incrementAndGet(); return ENCODER.encode(raw); }
            public boolean matches(CharSequence raw, String encoded) { return ENCODER.matches(raw, encoded); }
        };
        var direct = new JdbcPasswordChangeRepository(PasswordChangeFixtures.dataSource(db.postgres, "facilityflow_app"), counting);
        assertCode(PasswordChangeException.Code.INVALID_INPUT,
                () -> direct.changeOwnPassword(snapshot("REQUESTER", 4), "old-password", "short"));
        assertEquals(0, encodes.get());
        assertEquals(0L, value("version"));
    }

    @Test
    @DisplayName("REQ-SH-004: successful own-password change preserves the persisted session version")
    void sessionVersionIsNotInvalidated() {
        repository.changeOwnPassword(snapshot("REQUESTER", 4), "old-password", "new-password");
        assertEquals(4L, value("session_version"));
    }

    @Test
    @DisplayName("REQ-SH-010: identical current and replacement passwords are persisted as an allowed change")
    void identicalPasswordIsNotRejectedAsAChange() {
        repository.changeOwnPassword(snapshot("REQUESTER", 4), "old-password", "old-password");
        assertEquals("encoded$old-password", value("password_hash"));
        assertEquals(1, db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
    }

    @Test
    @DisplayName("REQ-SH-002/014: concurrent changes serialize and the later request verifies the latest hash")
    void staleConcurrentCurrentPasswordCannotOverwriteReplacement() throws Exception {
        CountDownLatch firstVerification = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger matches = new AtomicInteger();
        PasswordEncoder blocking = new PasswordEncoder() {
            public String encode(CharSequence raw) { return ENCODER.encode(raw); }
            public boolean matches(CharSequence raw, String encoded) {
                if (matches.incrementAndGet() == 1) {
                    firstVerification.countDown();
                    try {
                        if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new AssertionError("first verification was not released");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt(); throw new AssertionError(failure);
                    }
                }
                return ENCODER.matches(raw, encoded);
            }
        };
        var source = PasswordChangeFixtures.dataSource(db.postgres, "facilityflow_app");
        source.setApplicationName("t5-password-concurrency");
        var concurrentRepository = new JdbcPasswordChangeRepository(source, blocking);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> concurrentRepository.changeOwnPassword(
                    snapshot("REQUESTER", 4), "old-password", "first-password"));
            assertTrue(firstVerification.await(5, TimeUnit.SECONDS), "first change reaches verification while holding the account lock");
            var second = workers.submit(() -> concurrentRepository.changeOwnPassword(
                    snapshot("REQUESTER", 4), "old-password", "second-password"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean secondWaitingForLock = false;
            while (System.nanoTime() < deadline && !secondWaitingForLock) {
                Integer waiting = db.admin.queryForObject("""
                        SELECT count(*) FROM pg_stat_activity
                         WHERE application_name = 't5-password-concurrency' AND wait_event_type = 'Lock'
                        """, Integer.class);
                secondWaitingForLock = waiting != null && waiting > 0;
                if (!secondWaitingForLock) Thread.yield();
            }
            assertTrue(secondWaitingForLock, "second change waits on the row lock before reading the password hash");
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            ExecutionException secondFailure = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
            assertInstanceOf(PasswordChangeException.class, secondFailure.getCause());
            assertEquals(PasswordChangeException.Code.INCORRECT_CURRENT_PASSWORD,
                    ((PasswordChangeException) secondFailure.getCause()).code());
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
        }
        assertEquals("encoded$first-password", value("password_hash"));
        assertEquals(1, db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
    }

    private Object value(String column) {
        return db.app.queryForObject("SELECT " + column + " FROM facilityflow.user_account WHERE id = ?", Object.class, PasswordChangeFixtures.ACCOUNT);
    }

    private static CurrentAccountSnapshot snapshot(String role, long version) {
        return new CurrentAccountSnapshot(PasswordChangeFixtures.ACCOUNT, AccountRole.valueOf(role), version);
    }

    private static void assertCode(PasswordChangeException.Code code, org.junit.jupiter.api.function.Executable action) {
        PasswordChangeException failure = assertThrows(PasswordChangeException.class, action);
        assertEquals(code, failure.code());
    }

    private static final class SyntheticPasswordEncoder implements PasswordEncoder {
        public String encode(CharSequence rawPassword) { return "encoded$" + rawPassword; }
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals("{noop}" + rawPassword) || encodedPassword.equals("legacy$" + rawPassword)
                    || encodedPassword.equals("encoded$" + rawPassword);
        }
    }
}
