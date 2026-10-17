package sg.edu.nus.facilityflow.manager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import sg.edu.nus.facilityflow.identity.CurrentAccount;

@Repository
public class JdbcManagerRepository implements ManagerRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcManagerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void requireManager(CurrentAccount actor, boolean writing) {
        String lock = writing ? " FOR UPDATE" : " FOR SHARE";
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT id FROM facilityflow.user_account
                     WHERE id = :id AND lower(username) = lower(:username)
                       AND role = 'FACILITIES_MANAGER' AND active
                """ + lock + ") manager_account", Map.of(
                "id", actor.id(), "username", actor.username()), Long.class);
        if (count == null || count != 1L) {
            throw new AccessDeniedException("The requested operation is not available");
        }
    }

    @Override
    public Map<String, Long> statusCounts() {
        return counts("SELECT status AS label, count(*) AS total FROM facilityflow.maintenance_request GROUP BY status");
    }

    @Override
    public Map<String, Long> priorityCounts() {
        return counts("""
                SELECT manager_priority AS label, count(*) AS total
                  FROM facilityflow.maintenance_request
                 WHERE manager_priority IS NOT NULL
                 GROUP BY manager_priority
                """);
    }

    private Map<String, Long> counts(String sql) {
        var result = new HashMap<String, Long>();
        jdbc.query(sql, Map.of(), (row, number) -> {
            result.put(row.getString("label"), row.getLong("total"));
            return null;
        });
        return Map.copyOf(result);
    }

    @Override
    public List<ManagerViews.TechnicianWorkload> technicianWorkloads() {
        return jdbc.query("""
                SELECT account.id, account.username, account.display_name, count(request.id) AS active_requests
                  FROM facilityflow.user_account account
                  LEFT JOIN facilityflow.maintenance_request request
                    ON request.assigned_technician_id = account.id
                   AND request.status IN ('ASSIGNED', 'IN_PROGRESS')
                 WHERE account.role = 'TECHNICIAN' AND account.active
                 GROUP BY account.id, account.username, account.display_name
                 ORDER BY active_requests, lower(account.username), account.id
                """, Map.of(), (row, number) -> new ManagerViews.TechnicianWorkload(
                row.getObject("id", UUID.class), row.getString("username"),
                row.getString("display_name"), row.getLong("active_requests")));
    }

    @Override
    public List<ManagerViews.Option> activeRequesters() {
        return accountOptions("REQUESTER", true);
    }

    @Override
    public List<ManagerViews.Option> technicians(boolean activeOnly) {
        return accountOptions("TECHNICIAN", activeOnly);
    }

    private List<ManagerViews.Option> accountOptions(String role, boolean activeOnly) {
        String active = activeOnly ? " AND active" : "";
        return jdbc.query("""
                SELECT id, display_name || ' (' || username || ')' ||
                       CASE WHEN active THEN '' ELSE ' — inactive' END AS label
                  FROM facilityflow.user_account
                 WHERE role = :role
                """ + active + " ORDER BY active DESC, lower(display_name), lower(username), id",
                Map.of("role", role), (row, number) -> new ManagerViews.Option(
                row.getObject("id", UUID.class), row.getString("label")));
    }

    @Override
    public List<ManagerViews.Option> categories(boolean activeOnly) {
        String active = activeOnly ? " WHERE active" : "";
        return jdbc.query("SELECT id, name AS label FROM facilityflow.category" + active
                        + " ORDER BY active DESC, lower(name), id",
                Map.of(), (row, number) -> new ManagerViews.Option(
                        row.getObject("id", UUID.class), row.getString("label")));
    }

    @Override
    public List<ManagerViews.RequestSummary> requests(ManagerRequestFilters filters) {
        var sql = new StringBuilder("""
                SELECT request.display_id, request.title, request.location,
                       category.name AS category, request.reported_urgency,
                       request.manager_priority, request.status,
                       owner.display_name || ' (' || owner.username || ')' AS requester,
                       CASE WHEN technician.id IS NULL THEN NULL
                            ELSE technician.display_name || ' (' || technician.username || ')' END AS technician,
                       request.created_at, request.updated_at
                  FROM facilityflow.maintenance_request request
                  JOIN facilityflow.category category ON category.id = request.category_id
                  JOIN facilityflow.user_account owner ON owner.id = request.requester_id
                  LEFT JOIN facilityflow.user_account technician ON technician.id = request.assigned_technician_id
                 WHERE true
                """);
        var parameters = new MapSqlParameterSource();
        if (!filters.search().isEmpty()) {
            sql.append(" AND (position(:search in lower(request.display_id)) > 0")
                    .append(" OR position(:search in lower(request.title)) > 0")
                    .append(" OR position(:search in lower(request.location)) > 0")
                    .append(" OR position(:search in lower(owner.username)) > 0")
                    .append(" OR position(:search in lower(owner.display_name)) > 0")
                    .append(" OR position(:search in lower(coalesce(technician.username, ''))) > 0")
                    .append(" OR position(:search in lower(coalesce(technician.display_name, ''))) > 0)");
            parameters.addValue("search", filters.search().toLowerCase(java.util.Locale.ROOT));
        }
        equalsFilter(sql, parameters, "request.status", "status", filters.status());
        uuidFilter(sql, parameters, "request.category_id", "categoryId", filters.categoryId());
        equalsFilter(sql, parameters, "request.manager_priority", "priority", filters.priority());
        uuidFilter(sql, parameters, "request.assigned_technician_id", "technicianId", filters.technicianId());
        instantFilter(sql, parameters, "request.created_at >=", "createdFrom", filters.fromInstant());
        instantFilter(sql, parameters, "request.created_at <", "createdTo", filters.toExclusiveInstant());
        sql.append("""
                 ORDER BY
                   CASE WHEN request.status = 'OPEN' THEN 0
                        WHEN request.status IN ('ASSIGNED', 'IN_PROGRESS') THEN 1 ELSE 2 END,
                   CASE WHEN request.status = 'OPEN' THEN
                     CASE request.reported_urgency WHEN 'EMERGENCY' THEN 0 WHEN 'HIGH' THEN 1
                          WHEN 'NORMAL' THEN 2 ELSE 3 END END,
                   CASE WHEN request.status = 'OPEN' THEN request.created_at END,
                   CASE WHEN request.status IN ('ASSIGNED', 'IN_PROGRESS') THEN
                     CASE request.manager_priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1
                          WHEN 'MEDIUM' THEN 2 ELSE 3 END END,
                   CASE WHEN request.status IN ('ASSIGNED', 'IN_PROGRESS') THEN request.updated_at END,
                   CASE WHEN request.status NOT IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') THEN request.updated_at END DESC,
                   request.display_id
                 LIMIT 200
                """);
        return jdbc.query(sql.toString(), parameters, JdbcManagerRepository::mapRequest);
    }

    @Override
    public List<ManagerViews.AuditRow> audit(ManagerAuditFilters filters) {
        var sql = new StringBuilder("""
                SELECT event.occurred_at, actor.username AS actor, event.action, event.target_type,
                       request.display_id AS request_display_id
                  FROM facilityflow.audit_event event
                  JOIN facilityflow.user_account actor ON actor.id = event.actor_id
                  LEFT JOIN facilityflow.maintenance_request request
                    ON event.target_type = 'REQUEST' AND request.id = event.target_id
                 WHERE true
                """);
        var parameters = new MapSqlParameterSource();
        if (!filters.request().isEmpty()) {
            sql.append(" AND request.display_id IS NOT NULL")
                    .append(" AND position(:request in lower(request.display_id)) > 0");
            parameters.addValue("request", filters.request().toLowerCase(java.util.Locale.ROOT));
        }
        if (!filters.actor().isEmpty()) {
            sql.append(" AND (position(:actor in lower(actor.username)) > 0")
                    .append(" OR position(:actor in lower(actor.display_name)) > 0)");
            parameters.addValue("actor", filters.actor().toLowerCase(java.util.Locale.ROOT));
        }
        if (!filters.action().isEmpty()) {
            sql.append(" AND position(:action in lower(event.action)) > 0");
            parameters.addValue("action", filters.action().toLowerCase(java.util.Locale.ROOT));
        }
        instantFilter(sql, parameters, "event.occurred_at >=", "occurredFrom", filters.fromInstant());
        instantFilter(sql, parameters, "event.occurred_at <", "occurredTo", filters.toExclusiveInstant());
        sql.append(" ORDER BY event.occurred_at DESC, event.id DESC LIMIT 200");
        return jdbc.query(sql.toString(), parameters, (row, number) -> new ManagerViews.AuditRow(
                instant(row, "occurred_at"), row.getString("actor"), row.getString("action"),
                row.getString("target_type"), row.getString("request_display_id")));
    }

    @Override
    public boolean hasAuditEvents() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM facilityflow.audit_event)", Map.of(), Boolean.class));
    }

    @Override
    public boolean activeRequester(UUID requesterId) {
        return lockedActiveAccount(requesterId, "REQUESTER");
    }

    @Override
    public boolean activeCategory(UUID categoryId) {
        return Boolean.TRUE.equals(jdbc.query("""
                SELECT active FROM facilityflow.category WHERE id = :id FOR SHARE
                """, Map.of("id", categoryId), result -> result.next() && result.getBoolean("active")));
    }

    private boolean lockedActiveAccount(UUID id, String role) {
        return Boolean.TRUE.equals(jdbc.query("""
                SELECT active FROM facilityflow.user_account
                 WHERE id = :id AND role = :role FOR SHARE
                """, Map.of("id", id, "role", role), result -> result.next() && result.getBoolean("active")));
    }

    @Override
    public Created insert(UUID actorId, UUID requesterId, UUID categoryId, ManagerRequestDraft draft) {
        return jdbc.queryForObject("""
                INSERT INTO facilityflow.maintenance_request
                    (requester_id, recorded_by, title, description, location, category_id, reported_urgency)
                VALUES (:requester, :actor, :title, :description, :location, :category, :urgency)
                RETURNING id, display_id
                """, new MapSqlParameterSource()
                .addValue("requester", requesterId)
                .addValue("actor", actorId)
                .addValue("title", draft.title())
                .addValue("description", draft.description())
                .addValue("location", draft.location())
                .addValue("category", categoryId)
                .addValue("urgency", draft.reportedUrgency()),
                (row, number) -> new Created(row.getObject("id", UUID.class), row.getString("display_id")));
    }

    @Override
    public void auditCreation(UUID actorId, UUID requestId, UUID requesterId) {
        jdbc.update("""
                INSERT INTO facilityflow.audit_event
                    (actor_id, action, target_type, target_id, details)
                VALUES (:actor, 'REQUEST_RECORDED_ON_BEHALF', 'REQUEST', :request,
                        jsonb_build_object('requesterId', :requester))
                """, Map.of("actor", actorId, "request", requestId, "requester", requesterId));
    }

    private static void equalsFilter(StringBuilder sql, MapSqlParameterSource parameters,
            String column, String name, String value) {
        if (!value.isEmpty()) {
            sql.append(" AND ").append(column).append(" = :").append(name);
            parameters.addValue(name, value);
        }
    }

    private static void uuidFilter(StringBuilder sql, MapSqlParameterSource parameters,
            String column, String name, String value) {
        if (!value.isEmpty()) {
            sql.append(" AND ").append(column).append(" = :").append(name);
            parameters.addValue(name, UUID.fromString(value));
        }
    }

    private static void instantFilter(StringBuilder sql, MapSqlParameterSource parameters,
            String expression, String name, Instant value) {
        if (value != null) {
            sql.append(" AND ").append(expression).append(" :").append(name);
            parameters.addValue(name, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    private static ManagerViews.RequestSummary mapRequest(ResultSet row, int number) throws SQLException {
        return new ManagerViews.RequestSummary(
                row.getString("display_id"), row.getString("title"), row.getString("location"),
                row.getString("category"), row.getString("reported_urgency"),
                row.getString("manager_priority"), row.getString("status"),
                row.getString("requester"), row.getString("technician"),
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
