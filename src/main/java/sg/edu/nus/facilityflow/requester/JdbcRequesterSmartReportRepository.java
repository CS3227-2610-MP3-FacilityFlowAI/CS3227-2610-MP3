package sg.edu.nus.facilityflow.requester;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** JDBC eligibility, allowlisted catalogue context and freshness checks for Smart Report. */
@Repository
public class JdbcRequesterSmartReportRepository implements RequesterSmartReportRepository {
    private static final String ACTIVE_REQUESTER = """
            SELECT count(*)
              FROM facilityflow.user_account a
             WHERE a.id = :requesterId
               AND a.active = true
               AND a.role = 'REQUESTER'
               AND (:sessionVersion < 0 OR a.session_version = :sessionVersion)
            """;
    private static final String ELIGIBLE_EDIT = """
            SELECT r.id, r.display_id, r.version
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.user_account a ON a.id = r.requester_id
             WHERE r.display_id = :displayId
               AND r.requester_id = :requesterId
               AND r.status = 'OPEN'
               AND a.active = true
               AND a.role = 'REQUESTER'
               AND (:sessionVersion < 0 OR a.session_version = :sessionVersion)
            """;
    private static final String ACTIVE_CATEGORIES = """
            SELECT id, name, version
              FROM facilityflow.category
             WHERE active = true
             ORDER BY lower(name), id
            """;
    private static final String FRESH_EDIT = """
            SELECT count(*)
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.user_account a ON a.id = r.requester_id
             WHERE r.id = :requestId
               AND r.display_id = :displayId
               AND r.requester_id = :requesterId
               AND r.version = :requestVersion
               AND r.status = 'OPEN'
               AND a.active = true
               AND a.role = 'REQUESTER'
               AND (:sessionVersion < 0 OR a.session_version = :sessionVersion)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRequesterSmartReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Eligibility> probeCreate(CurrentAccountSnapshot requester) {
        return accountActive(requester)
                ? Optional.of(new Eligibility(requester.id(), null, null, null))
                : Optional.empty();
    }

    @Override
    public Optional<Eligibility> probeEdit(String displayId, CurrentAccountSnapshot requester) {
        List<Eligibility> matches = jdbc.query(
                ELIGIBLE_EDIT,
                Map.of(
                        "displayId", displayId,
                        "requesterId", requester.id(),
                        "sessionVersion", requester.sessionVersion()),
                (resultSet, rowNumber) -> new Eligibility(
                        requester.id(),
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("display_id"),
                        resultSet.getLong("version")));
        return matches.stream().findFirst();
    }

    @Override
    public ContextSnapshot loadContext(Eligibility eligibility, RequesterSmartReportInput input) {
        List<CategorySnapshot> categories = activeCategories();
        List<String> labels = categories.stream().map(CategorySnapshot::name).toList();
        SmartReportContext context = input.toContext(labels);
        if (context.selectedCategory() != null && !labels.contains(context.selectedCategory())) {
            throw new IllegalArgumentException("selected category is inactive");
        }
        return new ContextSnapshot(eligibility, context, categories);
    }

    @Override
    public boolean isStillEligible(
            ContextSnapshot context, CurrentAccountSnapshot requester, SmartReportResult result) {
        if (!context.eligibility().requesterId().equals(requester.id())) {
            return false;
        }
        boolean accessFresh = context.eligibility().persistedEdit()
                ? editFresh(context.eligibility(), requester)
                : accountActive(requester);
        if (!accessFresh) {
            return false;
        }
        List<CategorySnapshot> currentCategories = activeCategories();
        if (!currentCategories.equals(context.activeCategories())) {
            return false;
        }
        return result.suggestedCategory() == null
                || currentCategories.stream().anyMatch(category -> category.name().equals(result.suggestedCategory()));
    }

    private boolean accountActive(CurrentAccountSnapshot requester) {
        Number matches = jdbc.queryForObject(
                ACTIVE_REQUESTER,
                Map.of("requesterId", requester.id(), "sessionVersion", requester.sessionVersion()),
                Number.class);
        return matches != null && matches.longValue() == 1L;
    }

    private boolean editFresh(Eligibility eligibility, CurrentAccountSnapshot requester) {
        Number matches = jdbc.queryForObject(
                FRESH_EDIT,
                Map.of(
                        "requestId", eligibility.requestId(),
                        "displayId", eligibility.displayId(),
                        "requesterId", requester.id(),
                        "requestVersion", eligibility.requestVersion(),
                        "sessionVersion", requester.sessionVersion()),
                Number.class);
        return matches != null && matches.longValue() == 1L;
    }

    private List<CategorySnapshot> activeCategories() {
        return jdbc.query(ACTIVE_CATEGORIES, Map.of(), JdbcRequesterSmartReportRepository::mapCategory);
    }

    private static CategorySnapshot mapCategory(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CategorySnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getLong("version"));
    }
}
