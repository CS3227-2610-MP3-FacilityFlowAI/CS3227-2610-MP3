package sg.edu.nus.facilityflow.requester;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

@Repository
public class JdbcRequesterRequestRepository implements RequesterRequestRepository {
    private final JdbcTemplate jdbc;

    public JdbcRequesterRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void requireRequester(CurrentAccount account, boolean writing) {
        if (account.role() != AccountRole.REQUESTER || jdbc.queryForList("""
                SELECT id FROM facilityflow.user_account
                 WHERE id = ? AND active = true AND role = 'REQUESTER' AND session_version = ?
                """ + (writing ? " FOR UPDATE" : " FOR SHARE"), UUID.class,
                account.id(), account.sessionVersion()).isEmpty()) {
            throw new AccessDeniedException("The requested operation is not available.");
        }
    }

    @Override
    public List<RequesterRequests.Category> categories() {
        return jdbc.query("SELECT id, name FROM facilityflow.category WHERE active ORDER BY lower(name), id",
                (row, index) -> new RequesterRequests.Category(row.getObject("id", UUID.class), row.getString("name")));
    }

    @Override
    public List<RequesterRequests.Summary> list(UUID owner) {
        return jdbc.query("""
                SELECT r.display_id, r.title, r.location, c.name AS category,
                       r.reported_urgency, r.status, r.manager_priority, r.created_at
                  FROM facilityflow.maintenance_request r JOIN facilityflow.category c ON c.id = r.category_id
                 WHERE r.requester_id = ? ORDER BY r.created_at DESC, r.display_id ASC
                """, (row, index) -> new RequesterRequests.Summary(row.getString("display_id"), row.getString("title"),
                row.getString("location"), row.getString("category"), row.getString("reported_urgency"),
                row.getString("status"), row.getString("manager_priority"), row.getObject("created_at", OffsetDateTime.class).toInstant()), owner);
    }

