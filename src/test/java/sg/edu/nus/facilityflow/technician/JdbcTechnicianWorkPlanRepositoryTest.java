package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Real isolated PostgreSQL checks for REQ-TECH-012/013/017 persistence boundaries. */
class JdbcTechnicianWorkPlanRepositoryTest {
    private static final CurrentAccountSnapshot ACTOR = new CurrentAccountSnapshot(
            TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, 7);
    private static TechnicianCommandFixtures db;
    private static JdbcTechnicianWorkPlanRepository repository;

    @BeforeAll
    static void startIsolatedPostgres() throws Exception {
        db = TechnicianCommandFixtures.start();
        DataSource source = TechnicianCommandFixtures.dataSource(db.postgres, "facilityflow_app");
        ((PGSimpleDataSource) source).setApplicationName("t4-work-plan-sut");
        repository = new JdbcTechnicianWorkPlanRepository(new NamedParameterJdbcTemplate(source));
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
    @DisplayName("REQ-TECH-012: persisted active Technician, session, assignment and allowed state gate context")
    void loadRequiresCurrentPersistedTechnicianAndAssignedRequest() {
        String assigned = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 12);
        String otherAssignee = db.request(TechnicianCommandFixtures.OTHER_TECHNICIAN, "ASSIGNED", 1);
        String open = db.request(null, "OPEN", 0);
        String inProgress = db.request(TechnicianCommandFixtures.TECHNICIAN, "IN_PROGRESS", 5);
        String completed = db.request(TechnicianCommandFixtures.TECHNICIAN, "COMPLETED", 6);

        assertTrue(repository.loadEligible(assigned, ACTOR).isPresent());
        assertTrue(repository.loadEligible(inProgress, ACTOR).isPresent());
        assertTrue(repository.loadEligible(otherAssignee, ACTOR).isEmpty());
        assertTrue(repository.loadEligible(open, ACTOR).isEmpty());
        assertTrue(repository.loadEligible(completed, ACTOR).isEmpty());
        assertTrue(repository.loadEligible(assigned, new CurrentAccountSnapshot(
                TechnicianCommandFixtures.OTHER_TECHNICIAN, AccountRole.TECHNICIAN, 3)).isEmpty());
        assertTrue(repository.loadEligible(assigned, new CurrentAccountSnapshot(
                TechnicianCommandFixtures.TECHNICIAN, AccountRole.REQUESTER, 7)).isEmpty());
        assertTrue(repository.loadEligible(assigned, new CurrentAccountSnapshot(
                TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, -1)).isEmpty());

        db.admin.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertTrue(repository.loadEligible(assigned, ACTOR).isEmpty());
        db.admin.update("UPDATE facilityflow.user_account SET active = true, role = 'REQUESTER' WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertTrue(repository.loadEligible(assigned, ACTOR).isEmpty());
        db.admin.update("UPDATE facilityflow.user_account SET role = 'TECHNICIAN', session_version = 8 WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertTrue(repository.loadEligible(assigned, ACTOR).isEmpty());
        db.admin.update("UPDATE facilityflow.user_account SET session_version = 7 WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        db.admin.update("UPDATE facilityflow.category SET active = false WHERE id = ?",
                TechnicianCommandFixtures.ELECTRICAL);
        assertTrue(repository.loadEligible(assigned, ACTOR).isEmpty());
    }

    @Test
    @DisplayName("REQ-TECH-013: selects newest ten by timestamp then UUID and returns them oldest-first")
    void selectsOnlyLatestTenWithStableTieOrderAndOldestFirstMapping() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 2);
        UUID requestId = db.requestId(displayId);
        Instant base = Instant.parse("2026-10-18T00:00:00Z");
        List<UUID> ids = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID.fromString("80000000-0000-0000-0000-000000000000"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID.fromString("10000000-0000-0000-0000-000000000000"),
                UUID.fromString("20000000-0000-0000-0000-000000000000"),
                UUID.fromString("30000000-0000-0000-0000-000000000000"),
                UUID.fromString("40000000-0000-0000-0000-000000000000"),
                UUID.fromString("50000000-0000-0000-0000-000000000000"),
                UUID.fromString("60000000-0000-0000-0000-000000000000"));
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            Instant created = i < 2 ? base : base.plusSeconds(1);
            db.admin.update("INSERT INTO facilityflow.requester_update (id, request_id, author_id, note, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)", id, requestId, TechnicianCommandFixtures.REQUESTER,
                    "update-" + i, TechnicianCommandFixtures.utc(created));
        }
        int requestVersion = db.admin.queryForObject(
                "SELECT version FROM facilityflow.maintenance_request WHERE id = ?", Integer.class, requestId);
        int auditBefore = db.admin.queryForObject(
                "SELECT count(*) FROM facilityflow.audit_event WHERE target_id = ?", Integer.class, requestId);

