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
import org.postgresql.ds.PGSimpleDataSource;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

class JdbcTechnicianCompletionRepositoryTest {
    private static final CurrentAccountSnapshot ACTOR = new CurrentAccountSnapshot(
            TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, 7);
    private static TechnicianCommandFixtures db;
    private static JdbcTechnicianCommandRepository repository;

    @BeforeAll
    static void startIsolatedPostgres() throws Exception {
        db = TechnicianCommandFixtures.start();
        var source = TechnicianCommandFixtures.dataSource(db.postgres, "facilityflow_app");
        ((PGSimpleDataSource) source).setApplicationName("t3-completion-sut");
        repository = new JdbcTechnicianCommandRepository(source);
    }

    @AfterAll
    static void closeIsolatedPostgres() throws Exception {
        if (db != null) db.close();
    }

    @BeforeEach
    void resetFixture() { db.reset(); }

    @Test
    @DisplayName("REQ-TECH-007/008/SH-006: successful completion persists retained assignment, metadata, history and safe audit")
    void validCompletionPersistsMetadataAndHistory() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 12);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, ?, ?)",
                requestId, TechnicianCommandFixtures.OTHER_TECHNICIAN, "Earlier technician work", 20);
        String summary = "  Repaired pump.\nChecked flow.  ";

        repository.completeWork(ACTOR, displayId, 12, summary);

        assertEquals("COMPLETED", db.requestState(displayId));
        assertEquals(13, db.requestVersion(displayId));
        assertEquals(TechnicianCommandFixtures.TECHNICIAN, db.app.queryForObject(
                "SELECT assigned_technician_id FROM facilityflow.maintenance_request WHERE id = ?", UUID.class, requestId));
        assertEquals(TechnicianCommandFixtures.utc(java.time.Instant.parse("2026-10-17T08:00:00Z")),
                db.app.queryForObject("SELECT assigned_at FROM facilityflow.maintenance_request WHERE id = ?",
                        java.time.OffsetDateTime.class, requestId));
        assertEquals("MEDIUM", db.app.queryForObject(
                "SELECT manager_priority FROM facilityflow.maintenance_request WHERE id = ?", String.class, requestId));
        assertEquals("Repaired pump.\nChecked flow.", db.app.queryForObject(
                "SELECT resolution_summary FROM facilityflow.maintenance_request WHERE id = ?", String.class, requestId));
        assertEquals(TechnicianCommandFixtures.TECHNICIAN, db.app.queryForObject(
                "SELECT completed_by FROM facilityflow.maintenance_request WHERE id = ?", UUID.class, requestId));
        var completedAt = db.app.queryForObject("SELECT completed_at FROM facilityflow.maintenance_request WHERE id = ?",
                java.time.OffsetDateTime.class, requestId);
        var historyCompletedAt = db.app.queryForObject("SELECT completed_at FROM facilityflow.request_completion_history WHERE request_id = ?",
                java.time.OffsetDateTime.class, requestId);
        var updatedAt = db.app.queryForObject("SELECT updated_at FROM facilityflow.maintenance_request WHERE id = ?",
                java.time.OffsetDateTime.class, requestId);
        assertNotNull(completedAt);
        assertNotNull(historyCompletedAt);
        assertEquals(completedAt, historyCompletedAt);
        assertEquals(completedAt, updatedAt);
        assertEquals(1, historyCount(requestId));
        assertEquals("Repaired pump.\nChecked flow.", db.app.queryForObject(
                "SELECT resolution_summary FROM facilityflow.request_completion_history WHERE request_id = ?", String.class, requestId));
        assertEquals(TechnicianCommandFixtures.TECHNICIAN, db.app.queryForObject(
                "SELECT technician_id FROM facilityflow.request_completion_history WHERE request_id = ?", UUID.class, requestId));
        assertEquals(1, auditCount(requestId));
        assertEquals(3, auditKeyCount(requestId));
        String details = auditDetails(requestId);
        assertTrue(details.contains("\"fromStatus\": \"IN_PROGRESS\""));
        assertTrue(details.contains("\"toStatus\": \"COMPLETED\""));
        assertTrue(details.contains("\"version\": 13"));
        assertTrue(!details.contains("Repaired pump"));
        assertEquals(TechnicianCommandFixtures.TECHNICIAN, db.app.queryForObject(
                "SELECT actor_id FROM facilityflow.audit_event WHERE target_id = ?", UUID.class, requestId));
        assertEquals("REQUEST", db.app.queryForObject(
                "SELECT target_type FROM facilityflow.audit_event WHERE target_id = ?", String.class, requestId));
    }

    @Test
    @DisplayName("REQ-TECH-007/TECH-A04: missing Work Log rejects completion without changing request or audit")
    void completionRequiresAnyExistingWorkLog() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 4);
        UUID requestId = db.requestId(displayId);
        var failure = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, 4, "Valid resolution summary"));

        assertEquals(TechnicianCommandException.Code.WORK_LOG_REQUIRED, failure.code());
        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(4, db.requestVersion(displayId));
        assertNull(db.app.queryForObject("SELECT resolution_summary FROM facilityflow.maintenance_request WHERE id = ?",
                String.class, requestId));
        assertEquals(0, historyCount(requestId));
        assertEquals(0, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-TECH-007/010: wrong state, stale version and former assignment cannot complete")
    void wrongStateStaleAndUnassignedCommandsAreRejected() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 3);
        var stateFailure = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, 3, "Valid resolution summary"));
        assertEquals(TechnicianCommandException.Code.CONFLICT, stateFailure.code());
        db.migrator.update("UPDATE facilityflow.maintenance_request SET status = 'IN_PROGRESS', version = 4 WHERE display_id = ?", displayId);
        var stale = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, 3, "Valid resolution summary"));
        assertEquals(TechnicianCommandException.Code.CONFLICT, stale.code());
        db.migrator.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE display_id = ?",
                TechnicianCommandFixtures.OTHER_TECHNICIAN, displayId);
        var former = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, 4, "Valid resolution summary"));
        assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE, former.code());
    }

    @Test
    @DisplayName("REQ-SH-014/TECH-A05: two same-version concurrent completions produce one commit")
    void simultaneousSameVersionCompletionsHaveOneWinner() throws Exception {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 0);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attemptCompletion(displayId, "Completion summary one", ready, go));
            var second = pool.submit(() -> attemptCompletion(displayId, "Completion summary two", ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "completion workers did not become ready");
            go.countDown();
            List<String> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("success"::equals).count());
            assertEquals(1, results.stream().filter(TechnicianCommandException.Code.CONFLICT.name()::equals).count());
        }
        assertEquals("COMPLETED", db.requestState(displayId));
        assertEquals(1, db.requestVersion(displayId));
        assertEquals(1, historyCount(requestId));
        assertEquals(1, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-TECH-010/TECH-A06: completion rechecks assignment after waiting for the Request lock")
    void pendingCompletionRechecksAssignmentAfterRowLockWait() throws Exception {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 0);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        try (var blocker = db.postgres.getPostgresDatabase().getConnection();
             var pool = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var update = blocker.prepareStatement(
                    "UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE id = ?")) {
                update.setObject(1, TechnicianCommandFixtures.OTHER_TECHNICIAN);
                update.setObject(2, requestId);
                assertEquals(1, update.executeUpdate());
            }
            Future<String> pending = pool.submit(() -> attemptCompletion(displayId, "Valid completion summary"));
            try {
                awaitCompletionSutLockWait();
            } finally {
                blocker.commit();
            }
            assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE.name(), pending.get(5, TimeUnit.SECONDS));
        }
        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(0, db.requestVersion(displayId));
        assertEquals(0, historyCount(requestId));
        assertEquals(0, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-SH-002: completion requires a persisted active Technician with the exact session")
    void persistedRoleActivityAndSessionAreRechecked() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 5);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);

        var manager = new CurrentAccountSnapshot(TechnicianCommandFixtures.MANAGER, AccountRole.FACILITIES_MANAGER, 0);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> repository.completeWork(manager, displayId, 5, "Valid resolution summary"));
        var negativeSession = new CurrentAccountSnapshot(TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, -1);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> repository.completeWork(negativeSession, displayId, 5, "Valid resolution summary"));

        db.migrator.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", TechnicianCommandFixtures.TECHNICIAN);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> repository.completeWork(ACTOR, displayId, 5, "Valid resolution summary"));
        db.migrator.update("UPDATE facilityflow.user_account SET active = true, session_version = 8 WHERE id = ?", TechnicianCommandFixtures.TECHNICIAN);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> repository.completeWork(ACTOR, displayId, 5, "Valid resolution summary"));

        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(5, db.requestVersion(displayId));
        assertEquals(0, historyCount(requestId));
        assertEquals(0, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-A07: repository rechecks persisted role and rejects invalid completion inputs")
    void repositoryRechecksPersistedRoleAndValidatesInputs() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 5);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);

        var negative = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, -1, "Valid resolution summary"));
        var invalidSummary = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, 5, "short"));
        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, negative.code());
        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, invalidSummary.code());

        db.migrator.update("UPDATE facilityflow.user_account SET role = 'REQUESTER' WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> repository.completeWork(ACTOR, displayId, 5, "Valid resolution summary"));
        assertEquals("IN_PROGRESS", db.requestState(displayId));
        assertEquals(5, db.requestVersion(displayId));
        assertEquals(0, historyCount(requestId));
        assertEquals(0, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-SH-014: audit insertion failure rolls back completion and history")
    void auditFailureRollsBackAllCompletionWrites() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 7);
        UUID requestId = db.requestId(displayId);
        String before = requestSnapshot(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        db.admin.execute("CREATE FUNCTION facilityflow.fail_t3_completion_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'audit failure'; END $$");
        db.admin.execute("CREATE TRIGGER fail_t3_completion_audit BEFORE INSERT ON facilityflow.audit_event FOR EACH ROW WHEN (NEW.action = 'TECHNICIAN_WORK_COMPLETED') EXECUTE FUNCTION facilityflow.fail_t3_completion_audit()");
        try {
            var failure = assertThrows(TechnicianCommandException.class,
                    () -> repository.completeWork(ACTOR, displayId, 7, "Valid resolution summary"));
            assertEquals(TechnicianCommandException.Code.UNAVAILABLE, failure.code());
        } finally {
            db.admin.execute("DROP TRIGGER IF EXISTS fail_t3_completion_audit ON facilityflow.audit_event");
            db.admin.execute("DROP FUNCTION IF EXISTS facilityflow.fail_t3_completion_audit()");
        }
        assertEquals(before, requestSnapshot(displayId));
        assertNull(db.app.queryForObject("SELECT resolution_summary FROM facilityflow.maintenance_request WHERE id = ?",
                String.class, requestId));
        assertEquals(0, historyCount(requestId));
        assertEquals(0, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-SH-014: completion-history insertion failure rolls back request and audit writes")
    void historyFailureRollsBackCompletionAndAudit() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 6);
        UUID requestId = db.requestId(displayId);
        String before = requestSnapshot(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        db.admin.execute("CREATE FUNCTION facilityflow.fail_t3_completion_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'history failure'; END $$");
        db.admin.execute("CREATE TRIGGER fail_t3_completion_history BEFORE INSERT ON facilityflow.request_completion_history FOR EACH ROW EXECUTE FUNCTION facilityflow.fail_t3_completion_history()");
        try {
            var failure = assertThrows(TechnicianCommandException.class,
                    () -> repository.completeWork(ACTOR, displayId, 6, "Valid resolution summary"));
            assertEquals(TechnicianCommandException.Code.UNAVAILABLE, failure.code());
        } finally {
            db.admin.execute("DROP TRIGGER IF EXISTS fail_t3_completion_history ON facilityflow.request_completion_history");
            db.admin.execute("DROP FUNCTION IF EXISTS facilityflow.fail_t3_completion_history()");
        }
        assertEquals(before, requestSnapshot(displayId));
        assertEquals(0, historyCount(requestId));
        assertEquals(0, auditCount(requestId));
    }

    @Test
    @DisplayName("REQ-TECH-007/008: replayed same-version completion creates one immutable history and audit record")
    void replayCannotDuplicateCompletionEvidence() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 2);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Existing log', 1)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        repository.completeWork(ACTOR, displayId, 2, "Initial resolution summary");
        var replay = assertThrows(TechnicianCommandException.class,
                () -> repository.completeWork(ACTOR, displayId, 2, "Another resolution summary"));

        assertEquals(TechnicianCommandException.Code.CONFLICT, replay.code());
        assertEquals(1, historyCount(requestId));
        assertEquals(1, auditCount(requestId));
        var deniedMutation = assertThrows(org.springframework.dao.DataAccessException.class, () ->
                db.app.update("UPDATE facilityflow.request_completion_history SET resolution_summary = 'Changed resolution summary' WHERE request_id = ?", requestId));
        assertNotNull(deniedMutation);
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                db.app.update("DELETE FROM facilityflow.request_completion_history WHERE request_id = ?", requestId));
    }

    @Test
    @DisplayName("REQ-TECH-007/008: a returned request appends a new completion attempt and retains prior history")
    void returnedWorkCanBeCompletedAgainWithoutOverwritingHistory() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 1);
        UUID requestId = db.requestId(displayId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'First attempt log', 5)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        repository.completeWork(ACTOR, displayId, 1, "First completion summary");

        // Synthetic Manager return and Technician restart mirror their existing owned lifecycle seams.
        db.migrator.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED', version = version + 1 WHERE id = ?", requestId);
        db.migrator.update("UPDATE facilityflow.maintenance_request SET status = 'IN_PROGRESS', version = version + 1 WHERE id = ?", requestId);
        db.migrator.update("INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes) VALUES (?, ?, 'Second attempt log', 5)",
                requestId, TechnicianCommandFixtures.TECHNICIAN);
        repository.completeWork(ACTOR, displayId, 4, "Second completion summary");

        assertEquals(2, historyCount(requestId));
        assertEquals(2, auditCount(requestId));
        assertEquals(5, db.requestVersion(displayId));
        assertTrue(db.app.queryForList("SELECT resolution_summary FROM facilityflow.request_completion_history WHERE request_id = ? ORDER BY completed_at, id", String.class, requestId)
                .containsAll(List.of("First completion summary", "Second completion summary")));
        assertEquals("Second completion summary", db.app.queryForObject(
                "SELECT resolution_summary FROM facilityflow.maintenance_request WHERE id = ?", String.class, requestId));
    }

    private static int historyCount(UUID requestId) {
        return db.app.queryForObject("SELECT count(*) FROM facilityflow.request_completion_history WHERE request_id = ?", Integer.class, requestId);
    }
    private static int auditCount(UUID requestId) {
        return db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = 'TECHNICIAN_WORK_COMPLETED' AND target_id = ?", Integer.class, requestId);
    }
    private static String auditDetails(UUID requestId) {
        return db.app.queryForObject("SELECT details::text FROM facilityflow.audit_event WHERE action = 'TECHNICIAN_WORK_COMPLETED' AND target_id = ?", String.class, requestId);
    }
    private static int auditKeyCount(UUID requestId) {
        return db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event a CROSS JOIN LATERAL jsonb_object_keys(a.details) AS entry(key) WHERE a.action = 'TECHNICIAN_WORK_COMPLETED' AND a.target_id = ?", Integer.class, requestId);
    }

    private static String requestSnapshot(String displayId) {
        return db.app.queryForObject("SELECT ROW(status, assigned_technician_id, assigned_at, manager_priority, resolution_summary, completed_by, completed_at, updated_at, version)::text FROM facilityflow.maintenance_request WHERE display_id = ?",
                String.class, displayId);
    }

    private static String attemptCompletion(String displayId, String summary, CountDownLatch ready, CountDownLatch go)
            throws Exception {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent completion timed out");
        return attemptCompletion(displayId, summary);
    }

    private static String attemptCompletion(String displayId, String summary) {
        try {
            repository.completeWork(ACTOR, displayId, 0, summary);
            return "success";
        } catch (TechnicianCommandException failure) {
            return failure.code().name();
        }
    }

    private static void awaitCompletionSutLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = db.admin.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                     WHERE application_name = 't3-completion-sut' AND wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(10);
        }
        throw new AssertionError("completion command did not reach the expected PostgreSQL row lock within five seconds");
    }
}