    @Override
    public Optional<RequesterRequests.Detail> detail(UUID owner, String displayId) {
        var rows = jdbc.query("""
                SELECT r.display_id, r.title, r.description, r.location, c.name AS category,
                       r.reported_urgency, r.status, r.manager_priority, r.created_at, r.updated_at, r.category_id, r.version
                  FROM facilityflow.maintenance_request r JOIN facilityflow.category c ON c.id = r.category_id
                 WHERE r.requester_id = ? AND r.display_id = ?
                """, (row, index) -> new RequesterRequests.Detail(row.getString("display_id"), row.getString("title"),
                row.getString("description"), row.getString("location"), row.getString("category"),
                row.getString("reported_urgency"), row.getString("status"), row.getString("manager_priority"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getObject("updated_at", OffsetDateTime.class).toInstant(),
                row.getObject("category_id", UUID.class).toString(), row.getLong("version"), List.of()), owner, displayId);
        if (rows.isEmpty()) return Optional.empty();
        var request = rows.getFirst();
        return Optional.of(new RequesterRequests.Detail(request.displayId(), request.title(), request.description(),
                request.location(), request.category(), request.reportedUrgency(), request.status(), request.managerPriority(),
                request.createdAt(), request.updatedAt(), request.categoryId(), request.version(), history(owner, displayId)));
    }

    private List<RequesterRequests.History> history(UUID owner, String displayId) {
        // REQ-RQ-009: project only approved event labels/reasons, never raw audit details or Work Logs.
        return jdbc.query("""
                SELECT a.id, a.occurred_at,
                       CASE a.action WHEN 'REQUEST_CREATED' THEN 'Request submitted'
                           WHEN 'REQUEST_EDITED' THEN 'Report edited' WHEN 'REQUEST_ASSIGNED' THEN 'Assigned'
                           WHEN 'REQUEST_REASSIGNED' THEN 'Reassigned' WHEN 'REQUEST_STARTED' THEN 'Work started'
                           WHEN 'REQUEST_COMPLETED' THEN 'Work completed' WHEN 'REQUEST_CLOSED' THEN 'Closed'
                           WHEN 'REQUEST_CANCELLED' THEN 'Cancelled' WHEN 'REQUEST_RETURNED' THEN 'Returned for work'
                           WHEN 'REQUEST_REOPENED' THEN 'Reopened' END AS event,
                       CASE WHEN a.action IN ('REQUEST_CANCELLED', 'REQUEST_REASSIGNED', 'REQUEST_RETURNED', 'REQUEST_REOPENED')
                            THEN coalesce(a.details->>'reason', '') ELSE '' END AS note, '' AS author
                  FROM facilityflow.audit_event a JOIN facilityflow.maintenance_request r ON r.id = a.target_id
                 WHERE r.requester_id = ? AND r.display_id = ? AND a.target_type = 'REQUEST'
                   AND a.action IN ('REQUEST_CREATED', 'REQUEST_EDITED', 'REQUEST_ASSIGNED', 'REQUEST_REASSIGNED',
                       'REQUEST_STARTED', 'REQUEST_COMPLETED', 'REQUEST_CLOSED', 'REQUEST_CANCELLED', 'REQUEST_RETURNED', 'REQUEST_REOPENED')
                UNION ALL
                SELECT u.id, u.created_at, 'Requester update' AS event, u.note, author.display_name AS author
                  FROM facilityflow.requester_update u JOIN facilityflow.maintenance_request r ON r.id = u.request_id
                  JOIN facilityflow.user_account author ON author.id = u.author_id
                 WHERE r.requester_id = ? AND r.display_id = ?
                 ORDER BY occurred_at, id
                """, (row, index) -> new RequesterRequests.History(row.getString("event"), row.getString("note"),
                row.getString("author"), row.getObject("occurred_at", OffsetDateTime.class).toInstant()), owner, displayId, owner, displayId);
    }

    @Override
    public boolean submitted(UUID owner, UUID command) {
        return !jdbc.queryForList("SELECT command_id FROM facilityflow.request_submission WHERE requester_id = ? AND command_id = ?",
                UUID.class, owner, command).isEmpty();
    }

    @Override
    public boolean activeCategory(UUID category) {
        return !jdbc.queryForList("SELECT id FROM facilityflow.category WHERE id = ? AND active FOR SHARE", UUID.class, category).isEmpty();
    }

    @Override
    public Created insert(UUID owner, RequestDraft draft) {
        return jdbc.queryForObject("""
                INSERT INTO facilityflow.maintenance_request
                    (requester_id, recorded_by, title, description, location, category_id, reported_urgency)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id, display_id
                """, (row, index) -> new Created(row.getObject("id", UUID.class), row.getString("display_id")),
                owner, owner, draft.title(), draft.description(), draft.location(), UUID.fromString(draft.categoryId()), draft.reportedUrgency());
    }

    @Override
    public void auditCreation(UUID owner, UUID request) {
        jdbc.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id) VALUES (?, 'REQUEST_CREATED', 'REQUEST', ?)",
                owner, request);
    }

    @Override
    public void recordSubmission(UUID owner, UUID command, UUID request) {
        jdbc.update("INSERT INTO facilityflow.request_submission (requester_id, command_id, request_id) VALUES (?, ?, ?)", owner, command, request);
    }

    @Override
    public Optional<Locked> lockOwned(UUID owner, String displayId) {
        return jdbc.query("SELECT id, status, version FROM facilityflow.maintenance_request WHERE requester_id = ? AND display_id = ? FOR UPDATE",
                (row, index) -> new Locked(row.getObject("id", UUID.class), row.getString("status"), row.getLong("version")),
                owner, displayId).stream().findFirst();
    }

    @Override
    public void edit(Locked request, RequestDraft draft) {
        requireChanged(jdbc.update("""
                UPDATE facilityflow.maintenance_request SET title = ?, description = ?, location = ?, category_id = ?,
                    reported_urgency = ?, version = version + 1, updated_at = clock_timestamp()
                 WHERE id = ? AND version = ? AND status = 'OPEN'
                """, draft.title(), draft.description(), draft.location(), UUID.fromString(draft.categoryId()),
                draft.reportedUrgency(), request.id(), request.version()));
    }

    @Override
    public void cancel(Locked request) {
        requireChanged(jdbc.update("""
                UPDATE facilityflow.maintenance_request SET status = 'CANCELLED', version = version + 1, updated_at = clock_timestamp()
                 WHERE id = ? AND version = ? AND status = 'OPEN'
                """, request.id(), request.version()));
    }

    @Override
    public void addUpdate(UUID owner, Locked request, String note) {
        requireChanged(jdbc.update("""
                UPDATE facilityflow.maintenance_request SET version = version + 1, updated_at = clock_timestamp()
                 WHERE id = ? AND version = ? AND status NOT IN ('CLOSED', 'CANCELLED')
                """, request.id(), request.version()));
        jdbc.update("INSERT INTO facilityflow.requester_update (request_id, author_id, note) VALUES (?, ?, ?)", request.id(), owner, note);
    }

    @Override
    public void auditMutation(UUID owner, UUID request, String action, String reason) {
        jdbc.update("""
                INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details)
                VALUES (?, ?, 'REQUEST', ?, jsonb_strip_nulls(jsonb_build_object('reason', ?::text)))
                """, owner, action, request, reason);
    }

    private static void requireChanged(int count) {
        if (count != 1) throw new StaleRequestException();
    }
}
