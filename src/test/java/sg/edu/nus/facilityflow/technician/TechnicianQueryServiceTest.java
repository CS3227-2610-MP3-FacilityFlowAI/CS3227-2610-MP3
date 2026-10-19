package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;
import sg.edu.nus.facilityflow.technician.TechnicianQueryRepository.ReadSession;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

class TechnicianQueryServiceTest {
    private static final UUID TECHNICIAN = TechnicianQueryFixtures.TECHNICIAN;
    private static final UUID CATEGORY = TechnicianQueryFixtures.ELECTRICAL;
    private static final CurrentAccountSnapshot ACTOR =
            new CurrentAccountSnapshot(TECHNICIAN, AccountRole.TECHNICIAN, 7);

    private IdentityAuthorization authorization;
    private SnapshotRepository repository;
    private TechnicianQueryService service;

    @BeforeEach
    void setUp() {
        authorization = mock(IdentityAuthorization.class);
        when(authorization.requireRoleSnapshot(AccountRole.TECHNICIAN)).thenReturn(ACTOR);
        repository = new SnapshotRepository();
        service = new TechnicianQueryService(authorization, repository);
    }

    @Test
    @DisplayName("REQ-TECH-001/002: one authenticated snapshot supplies filters and unfiltered counts")
    void queueUsesOneScopedSnapshotAndKeepsTheSubmittedFilter() {
        var row = new QueueRow("FF-000101", "Ceiling light flickers", "Room 2", CATEGORY,
                "Test Electrical", RequestStatus.CLOSED, ManagerPriority.HIGH, ReportedUrgency.NORMAL,
                Instant.parse("2026-10-17T08:00:00Z"));
        repository.session.rows = List.of(row);
        repository.session.categories = List.of(new CategoryOption(CATEGORY, "Test Electrical"));
        repository.session.counts = new DashboardCounts(2, 1, 1);
        repository.session.total = 5;

        QueueFilter filter = new QueueFilter("  Flicker  ", RequestStatus.CLOSED, CATEGORY, ManagerPriority.HIGH);
        QueueView view = service.queue(filter);

        assertEquals("Flicker", view.filter().q());
        assertEquals(filter.status(), view.filter().status());
        assertEquals(filter.categoryId(), view.filter().categoryId());
        assertEquals(filter.priority(), view.filter().priority());
        assertEquals(new DashboardCounts(2, 1, 1), view.counts());
        assertEquals(5, view.assignedTotal());
        assertEquals(List.of(row), view.rows());
        assertEquals(List.of("persisted-authority", "counts", "categories", "total", "queue"),
                repository.session.calls);
        assertEquals(1, repository.snapshots);
        assertEquals(TECHNICIAN, repository.session.actor.id());
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-001: persisted identity rejection prevents any Request reads")
    void persistedIdentityFailurePrecedesAllAssignedDataReads() {
        repository.session.authorityFailure = new AccessDeniedException("not available");

        assertThrows(AccessDeniedException.class, () -> service.queue(new QueueFilter("", null, null, null)));

        assertEquals(List.of("persisted-authority"), repository.session.calls);
        assertEquals(1, repository.snapshots);
    }

    @Test
    @DisplayName("REQ-TECH-009: malformed, missing and unassigned detail requests share one safe outcome")
    void unavailableDetailSyntaxAndAssignmentAreIndistinguishable() {
        var malformed = assertThrows(TechnicianQueryException.class, () -> service.detail("FF-1"));
        var unknown = assertThrows(TechnicianQueryException.class, () -> service.detail("FF-999999"));
        var unassigned = assertThrows(TechnicianQueryException.class, () -> service.detail("FF-000102"));

        assertEquals(TechnicianQueryException.Code.NOT_AVAILABLE, malformed.code());
        assertEquals(malformed.code(), unknown.code());
        assertEquals(malformed.code(), unassigned.code());
        assertEquals(malformed.getMessage(), unknown.getMessage());
        assertEquals(malformed.getMessage(), unassigned.getMessage());
        assertTrue(repository.session.detailCalls.contains("FF-999999"));
        assertTrue(repository.session.detailCalls.contains("FF-000102"));
        assertEquals(2, repository.session.detailCalls.size());
    }

    @Test
    @DisplayName("REQ-TECH-002: a null filter is rejected without broadening to an unfiltered queue")
    void nullFilterFailsBeforeOpeningADataSnapshot() {
        var failure = assertThrows(TechnicianQueryException.class, () -> service.queue(null));

        assertEquals(TechnicianQueryException.Code.INVALID_FILTER, failure.code());
        assertEquals(0, repository.snapshots);
    }

    @Test
    @DisplayName("REQ-SH-002: snapshot sessions cannot be used after the callback returns")
    void readSessionClosesAfterTheSnapshotCallback() {
        service.queue(new QueueFilter("", null, null, null));

        assertThrows(IllegalStateException.class, () -> repository.retained.counts(TECHNICIAN));
    }

    private static final class SnapshotRepository implements TechnicianQueryRepository {
        private final FakeReadSession session = new FakeReadSession();
        private ReadSession retained;
        private int snapshots;

        @Override
        public <T> T readSnapshot(java.util.function.Function<ReadSession, T> reader) {
            snapshots++;
            session.open();
            retained = session;
            try {
                return reader.apply(session);
            } finally {
                session.close();
            }
        }
    }

    private static final class FakeReadSession implements ReadSession {
        private final List<String> calls = new java.util.ArrayList<>();
        private final List<String> detailCalls = new java.util.ArrayList<>();
        private boolean closed;
        private CurrentAccountSnapshot actor;
        private RuntimeException authorityFailure;
        private DashboardCounts counts = new DashboardCounts(0, 0, 0);
        private List<CategoryOption> categories = List.of();
        private List<QueueRow> rows = List.of();
        private long total;

        private void ensureOpen() {
            if (closed) throw new IllegalStateException("session closed");
        }

        @Override
        public void requireCurrentTechnician(CurrentAccountSnapshot actor) {
            ensureOpen();
            calls.add("persisted-authority");
            if (authorityFailure != null) throw authorityFailure;
            this.actor = actor;
        }

        @Override
        public DashboardCounts counts(UUID actorId) {
            ensureOpen();
            calls.add("counts");
            assertEquals(TECHNICIAN, actorId);
            return counts;
        }

        @Override
        public long assignedTotal(UUID actorId) {
            ensureOpen();
            calls.add("total");
            assertEquals(TECHNICIAN, actorId);
            return total;
        }

        @Override
        public List<CategoryOption> categories(UUID actorId) {
            ensureOpen();
            calls.add("categories");
            assertEquals(TECHNICIAN, actorId);
            return categories;
        }

        @Override
        public List<QueueRow> queue(UUID actorId, QueueFilter filter) {
            ensureOpen();
            calls.add("queue");
            assertEquals(TECHNICIAN, actorId);
            return rows;
        }

        @Override
        public Optional<RequestDetail> detail(UUID actorId, String displayId) {
            ensureOpen();
            assertEquals(TECHNICIAN, actorId);
            detailCalls.add(displayId);
            return Optional.empty();
        }

        private void close() {
            closed = true;
        }

        private void open() {
            closed = false;
            actor = null;
        }
    }
}
