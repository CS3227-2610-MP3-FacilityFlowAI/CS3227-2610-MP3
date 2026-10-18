package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.technician.TechnicianQueryRepository.ReadSession;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

class JdbcTechnicianQueryRepositoryTest {
    private static final UUID TECH = TechnicianQueryFixtures.TECHNICIAN;
    private static final UUID OTHER = TechnicianQueryFixtures.OTHER_TECHNICIAN;
    private static final UUID PRIOR = TechnicianQueryFixtures.PRIOR_TECHNICIAN;
    private static final UUID CATEGORY = TechnicianQueryFixtures.ELECTRICAL;
    private static final CurrentAccountSnapshot ACTOR =
            new CurrentAccountSnapshot(TECH, AccountRole.TECHNICIAN, 7);

    private TechnicianQueryFixtures db;
    private JdbcTechnicianQueryRepository repository;

    @BeforeEach
    void startIsolatedPostgres() throws Exception {
        db = TechnicianQueryFixtures.start();
        repository = new JdbcTechnicianQueryRepository(
                TechnicianQueryFixtures.dataSource(db.postgres, "facilityflow_app"));
    }

    @AfterEach
    void closeIsolatedPostgres() throws Exception {
        if (db != null) db.close();
    }

    @Test
    @DisplayName("REQ-TECH-001/003: assigned counts, all retained states and exact rank/time/ID order")
    void queueCountsEveryAssignedStateAndOrdersByPriorityUrgencyTimeThenId() {
        String legacy = db.app.queryForObject(
                "SELECT display_id FROM facilityflow.maintenance_request WHERE id = ?", String.class, db.legacyRequest);
        String legacyCritical = db.app.queryForObject("SELECT display_id FROM facilityflow.maintenance_request WHERE id = ?",
                String.class, db.criticalLegacyRequest);
        String criticalLate = request("Critical emergency later", TECH, "Block A", "Test Electrical",
                "ASSIGNED", "CRITICAL", "EMERGENCY", "2026-10-17T10:00:00Z");
        String criticalEarly = request("Critical emergency earlier", TECH, "Block A", "Test Electrical",
                "CANCELLED", "CRITICAL", "EMERGENCY", "2026-10-17T09:00:00Z");
        String criticalSameTime = request("Critical emergency same time", TECH, "Block A", "Test Electrical",
                "COMPLETED", "CRITICAL", "EMERGENCY", "2026-10-17T09:00:00Z");
        String criticalNormal = request("Critical normal", TECH, "Block A", "Test Plumbing",
                "CLOSED", "CRITICAL", "NORMAL", "2026-10-17T08:00:00Z");
        String highEmergency = request("High emergency", TECH, "Block A", "Test Plumbing",
                "IN_PROGRESS", "HIGH", "EMERGENCY", "2026-10-17T07:00:00Z");
        String lowKnown = request("Low known time", TECH, "Block A", "Test Electrical",
                "ASSIGNED", "LOW", "NORMAL", "2026-10-17T06:00:00Z");
        request("Other technician row", OTHER, "Block A", "Test Electrical",
                "ASSIGNED", "CRITICAL", "EMERGENCY", "2026-10-17T05:00:00Z");

        var view = serviceQueue(TECH, "", null, null, null);

        assertEquals(List.of(criticalEarly, criticalSameTime, criticalLate, legacyCritical, criticalNormal,
                        highEmergency, lowKnown, legacy),
                view.rows().stream().map(QueueRow::displayId).toList());
        assertEquals(new DashboardCounts(4, 1, 1), view.counts());
        assertEquals(8, view.assignedTotal());
        assertEquals(List.of("Test Electrical", "Test Plumbing"),
                view.categories().stream().map(CategoryOption::name).toList());
        assertEquals(List.of(RequestStatus.CANCELLED, RequestStatus.COMPLETED, RequestStatus.ASSIGNED,
                        RequestStatus.ASSIGNED, RequestStatus.CLOSED, RequestStatus.IN_PROGRESS,
                        RequestStatus.ASSIGNED, RequestStatus.ASSIGNED),
                view.rows().stream().map(QueueRow::status).toList());
        assertNull(view.rows().getLast().assignedAt());
    }

