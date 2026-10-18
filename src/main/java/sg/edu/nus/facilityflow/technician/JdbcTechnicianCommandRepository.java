package sg.edu.nus.facilityflow.technician;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Locked, atomic writes and append-only audit for REQ-TECH-004–006/010 and REQ-SH-002/006/014. */
@Repository
public class JdbcTechnicianCommandRepository implements TechnicianCommandRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcTechnicianCommandRepository(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void startWork(CurrentAccountSnapshot actor, String displayId, long expectedVersion) {
        requireSnapshot(actor);
        TechnicianCommandService.validateRequest(displayId, expectedVersion);
        execute(() -> {
            requireCurrentTechnician(actor);
            LockedRequest request = lockAssignedRequest(actor, displayId);
            requireStateAndVersion(request, "ASSIGNED", expectedVersion);
            MapSqlParameterSource parameters = commandParameters(actor, request, expectedVersion);
            int updated = jdbc.update("""
                    UPDATE facilityflow.maintenance_request
                       SET status = 'IN_PROGRESS', version = version + 1, updated_at = clock_timestamp()
                     WHERE id = :requestId AND assigned_technician_id = :actorId
                       AND status = 'ASSIGNED' AND version = :expectedVersion
                    """, parameters);
            requireUpdated(updated);
            appendAudit(parameters, "TECHNICIAN_WORK_STARTED", """
                    jsonb_build_object('fromStatus', 'ASSIGNED', 'toStatus', 'IN_PROGRESS',
                                       'version', CAST(:expectedVersion AS bigint) + 1)
                    """);
        });
    }

    @Override
    public void appendWorkLog(CurrentAccountSnapshot actor, String displayId, long expectedVersion,
            String note, int minutes) {
        requireSnapshot(actor);
        TechnicianCommandService.validateRequest(displayId, expectedVersion);
        String normalizedNote = TechnicianCommandService.normalizeNote(note, minutes);
        execute(() -> {
            requireCurrentTechnician(actor);
            LockedRequest request = lockAssignedRequest(actor, displayId);
            requireStateAndVersion(request, "IN_PROGRESS", expectedVersion);
            MapSqlParameterSource parameters = commandParameters(actor, request, expectedVersion)
                    .addValue("note", normalizedNote).addValue("minutes", minutes);
            int updated = jdbc.update("""
                    UPDATE facilityflow.maintenance_request
                       SET version = version + 1, updated_at = clock_timestamp()
                     WHERE id = :requestId AND assigned_technician_id = :actorId
                       AND status = 'IN_PROGRESS' AND version = :expectedVersion
                    """, parameters);
            requireUpdated(updated);
            requireInserted(jdbc.update("""
                    INSERT INTO facilityflow.work_log (request_id, author_id, note, minutes)
                    VALUES (:requestId, :actorId, :note, :minutes)
                    """, parameters));
            appendAudit(parameters, "TECHNICIAN_WORK_LOGGED", """
                    jsonb_build_object('minutes', CAST(:minutes AS integer),
                                       'version', CAST(:expectedVersion AS bigint) + 1)
                    """);
        });
    }

    private void execute(Runnable command) {
        try {
            transaction.executeWithoutResult(status -> command.run());
        } catch (DataAccessException | TransactionException failure) {
            // Discard causes, whose messages can include SQL, credentials or free text.
            throw new TechnicianCommandException(TechnicianCommandException.Code.UNAVAILABLE);
        }
    }

    private static void requireSnapshot(CurrentAccountSnapshot actor) {
        if (actor == null || actor.role() != AccountRole.TECHNICIAN || actor.sessionVersion() < 0) {
            throw denied();
        }
    }

    private void requireCurrentTechnician(CurrentAccountSnapshot actor) {
        // FOR SHARE also conflicts with non-key active/role/session UPDATEs (KEY SHARE would not).
        List<Boolean> matches = jdbc.query("""
                SELECT active AND role = 'TECHNICIAN' AND session_version = :sessionVersion AS permitted
                  FROM facilityflow.user_account WHERE id = :actorId FOR SHARE
                """, Map.of("actorId", actor.id(), "sessionVersion", actor.sessionVersion()),
                (rs, row) -> rs.getBoolean("permitted"));
        if (matches.size() != 1 || !matches.getFirst()) {
            throw denied();
        }
    }

    private LockedRequest lockAssignedRequest(CurrentAccountSnapshot actor, String displayId) {
        // Scope is part of the locked query: reassignment while waiting removes the row at READ_COMMITTED.
        List<LockedRequest> requests = jdbc.query("""
                SELECT id, status, version FROM facilityflow.maintenance_request
                 WHERE display_id = :displayId AND assigned_technician_id = :actorId FOR UPDATE
                """, Map.of("displayId", displayId, "actorId", actor.id()), (rs, row) ->
                new LockedRequest(rs.getObject("id", UUID.class), rs.getString("status"), rs.getLong("version")));
        if (requests.size() != 1) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.NOT_AVAILABLE);
        }
        return requests.getFirst();
    }

    private static void requireStateAndVersion(LockedRequest request, String status, long expectedVersion) {
        if (!request.status().equals(status) || request.version() != expectedVersion) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.CONFLICT);
        }
    }

    private static MapSqlParameterSource commandParameters(CurrentAccountSnapshot actor,
            LockedRequest request, long expectedVersion) {
        return new MapSqlParameterSource("actorId", actor.id()).addValue("requestId", request.id())
                .addValue("expectedVersion", expectedVersion);
    }

    private static void requireUpdated(int updated) {
        if (updated != 1) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.CONFLICT);
        }
    }

    private void appendAudit(MapSqlParameterSource parameters, String action, String safeDetailsSql) {
        // Both SQL expressions are private constants above; no client content enters SQL or audit details.
        requireInserted(jdbc.update("""
                INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details)
                VALUES (:actorId, :action, 'REQUEST', :requestId,
                """ + safeDetailsSql + ")", parameters.addValue("action", action)));
    }

    private static void requireInserted(int inserted) {
        if (inserted != 1) {
            throw new TechnicianCommandException(TechnicianCommandException.Code.UNAVAILABLE);
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("The requested operation is not available");
    }

    private record LockedRequest(UUID id, String status, long version) { }
}
