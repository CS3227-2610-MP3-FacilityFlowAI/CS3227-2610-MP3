package sg.edu.nus.facilityflow.ai;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PreDestroy;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;

/** PostgreSQL-time Account admission and fenced global-slot lease adapter. */
@Repository
public class JdbcAiAdmissionBackend implements AiAdmissionBackend {
    private static final Duration LEASE_DURATION = Duration.ofSeconds(45);
    private static final Duration RENEWAL_PERIOD = Duration.ofSeconds(15);

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AiConfiguration.Available configuration;
    private final ScheduledExecutorService renewals;

    public JdbcAiAdmissionBackend(
            NamedParameterJdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            AiConfiguration configuration) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.configuration = configuration instanceof AiConfiguration.Available available ? available : null;
        this.renewals = this.configuration == null ? null : Executors.newScheduledThreadPool(
                1,
                runnable -> {
                    Thread thread = new Thread(runnable, "ai-lease-renewal");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @PreDestroy
    void close() {
        if (renewals != null) {
            renewals.shutdownNow();
        }
    }

    @Override
    public Optional<Lease> tryAcquire(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        if (configuration == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transaction.execute(status -> acquireInTransaction(accountId, status)));
    }

    private Lease acquireInTransaction(UUID accountId, org.springframework.transaction.TransactionStatus status) {
        jdbc.update("""
                INSERT INTO facilityflow.rate_limit_bucket (scope, account_id)
                VALUES ('ACCOUNT_RATE', :accountId)
                ON CONFLICT DO NOTHING
                """, Map.of("accountId", accountId));

        Map<String, Object> window = jdbc.queryForMap("""
                SELECT cardinality(ARRAY(
                           SELECT t FROM unnest(invocation_times) t
                           WHERE t >= CURRENT_TIMESTAMP - interval '1 minute')) AS minute_count,
                       cardinality(ARRAY(
                           SELECT t FROM unnest(invocation_times) t
                           WHERE t >= CURRENT_TIMESTAMP - interval '1 hour')) AS hour_count
                  FROM facilityflow.rate_limit_bucket
                 WHERE scope = 'ACCOUNT_RATE' AND account_id = :accountId
                 FOR UPDATE
                """, Map.of("accountId", accountId));
        int minuteCount = ((Number) window.get("minute_count")).intValue();
        int hourCount = ((Number) window.get("hour_count")).intValue();
        if (minuteCount >= configuration.ratePerMinute() || hourCount >= configuration.ratePerHour()) {
            pruneAccountWindow(accountId);
            return null;
        }

        UUID owner = UUID.randomUUID();
        java.util.List<Map<String, Object>> slots = jdbc.queryForList("""
                SELECT id, slot, version
                  FROM facilityflow.rate_limit_bucket
                 WHERE scope = 'GLOBAL_SLOT'
                   AND (lease_expires_at IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP)
                   AND slot <= :maxInFlight
                 ORDER BY slot
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED
                """, Map.of("maxInFlight", configuration.maxInFlight()));
        if (slots.isEmpty()) {
            status.setRollbackOnly();
            return null;
        }
        Map<String, Object> slot = slots.get(0);
        UUID slotId = (UUID) slot.get("id");
        short slotNumber = ((Number) slot.get("slot")).shortValue();
        long previousVersion = ((Number) slot.get("version")).longValue();
        long fencingVersion = previousVersion + 1;
        int claimed = jdbc.update("""
                UPDATE facilityflow.rate_limit_bucket
                   SET lease_owner = :owner,
                       lease_expires_at = CURRENT_TIMESTAMP + interval '45 seconds',
                       version = :fencingVersion,
                       invocation_times = '{}'
                 WHERE id = :slotId
                   AND version = :previousVersion
                   AND (lease_expires_at IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("fencingVersion", fencingVersion)
                .addValue("slotId", slotId)
                .addValue("previousVersion", previousVersion));
        if (claimed != 1) {
            status.setRollbackOnly();
            return null;
        }
        jdbc.update("""
                UPDATE facilityflow.rate_limit_bucket
                   SET invocation_times = ARRAY(
                         SELECT t FROM unnest(invocation_times) t
                         WHERE t >= CURRENT_TIMESTAMP - interval '1 hour')
                         || ARRAY[CURRENT_TIMESTAMP]::timestamptz[]
                 WHERE scope = 'ACCOUNT_RATE' AND account_id = :accountId
                """, Map.of("accountId", accountId));
        return new JdbcLease(slotId, slotNumber, fencingVersion, owner);
    }

    private void pruneAccountWindow(UUID accountId) {
        jdbc.update("""
                UPDATE facilityflow.rate_limit_bucket
                   SET invocation_times = ARRAY(
                         SELECT t FROM unnest(invocation_times) t
                         WHERE t >= CURRENT_TIMESTAMP - interval '1 hour')
                 WHERE scope = 'ACCOUNT_RATE' AND account_id = :accountId
                """, Map.of("accountId", accountId));
    }

    private final class JdbcLease implements Lease {
        private final UUID slotId;
        private final short slot;
        private final long fencingVersion;
        private final UUID owner;
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean lost = new AtomicBoolean();
        private final ScheduledFuture<?> renewal;

        private JdbcLease(UUID slotId, short slot, long fencingVersion, UUID owner) {
            this.slotId = slotId;
            this.slot = slot;
            this.fencingVersion = fencingVersion;
            this.owner = owner;
            this.renewal = renewals.scheduleAtFixedRate(
                    this::renew, RENEWAL_PERIOD.toSeconds(), RENEWAL_PERIOD.toSeconds(), TimeUnit.SECONDS);
        }

        private void renew() {
            if (released.get()) {
                return;
            }
            try {
                int updated = jdbc.update("""
                        UPDATE facilityflow.rate_limit_bucket
                           SET lease_expires_at = CURRENT_TIMESTAMP + interval '45 seconds'
                         WHERE id = :slotId AND slot = :slot
                           AND lease_owner = :owner AND version = :fencingVersion
                           AND lease_expires_at > CURRENT_TIMESTAMP
                        """, new MapSqlParameterSource()
                        .addValue("slotId", slotId)
                        .addValue("slot", slot)
                        .addValue("owner", owner)
                        .addValue("fencingVersion", fencingVersion));
                if (updated != 1) {
                    lost.set(true);
                    release();
                }
            } catch (RuntimeException ignored) {
                // A failed renewal leaves the lease to expire; provider work is
                // still discarded by the owning request when terminal handling runs.
                lost.set(true);
                release();
            }
        }

        @Override
        public void release() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            renewal.cancel(false);
            try {
                jdbc.update("""
                        UPDATE facilityflow.rate_limit_bucket
                           SET lease_owner = NULL, lease_expires_at = NULL
                         WHERE id = :slotId AND slot = :slot
                           AND lease_owner = :owner AND version = :fencingVersion
                        """, new MapSqlParameterSource()
                        .addValue("slotId", slotId)
                        .addValue("slot", slot)
                        .addValue("owner", owner)
                        .addValue("fencingVersion", fencingVersion));
            } catch (RuntimeException ignored) {
                // Expiry is the recovery path when release cannot reach the DB.
            }
        }

        @Override
        public boolean valid() {
            return !lost.get() && !released.get();
        }
    }
}