    @Test
    @DisplayName("REQ-TECH-002/SH-008: trimmed case-insensitive literal search and AND filters")
    void searchTreatsLikeWildcardsLiterallyAndCombinesFilters() {
        String percent = request("A 100% leak", TECH, "Room C", "Test Electrical",
                "IN_PROGRESS", "HIGH", "NORMAL", "2026-10-17T08:00:00Z");
        String underscore = request("Pump_under repair", TECH, "Room D", "Test Electrical",
                "ASSIGNED", "HIGH", "NORMAL", "2026-10-17T08:01:00Z");
        String escape = request("Bang! repair", TECH, "Room E", "Test Electrical",
                "ASSIGNED", "HIGH", "NORMAL", "2026-10-17T08:02:00Z");
        String locationMatch = request("Air handler check", TECH, "Pump Room North", "Test Electrical",
                "ASSIGNED", "HIGH", "NORMAL", "2026-10-17T08:03:30Z");
        request("100X leak", TECH, "Room F", "Test Electrical",
                "IN_PROGRESS", "HIGH", "NORMAL", "2026-10-17T08:04:00Z");
        request("Unrelated", OTHER, "Room C", "Test Electrical",
                "IN_PROGRESS", "HIGH", "NORMAL", "2026-10-17T08:05:00Z");

        assertEquals(List.of(percent), ids(serviceQueue(TECH, "  100%  ", null, null, null)));
        assertEquals(List.of(underscore), ids(serviceQueue(TECH, "PUMP_UNDER", null, null, null)));
        assertEquals(List.of(escape), ids(serviceQueue(TECH, "!", null, null, null)));
        assertEquals(List.of(locationMatch), ids(serviceQueue(TECH, " pump room north ", null, null, null)));
        assertEquals(List.of(locationMatch), ids(serviceQueue(TECH, locationMatch, null, null, null)));
        assertEquals(List.of(percent), ids(serviceQueue(TECH, "100%", RequestStatus.IN_PROGRESS,
                CATEGORY, ManagerPriority.HIGH)));
        assertEquals(List.of(), ids(serviceQueue(TECH, "100%", RequestStatus.ASSIGNED,
                CATEGORY, ManagerPriority.HIGH)));
    }

    @Test
    @DisplayName("REQ-TECH-002: a valid but unknown category is an empty result, never an unfiltered queue")
    void unknownCategoryUuidReturnsNoRows() {
        request("Electrical request", TECH, "Room A", "Test Electrical", "ASSIGNED", "MEDIUM", "NORMAL",
                "2026-10-17T08:00:00Z");

        var view = serviceQueue(TECH, "", null, UUID.randomUUID(), null);

        assertTrue(view.rows().isEmpty());
        assertEquals(3, view.assignedTotal());
        assertEquals(3, view.counts().assigned());
    }

    @Test
    @DisplayName("REQ-SH-014: query snapshots run read-only at repeatable-read isolation")
    void readSnapshotUsesReadOnlyRepeatableReadTransaction() {
        repository.readSnapshot(session -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(4, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            return null;
        });
    }

