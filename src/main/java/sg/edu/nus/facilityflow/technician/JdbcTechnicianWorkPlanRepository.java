package sg.edu.nus.facilityflow.technician;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.ai.technician.RequesterUpdate;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Loads only the bounded, assigned Request context needed by the Work Plan seam. */
@Repository
public class JdbcTechnicianWorkPlanRepository implements TechnicianWorkPlanRepository {

    private static final String CURRENT_TECHNICIAN = """
            SELECT count(*)
              FROM facilityflow.user_account
             WHERE id = :technicianId
               AND active = true
               AND role = 'TECHNICIAN'
               AND session_version = :sessionVersion
            """;

    private static final String ELIGIBLE_REQUEST = """
            SELECT r.id, r.display_id, r.assigned_technician_id, r.title, r.description,
                   r.location, c.name AS category, r.reported_urgency, r.manager_priority,
                   r.status, r.version
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.category c ON c.id = r.category_id
             WHERE r.display_id = :displayId
               AND c.active = true
               AND r.assigned_technician_id = :technicianId
               AND r.status IN ('ASSIGNED', 'IN_PROGRESS')
            """;

    private static final String UPDATES = """
            SELECT id, created_at, note
              FROM facilityflow.requester_update
             WHERE request_id = :requestId
             ORDER BY created_at DESC, id DESC
             LIMIT 10
            """;

    private static final String STILL_ELIGIBLE = """
            SELECT count(*)
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.user_account a ON a.id = r.assigned_technician_id
              JOIN facilityflow.category c ON c.id = r.category_id
             WHERE r.id = :requestId
               AND r.display_id = :displayId
               AND r.assigned_technician_id = :technicianId
               AND r.version = :requestVersion
               AND r.status IN ('ASSIGNED', 'IN_PROGRESS')
               AND r.status = :requestStatus
               AND c.active = true
               AND c.name = :category
               AND a.active = true
               AND a.role = 'TECHNICIAN'
               AND a.id = :authenticatedAccountId
               AND a.session_version = :sessionVersion
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate snapshot;

    public JdbcTechnicianWorkPlanRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(
                Objects.requireNonNull(jdbc.getJdbcTemplate().getDataSource(), "dataSource"));
        this.snapshot = new TransactionTemplate(manager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<WorkPlanContext> loadEligible(String displayId, CurrentAccountSnapshot account) {
        if (!validTechnician(account)) {
            return Optional.empty();
        }
        // Authorization, Request and Updates share one short snapshot; it ends before provider use.
        return snapshot.execute(status -> {
            Number matches = jdbc.queryForObject(CURRENT_TECHNICIAN,
                    Map.of("technicianId", account.id(), "sessionVersion", account.sessionVersion()),
                    Number.class);
            if (matches == null || matches.longValue() != 1L) {
                return Optional.empty();
            }
            return loadContext(displayId, account.id());
        });
    }

    private Optional<WorkPlanContext> loadContext(String displayId, UUID technicianId) {
        List<WorkPlanContext> requests = jdbc.query(
                ELIGIBLE_REQUEST,
                Map.of("displayId", displayId, "technicianId", technicianId),
                (resultSet, rowNumber) -> mapContext(resultSet));
        if (requests.isEmpty()) {
            return Optional.empty();
        }
        WorkPlanContext context = requests.get(0);
        List<RequesterUpdate> updates = jdbc.query(
                UPDATES,
                Map.of("requestId", context.requestId()),
                JdbcTechnicianWorkPlanRepository::mapUpdate);
        return Optional.of(new WorkPlanContext(
                context.requestId(), context.displayId(), context.assignedTechnicianId(),
                context.title(), context.description(), context.location(), context.category(),
                context.reportedUrgency(), context.managerPriority(), context.status(),
                context.requestVersion(), updates));
    }

    @Override
    public boolean isStillEligible(WorkPlanContext context, CurrentAccountSnapshot accountSnapshot) {
        if (!validTechnician(accountSnapshot) || context == null
                || !context.assignedTechnicianId().equals(accountSnapshot.id())) {
            return false;
        }
        Number matches = jdbc.queryForObject(
                STILL_ELIGIBLE,
                Map.of(
                        "requestId", context.requestId(),
                        "displayId", context.displayId(),
                        "technicianId", context.assignedTechnicianId(),
                        "requestVersion", context.requestVersion(),
                        "requestStatus", context.status(),
                        "category", context.category(),
                        "authenticatedAccountId", accountSnapshot.id(),
                        "sessionVersion", accountSnapshot.sessionVersion()),
                Number.class);
        return matches != null && matches.longValue() == 1L;
    }

    private static boolean validTechnician(CurrentAccountSnapshot account) {
        return account != null && account.role() == AccountRole.TECHNICIAN
                && account.sessionVersion() >= 0;
    }

    private static WorkPlanContext mapContext(ResultSet resultSet) throws SQLException {
        return new WorkPlanContext(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("display_id"),
                resultSet.getObject("assigned_technician_id", UUID.class),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("location"),
                resultSet.getString("category"),
                resultSet.getString("reported_urgency"),
                resultSet.getString("manager_priority"),
                resultSet.getString("status"),
                resultSet.getLong("version"),
                List.of());
    }

    private static RequesterUpdate mapUpdate(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
        return new RequesterUpdate(
                resultSet.getObject("id", UUID.class), createdAt.toInstant(), resultSet.getString("note"));
    }
}
