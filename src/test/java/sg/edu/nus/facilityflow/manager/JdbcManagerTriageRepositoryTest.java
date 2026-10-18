package sg.edu.nus.facilityflow.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

class JdbcManagerTriageRepositoryTest {
    private static final UUID MANAGER_ID = UUID.fromString("44000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICIAN_ID = UUID.fromString("44000000-0000-0000-0000-000000000002");
    private static final UUID REQUEST_ID = UUID.fromString("44000000-0000-0000-0000-000000000003");
    private static final UUID HVAC_ID = UUID.fromString("44000000-0000-0000-0000-000000000004");
    private static final UUID ELECTRICAL_ID = UUID.fromString("44000000-0000-0000-0000-000000000005");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcManagerTriageRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:triage_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().execute("CREATE SCHEMA facilityflow");
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.user_account (
                    id uuid PRIMARY KEY, username varchar(100) NOT NULL, role varchar(40) NOT NULL,
                    active boolean NOT NULL, session_version bigint NOT NULL)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.category (
                    id uuid PRIMARY KEY, name varchar(50) NOT NULL, active boolean NOT NULL, version bigint NOT NULL)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.skill_tag (
                    id uuid PRIMARY KEY, name varchar(50) NOT NULL, active boolean NOT NULL, version bigint NOT NULL)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.technician_skill (
                    technician_id uuid NOT NULL, skill_tag_id uuid NOT NULL)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.maintenance_request (
                    id uuid PRIMARY KEY, display_id varchar(20) NOT NULL, title varchar(100) NOT NULL,
                    description varchar(2000) NOT NULL, location varchar(120) NOT NULL,
                    category_id uuid NOT NULL, reported_urgency varchar(20) NOT NULL,
                    manager_priority varchar(20), status varchar(20) NOT NULL, version bigint NOT NULL,
                    assigned_technician_id uuid)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.requester_update (
                    id uuid PRIMARY KEY, request_id uuid NOT NULL, created_at timestamp NOT NULL, note varchar(1000) NOT NULL)
                """);
        jdbc.update("INSERT INTO facilityflow.user_account VALUES (:id, 'manager', 'FACILITIES_MANAGER', true, 7)",
                Map.of("id", MANAGER_ID));
        jdbc.update("INSERT INTO facilityflow.user_account VALUES (:id, 'tech-one', 'TECHNICIAN', true, 1)",
                Map.of("id", TECHNICIAN_ID));
        jdbc.update("INSERT INTO facilityflow.category VALUES (:id, 'HVAC', true, 2)", Map.of("id", HVAC_ID));
        jdbc.update("INSERT INTO facilityflow.skill_tag VALUES (:id, 'Electrical', true, 3)", Map.of("id", ELECTRICAL_ID));
        jdbc.update("INSERT INTO facilityflow.technician_skill VALUES (:technicianId, :skillId)",
                Map.of("technicianId", TECHNICIAN_ID, "skillId", ELECTRICAL_ID));
        jdbc.update("""
                INSERT INTO facilityflow.maintenance_request
                    (id, display_id, title, description, location, category_id, reported_urgency,
                     manager_priority, status, version, assigned_technician_id)
                VALUES (:id, 'FF-000051', 'Leaking air conditioner', 'Water is pooling below the unit.',
                        'Block A', :categoryId, 'HIGH', NULL, 'OPEN', 3, NULL)
                """, new MapSqlParameterSource().addValue("id", REQUEST_ID).addValue("categoryId", HVAC_ID));
        jdbc.update("""
                INSERT INTO facilityflow.requester_update (id, request_id, created_at, note)
                VALUES (:id, :requestId, TIMESTAMP '2026-10-17 07:00:00', 'The leak worsened today.')
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("requestId", REQUEST_ID));
        repository = new JdbcManagerTriageRepository(jdbc);
    }

    @Test
    @DisplayName("REQ-MGR-020/021 context is Manager-scoped, bounded and oldest-to-newest")
    void probeAndContextBuildAllowlistedFacts() {
        CurrentAccountSnapshot manager = snapshot();
        ManagerTriageRepository.ContextSnapshot context = repository.loadContext(
                repository.probe("FF-000051", manager).orElseThrow());

        assertEquals(List.of("The leak worsened today."), context.assistantContext().requesterUpdates());
        assertEquals(List.of("HVAC"), context.assistantContext().activeCategoryLabels());
        assertEquals(List.of("Electrical"), context.assistantContext().activeSkillLabels());
        assertEquals(TECHNICIAN_ID, context.candidates().get(0).accountId());
        assertTrue(repository.probe("FF-000051", new CurrentAccountSnapshot(
                UUID.randomUUID(), AccountRole.FACILITIES_MANAGER, 7)).isEmpty());
    }

    @Test
    @DisplayName("REQ-MGR-025 Request, session and skill-catalogue changes invalidate triage")
    void freshnessRecheckRejectsChangedState() {
        CurrentAccountSnapshot manager = snapshot();
        ManagerTriageRepository.ContextSnapshot context = repository.loadContext(
                repository.probe("FF-000051", manager).orElseThrow());
        TriageResult result = new TriageResult("HVAC", "High", List.of(), List.of("Electrical"), List.of(), "Review.");

        assertTrue(repository.isStillEligible(context, manager, result));
        jdbc.update("UPDATE facilityflow.skill_tag SET version = 4 WHERE id = :id", Map.of("id", ELECTRICAL_ID));
        assertFalse(repository.isStillEligible(context, manager, result));
        jdbc.update("UPDATE facilityflow.skill_tag SET version = 3 WHERE id = :id", Map.of("id", ELECTRICAL_ID));
        jdbc.update("UPDATE facilityflow.maintenance_request SET version = 4 WHERE id = :id", Map.of("id", REQUEST_ID));
        assertFalse(repository.isStillEligible(context, manager, result));
        jdbc.update("UPDATE facilityflow.maintenance_request SET version = 3 WHERE id = :id", Map.of("id", REQUEST_ID));
        jdbc.update("UPDATE facilityflow.user_account SET session_version = 8 WHERE id = :id", Map.of("id", MANAGER_ID));
        assertFalse(repository.isStillEligible(context, manager, result));
    }

    private static CurrentAccountSnapshot snapshot() {
        return new CurrentAccountSnapshot(MANAGER_ID, AccountRole.FACILITIES_MANAGER, 7);
    }
}