    @Test
    @DisplayName("REQ-TECH-009: an operational PostgreSQL read failure becomes a generic unavailable outcome")
    void operationalReadFailureDoesNotExposeSqlOrPartialData() {
        db.admin.execute("REVOKE SELECT ON facilityflow.user_account FROM facilityflow_app");

        var failure = assertThrows(TechnicianQueryException.class, () -> repository.readSnapshot(session -> {
            session.requireCurrentTechnician(ACTOR);
            return session.queue(TECH, emptyFilter());
        }));

        assertEquals(TechnicianQueryException.Code.UNAVAILABLE, failure.code());
        assertEquals("Assigned work is temporarily unavailable. Please try again.", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-001: persisted active role and exact session version gate reads")
    void requiresPersistedActiveTechnicianAndExactNonnegativeSessionVersion() {
        request("Protected assignment", TECH, "Room A", "Test Electrical", "ASSIGNED", "HIGH", "NORMAL",
                "2026-10-17T08:00:00Z");
        var wrongVersion = new CurrentAccountSnapshot(TECH, AccountRole.TECHNICIAN, 6);
        var wrongRole = new CurrentAccountSnapshot(TECH, AccountRole.REQUESTER, 7);
        var negativeVersion = new CurrentAccountSnapshot(TECH, AccountRole.TECHNICIAN, -1);
        var inactiveTech = UUID.fromString("74000000-0000-0000-0000-000000000007");
        db.admin.update("INSERT INTO facilityflow.user_account (id, username, display_name, password_hash, role, active, session_version) "
                + "VALUES (?, 'inactive-tech', 'Inactive Technician', 'synthetic', 'TECHNICIAN', false, 0)", inactiveTech);

        assertThrows(AccessDeniedException.class, () -> readWith(wrongVersion, session -> session.queue(TECH, emptyFilter())));
        assertThrows(AccessDeniedException.class, () -> readWith(wrongRole, session -> session.queue(TECH, emptyFilter())));
        assertThrows(AccessDeniedException.class, () -> readWith(negativeVersion, session -> session.queue(TECH, emptyFilter())));
        assertThrows(AccessDeniedException.class, () -> readWith(
                new CurrentAccountSnapshot(inactiveTech, AccountRole.TECHNICIAN, 0),
                session -> session.queue(inactiveTech, emptyFilter())));
        assertThrows(AccessDeniedException.class, () -> repository.readSnapshot(session -> session.queue(TECH, emptyFilter())));
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-001: a snapshot stays bound to its first successfully authenticated actor")
    void deniedActorSwitchCannotAuthorizeASecondTechnicianOnRetry() {
        request("First technician assignment", TECH, "Room A", "Test Electrical", "ASSIGNED", "HIGH", "NORMAL",
                "2026-10-17T08:00:00Z");
        request("Second technician assignment", OTHER, "Room B", "Test Plumbing", "ASSIGNED", "MEDIUM", "NORMAL",
                "2026-10-17T09:00:00Z");
        var otherActor = new CurrentAccountSnapshot(OTHER, AccountRole.TECHNICIAN, 3);

        repository.readSnapshot(session -> {
            session.requireCurrentTechnician(ACTOR);
            assertThrows(AccessDeniedException.class, () -> session.requireCurrentTechnician(otherActor));
            assertThrows(AccessDeniedException.class, () -> session.requireCurrentTechnician(otherActor));
            assertThrows(AccessDeniedException.class, () -> session.queue(OTHER, emptyFilter()));
            assertThrows(AccessDeniedException.class, () -> session.queue(TECH, emptyFilter()));
            session.requireCurrentTechnician(ACTOR);
            assertFalse(session.queue(TECH, emptyFilter()).isEmpty());
            return null;
        });
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-001: a failed recheck cannot clear the original actor binding")
    void staleReauthenticationFailureCannotUnlockAnotherActor() {
        request("First technician assignment", TECH, "Room A", "Test Electrical", "ASSIGNED", "HIGH", "NORMAL",
                "2026-10-17T08:00:00Z");
        request("Second technician assignment", OTHER, "Room B", "Test Plumbing", "ASSIGNED", "MEDIUM", "NORMAL",
                "2026-10-17T09:00:00Z");
        var staleActor = new CurrentAccountSnapshot(TECH, AccountRole.TECHNICIAN, 6);
        var otherActor = new CurrentAccountSnapshot(OTHER, AccountRole.TECHNICIAN, 3);

        repository.readSnapshot(session -> {
            session.requireCurrentTechnician(ACTOR);
            assertThrows(AccessDeniedException.class, () -> session.requireCurrentTechnician(staleActor));
            assertThrows(AccessDeniedException.class, () -> session.queue(TECH, emptyFilter()));
            assertThrows(AccessDeniedException.class, () -> session.requireCurrentTechnician(otherActor));
            assertThrows(AccessDeniedException.class, () -> session.queue(OTHER, emptyFilter()));
            session.requireCurrentTechnician(ACTOR);
            assertFalse(session.queue(TECH, emptyFilter()).isEmpty());
            return null;
        });
    }

    @Test
    @DisplayName("REQ-SH-002: a nested read snapshot cannot reuse the suspended outer snapshot capability")
    void nestedSnapshotUsesItsOwnActorAndOuterSessionResumesOnlyAfterward() {
        request("First technician assignment", TECH, "Room A", "Test Electrical", "ASSIGNED", "HIGH", "NORMAL",
                "2026-10-17T08:00:00Z");
        request("Second technician assignment", OTHER, "Room B", "Test Plumbing", "ASSIGNED", "MEDIUM", "NORMAL",
                "2026-10-17T09:00:00Z");
        var otherActor = new CurrentAccountSnapshot(OTHER, AccountRole.TECHNICIAN, 3);

        repository.readSnapshot(outer -> {
            outer.requireCurrentTechnician(ACTOR);
            assertFalse(outer.queue(TECH, emptyFilter()).isEmpty());
            repository.readSnapshot(inner -> {
                assertThrows(IllegalStateException.class, () -> outer.counts(TECH));
                assertThrows(IllegalStateException.class, () -> outer.queue(TECH, emptyFilter()));
                inner.requireCurrentTechnician(otherActor);
                assertFalse(inner.queue(OTHER, emptyFilter()).isEmpty());
                return null;
            });
            assertFalse(outer.queue(TECH, emptyFilter()).isEmpty());
            return null;
        });
    }

    @Test
    @DisplayName("REQ-TECH-009: current assignee reads both histories with original authors after reassignment")
    void historiesStayRequestScopedAndKeepPriorTechnicianAuthorship() {
        String displayId = request("Reassigned request", TECH, "Room B", "Test Electrical",
                "IN_PROGRESS", "HIGH", "NORMAL", "2026-10-17T08:00:00Z");
        UUID requestId = db.requestId(displayId);
        Instant first = Instant.parse("2026-10-17T09:00:00Z");
        Instant second = Instant.parse("2026-10-17T10:00:00Z");
        db.addUpdate(UUID.fromString("75000000-0000-0000-0000-000000000001"), requestId,
                TechnicianQueryFixtures.REQUESTER, "Requester update", first);
        db.addUpdate(UUID.fromString("75000000-0000-0000-0000-000000000002"), requestId,
                TechnicianQueryFixtures.REQUESTER, "Tie update first", second);
        db.addUpdate(UUID.fromString("75000000-0000-0000-0000-000000000003"), requestId,
                TechnicianQueryFixtures.REQUESTER, "Tie update second", second);
        db.addWorkLog(UUID.fromString("75000000-0000-0000-0000-000000000011"), requestId,
                PRIOR, "Earlier technician work", 35, first);
        db.addWorkLog(UUID.fromString("75000000-0000-0000-0000-000000000012"), requestId,
                TECH, "Tie log first", 20, second);
        db.addWorkLog(UUID.fromString("75000000-0000-0000-0000-000000000013"), requestId,
                TECH, "Tie log second", 15, second);
        db.addWorkLog(UUID.fromString("75000000-0000-0000-0000-000000000014"), requestId,
                TECH, "Current technician work", 20, second);

        var detail = detail(TECH, displayId);
        assertEquals(List.of("Requester update", "Tie update first", "Tie update second"),
                detail.requesterUpdates().stream().map(RequesterUpdateView::note).toList());
        assertEquals(List.of("Requester One", "Requester One", "Requester One"), detail.requesterUpdates().stream()
                .map(RequesterUpdateView::authorDisplayName).toList());
        assertEquals(List.of("Earlier technician work", "Tie log first", "Tie log second", "Current technician work"),
                detail.workLogs().stream().map(WorkLogView::note).toList());
        assertEquals(List.of("Prior Technician", "Technician One", "Technician One", "Technician One"),
                detail.workLogs().stream().map(WorkLogView::authorDisplayName).toList());
        assertTrue(detail(OTHER, displayId) == null);

        db.app.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ?, assigned_at = ? WHERE id = ?",
                OTHER, java.time.OffsetDateTime.parse("2026-10-18T03:00:00Z"), requestId);
        var reassignedDetail = detail(OTHER, displayId);
        assertEquals(List.of("Prior Technician", "Technician One", "Technician One", "Technician One"), reassignedDetail.workLogs().stream()
                .map(WorkLogView::authorDisplayName).toList());
        assertTrue(detail(TECH, displayId) == null);
    }

    @Test
    @DisplayName("REQ-SH-002: a read session expires after its repeatable snapshot and sees concurrent changes next time")
    void snapshotIsRepeatableAndSessionCannotEscapeCallback() {
        String displayId = request("Snapshot assignment", TECH, "Room A", "Test Electrical", "ASSIGNED", "HIGH", "NORMAL",
                "2026-10-17T08:00:00Z");
        UUID requestId = db.requestId(displayId);
        final ReadSession[] retained = new ReadSession[1];

        long inSnapshot = repository.readSnapshot(session -> {
            session.requireCurrentTechnician(ACTOR);
            retained[0] = session;
            db.admin.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ?, assigned_at = ? WHERE id = ?",
                    OTHER, java.time.OffsetDateTime.parse("2026-10-18T03:00:00Z"), requestId);
            db.admin.update("UPDATE facilityflow.user_account SET active = false, session_version = 8 WHERE id = ?", TECH);
            assertEquals(3, session.counts(TECH).assigned());
            assertEquals(1, session.categories(TECH).size());
            assertEquals(displayId, session.detail(TECH, displayId).orElseThrow().request().displayId());
            return session.queue(TECH, new QueueFilter("", null, null, ManagerPriority.HIGH)).size();
        });
        assertEquals(1, inSnapshot);
        assertThrows(IllegalStateException.class, () -> retained[0].queue(TECH, emptyFilter()));
        assertThrows(AccessDeniedException.class, () -> queueAs(TECH));
        assertEquals(1, queueAs(OTHER).rows().size());
    }

    private String request(String title, UUID assignee, String location, String category,
                           String status, String priority, String urgency, String at) {
        return db.assignedRequest(assignee, title, location, category, status, priority, urgency,
                Instant.parse(at));
    }

    private QueueView serviceQueue(UUID actorId, String query, RequestStatus status, UUID category,
                                   ManagerPriority priority) {
        CurrentAccountSnapshot actor = actorId.equals(TECH) ? ACTOR
                : new CurrentAccountSnapshot(actorId, AccountRole.TECHNICIAN, actorId.equals(OTHER) ? 3 : 2);
        return repository.readSnapshot(session -> {
            session.requireCurrentTechnician(actor);
            return new QueueView(new QueueFilter(query, status, category, priority), session.counts(actorId),
                    session.categories(actorId), session.assignedTotal(actorId), session.queue(actorId,
                    new QueueFilter(query, status, category, priority)));
        });
    }

    private RequestDetail detail(UUID actorId, String displayId) {
        CurrentAccountSnapshot actor = actorId.equals(TECH) ? ACTOR
                : new CurrentAccountSnapshot(actorId, AccountRole.TECHNICIAN, actorId.equals(OTHER) ? 3 : 2);
        return repository.readSnapshot(session -> {
            session.requireCurrentTechnician(actor);
            return session.detail(actorId, displayId).orElse(null);
        });
    }

    private <T> T readWith(CurrentAccountSnapshot actor, java.util.function.Function<ReadSession, T> query) {
        return repository.readSnapshot(session -> {
            session.requireCurrentTechnician(actor);
            return query.apply(session);
        });
    }

    private QueueView queueAs(UUID actorId) {
        return serviceQueue(actorId, "", null, null, null);
    }

    private static QueueFilter emptyFilter() {
        return new QueueFilter("", null, null, null);
    }

    private static List<String> ids(QueueView view) {
        return view.rows().stream().map(QueueRow::displayId).toList();
    }
}
