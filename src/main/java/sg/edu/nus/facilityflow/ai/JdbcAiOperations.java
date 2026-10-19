package sg.edu.nus.facilityflow.ai;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** AI-SEC-008/017/021: cross-instance rolling admission, expiring slots and metadata only. */
@Repository
public class JdbcAiOperations implements AiOperations {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final int minute;
    private final int hour;
    private final int slots;

    public JdbcAiOperations(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Value("${AI_RATE_PER_MINUTE:5}") int minute, @Value("${AI_RATE_PER_HOUR:30}") int hour,
            @Value("${AI_MAX_IN_FLIGHT:5}") int slots) {
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(manager);
        transactions.setTimeout(5);
        this.minute = Math.clamp(minute, 1, 5);
        this.hour = Math.clamp(hour, 1, 30);
        this.slots = Math.clamp(slots, 1, 5);
    }

    public Permit acquire(UUID actor, int timeoutSeconds) {
        return transactions.execute(tx -> {
            jdbc.execute("SET LOCAL lock_timeout = '2s'");
            jdbc.execute("SET LOCAL statement_timeout = '5s'");
            jdbc.update("INSERT INTO facilityflow.rate_limit_bucket (scope, account_id) VALUES ('ACCOUNT_RATE', ?) ON CONFLICT DO NOTHING", actor);
            jdbc.queryForObject("SELECT id FROM facilityflow.rate_limit_bucket WHERE scope = 'ACCOUNT_RATE' AND account_id = ? FOR UPDATE", UUID.class, actor);
            var now = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
            boolean allowed = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT (SELECT count(*) FROM unnest(invocation_times) t WHERE t > ?::timestamptz - interval '1 minute') < ?
                       AND (SELECT count(*) FROM unnest(invocation_times) t WHERE t > ?::timestamptz - interval '1 hour') < ?
                      FROM facilityflow.rate_limit_bucket WHERE scope = 'ACCOUNT_RATE' AND account_id = ?
                    """, Boolean.class, now, minute, now, hour, actor));
            if (!allowed) return null;
            var permits = jdbc.query("""
                    UPDATE facilityflow.rate_limit_bucket SET lease_expires_at = clock_timestamp() + (? * interval '1 second'), version = version + 1
                     WHERE id = (SELECT id FROM facilityflow.rate_limit_bucket WHERE scope = 'GLOBAL_SLOT' AND slot <= ?
                       AND (lease_expires_at IS NULL OR lease_expires_at <= clock_timestamp()) ORDER BY slot LIMIT 1 FOR UPDATE SKIP LOCKED)
                     RETURNING id, version
                    """, (row, i) -> new Permit(row.getObject("id", UUID.class), row.getLong("version")), timeoutSeconds + 15, slots);
            if (permits.isEmpty()) return null;
            jdbc.update("""
                    UPDATE facilityflow.rate_limit_bucket
                       SET invocation_times = ARRAY(SELECT t FROM unnest(invocation_times) t WHERE t > ?::timestamptz - interval '1 hour') || ARRAY[?::timestamptz],
                           version = version + 1
                     WHERE scope = 'ACCOUNT_RATE' AND account_id = ?
                    """, now, now, actor);
            return permits.getFirst();
        });
    }

    public void release(Permit permit) {
        jdbc.update("UPDATE facilityflow.rate_limit_bucket SET lease_expires_at = NULL, version = version + 1 WHERE id = ? AND version = ?",
                permit.slotId(), permit.version());
    }

}
