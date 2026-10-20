package sg.edu.nus.facilityflow.requester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

class JdbcRequesterSmartReportRepositoryTest {
    private static final UUID REQUESTER_ID = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("42000000-0000-0000-0000-000000000002");
    private static final UUID REQUEST_ID = UUID.fromString("42000000-0000-0000-0000-000000000003");
    private static final UUID ELECTRICAL_ID = UUID.fromString("42000000-0000-0000-0000-000000000004");
    private static final UUID PLUMBING_ID = UUID.fromString("42000000-0000-0000-0000-000000000005");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcRequesterSmartReportRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:smart_report_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().execute("CREATE SCHEMA facilityflow");
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.user_account (
                    id uuid PRIMARY KEY, role varchar(40) NOT NULL,
                    active boolean NOT NULL, session_version bigint NOT NULL)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.category (
                    id uuid PRIMARY KEY, name varchar(50) NOT NULL,
                    active boolean NOT NULL, version bigint NOT NULL)
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE facilityflow.maintenance_request (
                    id uuid PRIMARY KEY, display_id varchar(20) NOT NULL,
                    requester_id uuid NOT NULL, status varchar(30) NOT NULL,
                    version bigint NOT NULL)
                """);
        update("INSERT INTO facilityflow.user_account VALUES (:id, 'REQUESTER', true, 7)", REQUESTER_ID);
        update("INSERT INTO facilityflow.user_account VALUES (:id, 'REQUESTER', true, 1)", OTHER_ID);
        jdbc.update("""
                INSERT INTO facilityflow.maintenance_request
                    (id, display_id, requester_id, status, version)
                VALUES (:id, 'FF-000042', :requesterId, 'OPEN', 3)
                """, new MapSqlParameterSource()
                .addValue("id", REQUEST_ID).addValue("requesterId", REQUESTER_ID));
        insertCategory(ELECTRICAL_ID, "Electrical", 2);
        insertCategory(PLUMBING_ID, "Plumbing", 1);
        repository = new JdbcRequesterSmartReportRepository(jdbc);
    }

    @Test
    @DisplayName("REQ-RQ-012/013 edit probe enforces owner and builds sorted active catalogue context")
    void editProbeAndContextAreOwnerScopedAndAllowlisted() {
        CurrentAccountSnapshot requester = snapshot(REQUESTER_ID, 7);

        var eligibility = repository.probeEdit("FF-000042", requester).orElseThrow();
        var context = repository.loadContext(eligibility, input());

        assertEquals(List.of("Electrical", "Plumbing"), context.assistantContext().activeCategoryLabels());
        assertTrue(repository.probeEdit("FF-000042", snapshot(OTHER_ID, 1)).isEmpty());
        assertTrue(repository.probeCreate(requester).isPresent());
    }

    @Test
    @DisplayName("RQ-AI04/AI-SEC-028 session, request and catalogue changes invalidate the result")
    void freshnessRecheckRejectsChangedPersistedState() {
        CurrentAccountSnapshot requester = snapshot(REQUESTER_ID, 7);
        var context = repository.loadContext(
                repository.probeEdit("FF-000042", requester).orElseThrow(), input());
        SmartReportResult result = result();

        assertTrue(repository.isStillEligible(context, requester, result));

        jdbc.update("UPDATE facilityflow.category SET version = 3 WHERE id = :id",
                java.util.Map.of("id", ELECTRICAL_ID));
        assertFalse(repository.isStillEligible(context, requester, result));

        jdbc.update("UPDATE facilityflow.category SET version = 2 WHERE id = :id",
                java.util.Map.of("id", ELECTRICAL_ID));
        jdbc.update("UPDATE facilityflow.maintenance_request SET version = 4 WHERE id = :id",
                java.util.Map.of("id", REQUEST_ID));
        assertFalse(repository.isStillEligible(context, requester, result));

        jdbc.update("UPDATE facilityflow.maintenance_request SET version = 3 WHERE id = :id",
                java.util.Map.of("id", REQUEST_ID));
        jdbc.update("UPDATE facilityflow.user_account SET session_version = 8 WHERE id = :id",
                java.util.Map.of("id", REQUESTER_ID));
        assertFalse(repository.isStillEligible(context, requester, result));
    }

    private void update(String sql, UUID id) {
        jdbc.update(sql, java.util.Map.of("id", id));
    }

    @Test void formCategoryIdResolvesToActiveLabelBeforeProviderContext() {
        var eligibility = repository.probeCreate(snapshot(REQUESTER_ID, 7)).orElseThrow();
        var context = repository.loadContext(eligibility, new RequesterSmartReportInput(
                "Flickering light", null, null, ELECTRICAL_ID.toString(), "Normal"));
        assertEquals("Electrical", context.assistantContext().selectedCategory());
        assertFalse(context.assistantContext().toString().contains(ELECTRICAL_ID.toString()));
    }

    private void insertCategory(UUID id, String name, long version) {
        jdbc.update("INSERT INTO facilityflow.category VALUES (:id, :name, true, :version)",
                new MapSqlParameterSource().addValue("id", id)
                        .addValue("name", name).addValue("version", version));
    }

    private static CurrentAccountSnapshot snapshot(UUID id, long sessionVersion) {
        return new CurrentAccountSnapshot(id, AccountRole.REQUESTER, sessionVersion);
    }

    private static RequesterSmartReportInput input() {
        return new RequesterSmartReportInput(
                "Flickering light", "The ceiling light flickers intermittently.",
                "Room 4", "Electrical", "High");
    }

    private static SmartReportResult result() {
        return new SmartReportResult(
                "Flickering ceiling light",
                "The ceiling light flickers intermittently during normal use.",
                "Room 4", "Electrical", "High", List.of(),
                "The draft preserves the reported symptoms.");
    }
}
