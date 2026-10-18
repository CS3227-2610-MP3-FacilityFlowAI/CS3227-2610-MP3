package sg.edu.nus.facilityflow.technician;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.*;

/** Parameterized, consistent assigned reads for REQ-TECH-001–003/009 and REQ-SH-002. */
@Repository
public class JdbcTechnicianQueryRepository implements TechnicianQueryRepository {
    private static final String REQUEST_COLUMNS = """
            SELECT r.display_id, r.title, r.location, r.category_id, c.name AS category,
                   r.status, r.manager_priority, r.reported_urgency, r.assigned_at,
                   r.description, r.created_at, r.updated_at, r.version,
                   r.resolution_summary, r.completed_at
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.category c ON c.id = r.category_id
             WHERE r.assigned_technician_id = :actorId
            """;

    private static final String QUEUE_ORDER = """
             ORDER BY CASE r.manager_priority
                          WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1
                          WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END,
                      CASE r.reported_urgency
                          WHEN 'EMERGENCY' THEN 0 WHEN 'HIGH' THEN 1
                          WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END,
                      r.assigned_at ASC NULLS LAST, r.display_id ASC
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DataSource transactionDataSource;

    public JdbcTechnicianQueryRepository(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        // The manager unwraps a TransactionAwareDataSourceProxy before binding its resource.
        this.transactionDataSource = Objects.requireNonNull(manager.getDataSource(), "transactionDataSource");
        this.transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T readSnapshot(Function<ReadSession, T> reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            return transaction.execute(status -> {
                Session session = new Session();
                try {
                    return reader.apply(session);
                } finally {
                    session.close();
                }
            });
        } catch (DataAccessException | TransactionException failure) {
            // Do not attach a cause containing SQL, connection details or stored free text.
            throw new TechnicianQueryException(TechnicianQueryException.Code.UNAVAILABLE);
        }
    }

    private final class Session implements ReadSession {
        private final Thread owner = Thread.currentThread();
        private final Object transactionResource =
                TransactionSynchronizationManager.getResource(transactionDataSource);
        private volatile boolean closed;
        private UUID boundActor;
        private UUID authorizedActor;

        private void requireOpen() {
            if (closed || owner != Thread.currentThread() || transactionResource == null
                    || TransactionSynchronizationManager.getResource(transactionDataSource) != transactionResource) {
                throw new IllegalStateException("Read session is outside its snapshot");
            }
        }

        private void requireActor(UUID actorId) {
            requireOpen();
            if (authorizedActor == null || !authorizedActor.equals(actorId)) {
                throw denied();
            }
        }

        private void close() {
            closed = true;
            authorizedActor = null;
        }

        @Override
        public void requireCurrentTechnician(CurrentAccountSnapshot actor) {
            requireOpen();
            authorizedActor = null;
            if (actor == null || actor.role() != AccountRole.TECHNICIAN || actor.sessionVersion() < 0
                    || (boundActor != null && !boundActor.equals(actor.id()))) {
                throw denied();
            }
            Long matches = jdbc.queryForObject("""
                    SELECT count(*) FROM facilityflow.user_account
                     WHERE id = :actorId AND active = true AND role = 'TECHNICIAN'
                       AND session_version = :sessionVersion
                    """, Map.of("actorId", actor.id(), "sessionVersion", actor.sessionVersion()), Long.class);
            if (matches == null || matches != 1L) {
                throw denied();
            }
            // The first successful identity remains bound even after a caught failed retry.
            if (boundActor == null) {
                boundActor = actor.id();
            }
            authorizedActor = actor.id();
        }

        @Override
        public DashboardCounts counts(UUID actorId) {
            requireActor(actorId);
            return jdbc.queryForObject("""
                    SELECT count(*) FILTER (WHERE status = 'ASSIGNED') AS assigned,
                           count(*) FILTER (WHERE status = 'IN_PROGRESS') AS in_progress,
                           count(*) FILTER (WHERE status = 'COMPLETED') AS completed
                      FROM facilityflow.maintenance_request WHERE assigned_technician_id = :actorId
                    """, Map.of("actorId", actorId), (rs, row) -> new DashboardCounts(
                            rs.getLong("assigned"), rs.getLong("in_progress"), rs.getLong("completed")));
        }

        @Override
        public long assignedTotal(UUID actorId) {
            requireActor(actorId);
            Long total = jdbc.queryForObject("""
                    SELECT count(*) FROM facilityflow.maintenance_request
                     WHERE assigned_technician_id = :actorId
                    """, Map.of("actorId", actorId), Long.class);
            return Objects.requireNonNull(total, "count result");
        }

        @Override
        public List<CategoryOption> categories(UUID actorId) {
            requireActor(actorId);
            return List.copyOf(jdbc.query("""
                    SELECT DISTINCT c.id, c.name
                      FROM facilityflow.category c
                      JOIN facilityflow.maintenance_request r ON r.category_id = c.id
                     WHERE r.assigned_technician_id = :actorId
                     ORDER BY c.name, c.id
                    """, Map.of("actorId", actorId), (rs, row) ->
                            new CategoryOption(rs.getObject("id", UUID.class), rs.getString("name"))));
        }

        @Override
        public List<QueueRow> queue(UUID actorId, QueueFilter filter) {
            requireActor(actorId);
            if (filter == null) {
                throw new TechnicianQueryException(TechnicianQueryException.Code.INVALID_FILTER);
            }
            StringBuilder sql = new StringBuilder(REQUEST_COLUMNS);
            MapSqlParameterSource parameters = new MapSqlParameterSource("actorId", actorId);
            if (!filter.q().isEmpty()) {
                sql.append("""
                         AND (r.display_id ILIKE :search ESCAPE '!'
                           OR r.title ILIKE :search ESCAPE '!'
                           OR r.location ILIKE :search ESCAPE '!')
                        """);
                parameters.addValue("search", "%" + filter.q().replace("!", "!!")
                        .replace("%", "!%").replace("_", "!_") + "%");
            }
            if (filter.status() != null) {
                sql.append(" AND r.status = :status\n");
                parameters.addValue("status", filter.status().name());
            }
            if (filter.categoryId() != null) {
                sql.append(" AND r.category_id = :categoryId\n");
                parameters.addValue("categoryId", filter.categoryId());
            }
            if (filter.priority() != null) {
                sql.append(" AND r.manager_priority = :priority\n");
                parameters.addValue("priority", filter.priority().name());
            }
            sql.append(QUEUE_ORDER);
            return List.copyOf(jdbc.query(sql.toString(), parameters, (rs, row) -> mapQueueRow(rs)));
        }

        @Override
        public Optional<RequestDetail> detail(UUID actorId, String displayId) {
            requireActor(actorId);
            if (displayId == null) {
                return Optional.empty();
            }
            Map<String, Object> parameters = Map.of("actorId", actorId, "displayId", displayId);
            List<RequestDetail> details = jdbc.query(REQUEST_COLUMNS + " AND r.display_id = :displayId",
                    parameters, (rs, row) -> new RequestDetail(mapQueueRow(rs), rs.getString("description"),
                            instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"),
                            rs.getString("resolution_summary"), instant(rs, "completed_at"), List.of(), List.of()));
            if (details.isEmpty()) {
                return Optional.empty();
            }
            // Each child query repeats assignment scope; internal Request IDs never leave persistence.
            List<RequesterUpdateView> updates = jdbc.query("""
                    SELECT u.note, a.display_name, u.created_at
                      FROM facilityflow.requester_update u
                      JOIN facilityflow.maintenance_request r ON r.id = u.request_id
                      JOIN facilityflow.user_account a ON a.id = u.author_id
                     WHERE r.display_id = :displayId AND r.assigned_technician_id = :actorId
                     ORDER BY u.created_at ASC, u.id ASC
                    """, parameters, (rs, row) -> new RequesterUpdateView(
                            rs.getString("note"), rs.getString("display_name"), instant(rs, "created_at")));
            List<WorkLogView> logs = jdbc.query("""
                    SELECT w.note, w.minutes, a.display_name, w.created_at
                      FROM facilityflow.work_log w
                      JOIN facilityflow.maintenance_request r ON r.id = w.request_id
                      JOIN facilityflow.user_account a ON a.id = w.author_id
                     WHERE r.display_id = :displayId AND r.assigned_technician_id = :actorId
                     ORDER BY w.created_at ASC, w.id ASC
                    """, parameters, (rs, row) -> new WorkLogView(rs.getString("note"),
                            rs.getInt("minutes"), rs.getString("display_name"), instant(rs, "created_at")));
            RequestDetail detail = details.getFirst();
            return Optional.of(new RequestDetail(detail.request(), detail.description(), detail.createdAt(),
                    detail.updatedAt(), detail.version(), detail.resolutionSummary(), detail.completedAt(),
                    updates, logs));
        }
    }

    private static QueueRow mapQueueRow(ResultSet rs) throws SQLException {
        String priority = rs.getString("manager_priority");
        return new QueueRow(rs.getString("display_id"), rs.getString("title"), rs.getString("location"),
                rs.getObject("category_id", UUID.class), rs.getString("category"),
                RequestStatus.valueOf(rs.getString("status")),
                priority == null ? null : ManagerPriority.valueOf(priority),
                ReportedUrgency.valueOf(rs.getString("reported_urgency")), instant(rs, "assigned_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime timestamp = rs.getObject(column, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("The requested operation is not available");
    }
}
