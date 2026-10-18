package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

class JdbcTechnicianCommandRepositoryTest {
    private static final CurrentAccountSnapshot ACTOR = new CurrentAccountSnapshot(
            TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, 7);

    private static TechnicianCommandFixtures db;
    private static DataSource appDataSource;
    private static JdbcTechnicianCommandRepository repository;

    @BeforeAll
    static void startIsolatedPostgres() throws Exception {
        db = TechnicianCommandFixtures.start();
        appDataSource = TechnicianCommandFixtures.dataSource(db.postgres, "facilityflow_app");
        ((PGSimpleDataSource) appDataSource).setApplicationName("t2-command-sut");
        repository = new JdbcTechnicianCommandRepository(appDataSource);
    }

    @AfterAll
    static void closeIsolatedPostgres() throws Exception {
        if (db != null) db.close();
    }

    @BeforeEach
    void resetFixture() {
        db.reset();
    }

    @Test
    @DisplayName("REQ-TECH-004/TECH-A01/SH-006: start atomically advances state and writes one safe audit event")
    void startPersistsStateVersionAndSafeAudit() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 12);
        UUID requestId = db.requestId(displayId);

        repository.startWork(ACTOR, displayId, 12);

        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(13, db.requestVersion(displayId));
        assertEquals(1, auditCount("TECHNICIAN_WORK_STARTED", requestId));
        String details = auditDetails("TECHNICIAN_WORK_STARTED", requestId);
        assertTrue(details.contains("\"fromStatus\": \"ASSIGNED\""));
        assertTrue(details.contains("\"toStatus\": \"IN_PROGRESS\""));
        assertTrue(details.contains("\"version\": 13"));
        assertEquals(3, auditKeyCount("TECHNICIAN_WORK_STARTED", requestId));
        assertAuditActorAndTarget(requestId, "TECHNICIAN_WORK_STARTED");
    }

    @Test
    @DisplayName("REQ-TECH-005/006/TECH-A03/SH-006: a Work Log is append-only with database ID/time and safe audit")
    void appendLogPersistsAuthorTimeAndAuditWithoutFreeText() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 2);
        UUID requestId = db.requestId(displayId);
        String privateNote = "Replaced valve; customer code 7781";

        repository.appendWorkLog(ACTOR, displayId, 2, privateNote, 35);

        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(3, db.requestVersion(displayId));
        assertEquals(1, db.app.queryForObject(
                "SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?", Integer.class, requestId));
        assertEquals(TechnicianCommandFixtures.TECHNICIAN, db.app.queryForObject(
                "SELECT author_id FROM facilityflow.work_log WHERE request_id = ?", UUID.class, requestId));
        assertEquals(privateNote, db.app.queryForObject(
                "SELECT note FROM facilityflow.work_log WHERE request_id = ?", String.class, requestId));
        assertEquals(35, db.app.queryForObject(
                "SELECT minutes FROM facilityflow.work_log WHERE request_id = ?", Integer.class, requestId));
        assertNotNull(db.app.queryForObject(
                "SELECT created_at FROM facilityflow.work_log WHERE request_id = ?", java.time.OffsetDateTime.class, requestId));
        assertEquals(1, auditCount("TECHNICIAN_WORK_LOGGED", requestId));
        String details = auditDetails("TECHNICIAN_WORK_LOGGED", requestId);
        assertTrue(details.contains("\"minutes\": 35"));
        assertTrue(details.contains("\"version\": 3"));
        assertEquals(2, auditKeyCount("TECHNICIAN_WORK_LOGGED", requestId));
        assertTrue(!details.contains(privateNote));
        assertTrue(!details.contains("Synthetic T2 request"));
        assertAuditActorAndTarget(requestId, "TECHNICIAN_WORK_LOGGED");
    }

    @Test
    @DisplayName("REQ-SH-014: command commit survives rollback of a suspended outer transaction")
    void commandUsesIndependentTransactionInsideCallerTransaction() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);

        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(appDataSource));
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE);
        db.admin.execute("""
                CREATE FUNCTION facilityflow.assert_t2_read_committed() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF current_setting('transaction_isolation') <> 'read committed' THEN
                        RAISE EXCEPTION 'command isolation is not read committed';
                    END IF;
                    RETURN NEW;
                END $$
                """);
        db.admin.execute("CREATE TRIGGER assert_t2_read_committed BEFORE UPDATE OF status "
                + "ON facilityflow.maintenance_request FOR EACH ROW EXECUTE FUNCTION facilityflow.assert_t2_read_committed()");
        try {
            outer.executeWithoutResult(status -> {
                repository.startWork(ACTOR, displayId, 0);
                status.setRollbackOnly();
            });
        } finally {
            db.admin.execute("DROP TRIGGER IF EXISTS assert_t2_read_committed ON facilityflow.maintenance_request");
            db.admin.execute("DROP FUNCTION IF EXISTS facilityflow.assert_t2_read_committed()");
        }

        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(1, db.requestVersion(displayId));
    }

    @Test
    @DisplayName("REQ-TECH-004/005: duplicate same-version commands conflict without duplicate writes")
    void replayAtSameVersionCannotDuplicateCommand() {
        String startId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        repository.startWork(ACTOR, startId, 0);
        var replayStart = assertThrows(TechnicianCommandException.class,
                () -> repository.startWork(ACTOR, startId, 0));
        String logId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 9);
        repository.appendWorkLog(ACTOR, logId, 9, "Valve checked", 10);
        var replayLog = assertThrows(TechnicianCommandException.class,
                () -> repository.appendWorkLog(ACTOR, logId, 9, "Valve checked", 10));

        assertEquals(TechnicianCommandException.Code.CONFLICT, replayStart.code());
        assertEquals(TechnicianCommandException.Code.CONFLICT, replayLog.code());
        assertEquals(1, db.app.queryForObject(
                "SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?", Integer.class, db.requestId(logId)));
        assertEquals(1, db.app.queryForObject(
                "SELECT count(*) FROM facilityflow.audit_event WHERE target_id = ?", Integer.class, db.requestId(logId)));
    }

    @Test
    @DisplayName("REQ-TECH-005/010/TECH-A06: inaccessible and malformed objects share safe unavailable result")
    void unassignedUnknownAndMalformedIdsAreUnavailable() {
        String otherTech = db.request(TechnicianCommandFixtures.OTHER_TECHNICIAN, "ASSIGNED", 0);
        for (String id : List.of(otherTech, "FF-999999", "FF-000000", "garbage")) {
            var failure = assertThrows(TechnicianCommandException.class, () -> repository.startWork(ACTOR, id, 0));
            assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE, failure.code());
            assertEquals("The requested work is not available.", failure.getMessage());
            assertNull(failure.getCause());
        }
    }

    @Test
    @DisplayName("REQ-TECH-004/005: persisted state and stale Request versions conflict without writes")
    void wrongStateAndStaleVersionConflict() {
        String wrongState = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 3);
        String staleVersion = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 5);

        var stateFailure = assertThrows(TechnicianCommandException.class,
                () -> repository.startWork(ACTOR, wrongState, 3));
        var staleFailure = assertThrows(TechnicianCommandException.class,
                () -> repository.startWork(ACTOR, staleVersion, 4));

        assertEquals(TechnicianCommandException.Code.CONFLICT, stateFailure.code());
        assertEquals(TechnicianCommandException.Code.CONFLICT, staleFailure.code());
        assertEquals("IN_PROGRESS", db.requestState(wrongState));
        assertEquals(3, db.requestVersion(wrongState));
        assertEquals("ASSIGNED", db.requestState(staleVersion));
        assertEquals(5, db.requestVersion(staleVersion));
    }

    @Test
    @DisplayName("REQ-TECH-004/005/011: commands accept only their specified persisted lifecycle states")
    void eachCommandRejectsEveryOtherPersistedState() {
        String open = db.request(null, "OPEN", 0);
        String inProgressForStart = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 0);
        String completedForStart = db.request(TechnicianCommandFixtures.TECHNICIAN, "COMPLETED", 0);
        String closedForStart = db.request(TechnicianCommandFixtures.TECHNICIAN, "CLOSED", 0);
        String cancelledForStart = db.request(TechnicianCommandFixtures.TECHNICIAN, "CANCELLED", 0);
        String assignedForLog = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        String completedForLog = db.request(TechnicianCommandFixtures.TECHNICIAN, "COMPLETED", 0);
        String closedForLog = db.request(TechnicianCommandFixtures.TECHNICIAN, "CLOSED", 0);
        String cancelledForLog = db.request(TechnicianCommandFixtures.TECHNICIAN, "CANCELLED", 0);

        assertUnavailable(open, 0);
        assertConflict(inProgressForStart, 0);
        assertConflict(completedForStart, 0);
        assertConflict(closedForStart, 0);
        assertConflict(cancelledForStart, 0);
        assertLogConflict(assignedForLog, 0);
        assertLogConflict(completedForLog, 0);
        assertLogConflict(closedForLog, 0);
        assertLogConflict(cancelledForLog, 0);

        for (String id : List.of(inProgressForStart, completedForStart, closedForStart, cancelledForStart,
                assignedForLog, completedForLog, closedForLog, cancelledForLog)) {
            assertEquals(0, auditCountForRequest(db.requestId(id)));
            assertEquals(0, db.app.queryForObject(
                    "SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?", Integer.class, db.requestId(id)));
            assertEquals(0, db.requestVersion(id));
        }
    }

    @Test
    @DisplayName("REQ-SH-002: repository independently denies bad snapshot role/version and persisted account changes")
    void accountRoleActiveAndSessionVersionAreRechecked() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        assertThrows(AccessDeniedException.class, () -> repository.startWork(
                new CurrentAccountSnapshot(TechnicianCommandFixtures.TECHNICIAN, AccountRole.REQUESTER, 7), displayId, 0));
        assertThrows(AccessDeniedException.class, () -> repository.startWork(
                new CurrentAccountSnapshot(TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, -1), displayId, 0));
        assertThrows(AccessDeniedException.class, () -> repository.startWork(null, displayId, 0));

        db.admin.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", TechnicianCommandFixtures.TECHNICIAN);
        assertThrows(AccessDeniedException.class, () -> repository.startWork(ACTOR, displayId, 0));
        db.admin.update("UPDATE facilityflow.user_account SET active = true, role = 'REQUESTER' WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertThrows(AccessDeniedException.class, () -> repository.startWork(ACTOR, displayId, 0));
        db.admin.update("UPDATE facilityflow.user_account SET role = 'TECHNICIAN', session_version = 8 WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertThrows(AccessDeniedException.class, () -> repository.startWork(ACTOR, displayId, 0));
        assertEquals("ASSIGNED", db.requestState(displayId));
        assertEquals(0, db.requestVersion(displayId));
    }

    @Test
    @DisplayName("REQ-TECH-010/TECH-A06: reassignment before command leaves the Request untouched")
    void formerAssigneeCannotMutateAfterReassignment() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        db.admin.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE display_id = ?",
                TechnicianCommandFixtures.OTHER_TECHNICIAN, displayId);

        var failure = assertThrows(TechnicianCommandException.class,
                () -> repository.startWork(ACTOR, displayId, 0));

        assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE, failure.code());
        assertEquals("ASSIGNED", db.requestState(displayId));
        assertEquals(0, db.requestVersion(displayId));
        assertEquals(0, auditCountForRequest(db.requestId(displayId)));
    }

    @Test
    @DisplayName("REQ-SH-014/SH-006: an Audit INSERT failure rolls back Request version and Work Log")
    void auditFailureRollsBackWholeLogCommand() {
        String startId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 4);
        UUID startRequestId = db.requestId(startId);
        String logId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 7);
        UUID logRequestId = db.requestId(logId);
        db.admin.execute("""
                CREATE FUNCTION facilityflow.fail_t2_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic audit rejection'; END $$
                """);
        db.admin.execute("CREATE TRIGGER fail_t2_audit BEFORE INSERT ON facilityflow.audit_event "
                + "FOR EACH ROW EXECUTE FUNCTION facilityflow.fail_t2_audit()");

        TechnicianCommandException failure;
        try {
            var startFailure = assertThrows(TechnicianCommandException.class,
                    () -> repository.startWork(ACTOR, startId, 4));
            assertEquals(TechnicianCommandException.Code.UNAVAILABLE, startFailure.code());
            assertNull(startFailure.getCause());
            assertEquals("ASSIGNED", db.requestState(startId));
            assertEquals(4, db.requestVersion(startId));
            assertEquals(0, auditCountForRequest(startRequestId));

            failure = assertThrows(TechnicianCommandException.class,
                    () -> repository.appendWorkLog(ACTOR, logId, 7, "Private log text", 30));
        } finally {
            db.admin.execute("DROP TRIGGER IF EXISTS fail_t2_audit ON facilityflow.audit_event");
            db.admin.execute("DROP FUNCTION IF EXISTS facilityflow.fail_t2_audit()");
        }

        assertEquals(TechnicianCommandException.Code.UNAVAILABLE, failure.code());
        assertNull(failure.getCause());
        assertEquals("IN_PROGRESS", db.requestState(logId));
        assertEquals(7, db.requestVersion(logId));
        assertEquals(0, db.app.queryForObject(
                "SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?", Integer.class, logRequestId));
        assertEquals(0, auditCountForRequest(logRequestId));
    }

    @Test
    @DisplayName("REQ-SH-007/TECH-006: app role cannot update or delete historical Work Logs or Audit Events")
    void applicationRoleCannotRewriteOrDeleteHistory() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 0);
        UUID requestId = db.requestId(displayId);
        repository.appendWorkLog(ACTOR, displayId, 0, "Append-only evidence", 15);
        UUID workLogId = db.app.queryForObject(
                "SELECT id FROM facilityflow.work_log WHERE request_id = ?", UUID.class, requestId);
        UUID auditId = db.app.queryForObject(
                "SELECT id FROM facilityflow.audit_event WHERE target_id = ?", UUID.class, requestId);

        assertThrows(DataAccessException.class, () -> db.app.update(
                "UPDATE facilityflow.work_log SET note = 'rewritten' WHERE id = ?", workLogId));
        assertThrows(DataAccessException.class, () -> db.app.update(
                "DELETE FROM facilityflow.work_log WHERE id = ?", workLogId));
        assertThrows(DataAccessException.class, () -> db.app.update(
                "UPDATE facilityflow.audit_event SET action = 'rewritten' WHERE id = ?", auditId));
        assertThrows(DataAccessException.class, () -> db.app.update(
                "DELETE FROM facilityflow.audit_event WHERE id = ?", auditId));
        assertEquals("Append-only evidence", db.app.queryForObject(
                "SELECT note FROM facilityflow.work_log WHERE id = ?", String.class, workLogId));
        assertEquals("TECHNICIAN_WORK_LOGGED", db.app.queryForObject(
                "SELECT action FROM facilityflow.audit_event WHERE id = ?", String.class, auditId));
    }

    @Test
    @DisplayName("REQ-SH-014/TECH-A01: two same-version concurrent starts serialize to exactly one commit")
    void simultaneousSameVersionStartsHaveOneWinner() throws Exception {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attemptStart(displayId, ready, go));
            var second = pool.submit(() -> attemptStart(displayId, ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not become ready");
            go.countDown();
            List<String> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("success"::equals).count());
            assertEquals(1, results.stream().filter(TechnicianCommandException.Code.CONFLICT.name()::equals).count());
        }
        UUID requestId = db.requestId(displayId);
        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(1, db.requestVersion(displayId));
        assertEquals(1, auditCount("TECHNICIAN_WORK_STARTED", requestId));
    }

    @Test
    @DisplayName("REQ-SH-014/TECH-A03: concurrent same-version Work Logs append exactly one row")
    void simultaneousSameVersionLogsHaveOneWinner() throws Exception {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 0);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attemptLog(displayId, "log one", ready, go));
            var second = pool.submit(() -> attemptLog(displayId, "log two", ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not become ready");
            go.countDown();
            List<String> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("success"::equals).count());
            assertEquals(1, results.stream().filter(TechnicianCommandException.Code.CONFLICT.name()::equals).count());
        }
        UUID requestId = db.requestId(displayId);
        assertEquals(1, db.app.queryForObject(
                "SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?", Integer.class, requestId));
        assertEquals(1, db.requestVersion(displayId));
        assertEquals(1, auditCount("TECHNICIAN_WORK_LOGGED", requestId));
    }

    @Test
    @DisplayName("REQ-TECH-010/TECH-A06: reassignment committed while the command waits blocks the former assignee")
    void pendingCommandRechecksAssignmentAfterRowLockWait() throws Exception {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        UUID requestId = db.requestId(displayId);
        try (var blocker = db.postgres.getPostgresDatabase().getConnection();
             var pool = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var update = blocker.prepareStatement(
                    "UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE id = ?")) {
                update.setObject(1, TechnicianCommandFixtures.OTHER_TECHNICIAN);
                update.setObject(2, requestId);
                assertEquals(1, update.executeUpdate());
            }
            Future<String> pending = pool.submit(() -> attemptStart(displayId));
            try {
                awaitSutLockWait();
            } finally {
                blocker.commit();
            }
            assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE.name(), pending.get(5, TimeUnit.SECONDS));
        }
        assertEquals("ASSIGNED", db.requestState(displayId));
        assertEquals(0, db.requestVersion(displayId));
        assertEquals(0, auditCountForRequest(requestId));
    }

    @Test
    @DisplayName("REQ-SH-002: session invalidation committed while authorization waits prevents the command")
    void pendingCommandRechecksSessionVersionAfterAccountLockWait() throws Exception {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 0);
        try (var blocker = db.postgres.getPostgresDatabase().getConnection();
             var pool = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var update = blocker.prepareStatement(
                    "UPDATE facilityflow.user_account SET session_version = 8 WHERE id = ?")) {
                update.setObject(1, TechnicianCommandFixtures.TECHNICIAN);
                assertEquals(1, update.executeUpdate());
            }
            Future<String> pending = pool.submit(() -> attemptStart(displayId));
            try {
                awaitSutLockWait();
            } finally {
                blocker.commit();
            }
            assertEquals("ACCESS_DENIED", pending.get(5, TimeUnit.SECONDS));
        }
        assertEquals("ASSIGNED", db.requestState(displayId));
        assertEquals(0, db.requestVersion(displayId));
        assertEquals(0, auditCountForRequest(db.requestId(displayId)));
    }

    private static String attemptStart(String displayId, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent start timed out");
        try {
            repository.startWork(ACTOR, displayId, 0);
            return "success";
        } catch (TechnicianCommandException failure) {
            return failure.code().name();
        }
    }

    private static String attemptStart(String displayId) {
        try {
            repository.startWork(ACTOR, displayId, 0);
            return "success";
        } catch (AccessDeniedException failure) {
            return "ACCESS_DENIED";
        } catch (TechnicianCommandException failure) {
            return failure.code().name();
        }
    }

    private static void awaitSutLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = db.admin.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                     WHERE application_name = 't2-command-sut' AND wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(10);
        }
        throw new AssertionError("command did not reach the expected PostgreSQL row lock within five seconds");
    }

    private static String attemptLog(String displayId, String note, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent log timed out");
        try {
            repository.appendWorkLog(ACTOR, displayId, 0, note, 10);
            return "success";
        } catch (TechnicianCommandException failure) {
            return failure.code().name();
        }
    }

    private static int auditCount(String action, UUID requestId) {
        return db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = ? AND target_id = ?",
                Integer.class, action, requestId);
    }

    private static void assertUnavailable(String displayId, long version) {
        var failure = assertThrows(TechnicianCommandException.class,
                () -> repository.startWork(ACTOR, displayId, version));
        assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE, failure.code());
    }

    private static void assertConflict(String displayId, long version) {
        var failure = assertThrows(TechnicianCommandException.class,
                () -> repository.startWork(ACTOR, displayId, version));
        assertEquals(TechnicianCommandException.Code.CONFLICT, failure.code());
        assertNull(failure.getCause());
    }

    private static void assertLogConflict(String displayId, long version) {
        var failure = assertThrows(TechnicianCommandException.class,
                () -> repository.appendWorkLog(ACTOR, displayId, version, "Valid work", 10));
        assertEquals(TechnicianCommandException.Code.CONFLICT, failure.code());
        assertNull(failure.getCause());
    }

    private static int auditCountForRequest(UUID requestId) {
        return db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE target_id = ?",
                Integer.class, requestId);
    }

    private static String auditDetails(String action, UUID requestId) {
        return db.app.queryForObject("SELECT details::text FROM facilityflow.audit_event WHERE action = ? AND target_id = ?",
                String.class, action, requestId);
    }

    private static int auditKeyCount(String action, UUID requestId) {
        return db.app.queryForObject("""
                SELECT count(*)
                  FROM facilityflow.audit_event a
                  CROSS JOIN LATERAL jsonb_object_keys(a.details) AS entry(key)
                 WHERE a.action = ? AND a.target_id = ?
                """, Integer.class, action, requestId);
    }

    private static void assertAuditActorAndTarget(UUID requestId, String action) {
        assertEquals(TechnicianCommandFixtures.TECHNICIAN, db.app.queryForObject(
                "SELECT actor_id FROM facilityflow.audit_event WHERE action = ? AND target_id = ?", UUID.class,
                action, requestId));
        assertEquals("REQUEST", db.app.queryForObject(
                "SELECT target_type FROM facilityflow.audit_event WHERE action = ? AND target_id = ?", String.class,
                action, requestId));
        assertNotNull(db.app.queryForObject(
                "SELECT occurred_at FROM facilityflow.audit_event WHERE action = ? AND target_id = ?",
                java.time.OffsetDateTime.class, action, requestId));
    }
}