        WorkPlanContext context = repository.loadEligible(displayId, ACTOR).orElseThrow();

        List<UUID> expected = ids.subList(2, 12).stream()
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        assertEquals(expected, context.selectedRequesterUpdates().stream().map(u -> u.id()).toList());
        assertEquals(expected.stream().map(id -> "update-" + ids.indexOf(id)).toList(),
                context.selectedRequesterUpdates().stream().map(u -> u.note()).toList());
        assertEquals(requestVersion, db.requestVersion(displayId));
        assertEquals(auditBefore, db.admin.queryForObject(
                "SELECT count(*) FROM facilityflow.audit_event WHERE target_id = ?", Integer.class, requestId));
        assertEquals(0, db.admin.queryForObject(
                "SELECT count(*) FROM facilityflow.work_log WHERE request_id = ?", Integer.class, requestId));
    }

    @Test
    @DisplayName("REQ-TECH-017/AI-SEC-028: exact original status, version, assignee and session must remain eligible")
    void recheckRejectsEveryPersistedAccessOrFreshnessChange() {
        String displayId = db.request(TechnicianCommandFixtures.TECHNICIAN, "ASSIGNED", 4);
        WorkPlanContext context = repository.loadEligible(displayId, ACTOR).orElseThrow();
        assertTrue(repository.isStillEligible(context, ACTOR));
        assertFalse(repository.isStillEligible(context, new CurrentAccountSnapshot(
                TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, 8)));
        assertFalse(repository.isStillEligible(context, new CurrentAccountSnapshot(
                TechnicianCommandFixtures.TECHNICIAN, AccountRole.REQUESTER, 7)));

        db.admin.update("UPDATE facilityflow.maintenance_request SET version = version + 1 WHERE display_id = ?", displayId);
        assertFalse(repository.isStillEligible(context, ACTOR));
        db.admin.update("UPDATE facilityflow.maintenance_request SET version = 4, status = 'IN_PROGRESS' WHERE display_id = ?", displayId);
        assertFalse(repository.isStillEligible(context, ACTOR));
        db.admin.update("UPDATE facilityflow.maintenance_request SET status = 'ASSIGNED', assigned_technician_id = ? WHERE display_id = ?",
                TechnicianCommandFixtures.OTHER_TECHNICIAN, displayId);
        assertFalse(repository.isStillEligible(context, ACTOR));
        db.admin.update("UPDATE facilityflow.maintenance_request SET assigned_technician_id = ? WHERE display_id = ?",
                TechnicianCommandFixtures.TECHNICIAN, displayId);
        db.admin.update("UPDATE facilityflow.user_account SET session_version = 8 WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertFalse(repository.isStillEligible(context, ACTOR));
        db.admin.update("UPDATE facilityflow.user_account SET session_version = 7, active = false WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        assertFalse(repository.isStillEligible(context, ACTOR));

        db.admin.update("UPDATE facilityflow.user_account SET active = true WHERE id = ?",
                TechnicianCommandFixtures.TECHNICIAN);
        db.admin.update("UPDATE facilityflow.category SET active = false WHERE id = ?",
                TechnicianCommandFixtures.ELECTRICAL);
        assertFalse(repository.isStillEligible(context, ACTOR));
    }
}
