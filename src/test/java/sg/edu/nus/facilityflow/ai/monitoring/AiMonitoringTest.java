package sg.edu.nus.facilityflow.ai.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.JdbcAiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.identity.*;

class AiMonitoringTest {
    private static EmbeddedPostgres postgres;
    private static JdbcTemplate admin;
    private static JdbcTemplate app;
    private static DataSource appSource;
    private UUID actor;

    @BeforeAll static void database() throws Exception {
        postgres = EmbeddedPostgres.start();
        admin = new JdbcTemplate(postgres.getPostgresDatabase());
        try (var connection = postgres.getPostgresDatabase().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("workflow/database/bootstrap-roles.sql"));
        }
        admin.execute("ALTER ROLE facilityflow_migrator LOGIN");
        admin.execute("ALTER ROLE facilityflow_app LOGIN");
        Flyway.configure().dataSource(source("facilityflow_migrator")).schemas("facilityflow")
                .defaultSchema("facilityflow").cleanDisabled(true).ignoreMigrationPatterns(new String[0])
                .target("6").load().migrate();
        appSource = source("facilityflow_app");
        app = new JdbcTemplate(appSource);
    }

    @AfterAll static void close() throws Exception { if (postgres != null) postgres.close(); }

    @BeforeEach void reset() {
        admin.execute("TRUNCATE facilityflow.ai_invocation");
        actor = app.queryForObject("INSERT INTO facilityflow.user_account (username, display_name, password_hash, role) "
                + "VALUES (?, 'Synthetic Manager', 'synthetic', 'FACILITIES_MANAGER') RETURNING id", UUID.class,
                "monitor-" + UUID.randomUUID().toString().substring(0, 10));
    }

    @Test @DisplayName("REQ-AI-007/008: aggregates distinguish unknown usage, retries, unfinished and failed invocations")
    void aggregatesFinishedOutcomesWithoutInventingUsage() {
        row("SMART_REPORT", "SUCCEEDED", "2026-01-01T16:00:00Z", 100, 0, 10L, 20L, 30L);
        row("WORK_PLAN", "SUCCEEDED", "2026-01-02T00:00:00Z", 300, 1, 0L, 0L, 0L);
        row("TRIAGE", "DISCARDED_ACCESS_CHANGED", "2026-01-02T03:00:00Z", 500, 0, null, null, null);
        row("TRIAGE", "STARTED", "2026-01-02T04:00:00Z", 0, 0, null, null, null);
        var result = service(0).read(new AiMonitoringFilter("2026-01-02", "2026-01-02", "")).report();
        var totals = result.totals();
        assertEquals(4, totals.admitted());
        assertEquals(3, totals.finished());
        assertEquals(2, totals.succeeded());
        assertEquals(1, totals.pending());
        assertEquals(1, totals.unresolved());
        assertEquals(1, totals.retries());
        assertEquals(new BigDecimal("30"), totals.totalTokens());
        assertEquals(1, totals.missingTotalUsage());
        assertEquals(300d, totals.medianMs());
        assertEquals(480d, totals.p95Ms());
        assertEquals(new BigDecimal("66.7"), totals.successPercent());
        assertEquals(3, result.breakdown().size());
        assertTrue(result.outcomes().stream().anyMatch(value -> value.status().equals("DISCARDED_ACCESS_CHANGED")));
        assertFalse(result.toString().contains(actor.toString()));
        assertEquals(0, app.queryForObject("SELECT count(*) FROM facilityflow.audit_event", Integer.class));
    }

    @Test @DisplayName("REQ-SH-009 / REQ-AI-008: inclusive local-day boundaries and assistant filter are exact")
    void filtersBySingaporeDayAndFeature() {
        row("SMART_REPORT", "SUCCEEDED", "2026-01-01T15:59:59Z", 1, 0, null, null, null);
        row("SMART_REPORT", "SUCCEEDED", "2026-01-01T16:00:00Z", 1, 0, null, null, null);
        row("SMART_REPORT", "SUCCEEDED", "2026-01-02T15:59:59Z", 1, 0, null, null, null);
        row("SMART_REPORT", "SUCCEEDED", "2026-01-02T16:00:00Z", 1, 0, null, null, null);
        row("WORK_PLAN", "SUCCEEDED", "2026-01-02T01:00:00Z", 1, 0, null, null, null);
        var counts = service(0).read(new AiMonitoringFilter("2026-01-02", "2026-01-02", "SMART_REPORT")).report().totals();
        assertEquals(2, counts.admitted());
        assertNull(counts.totalTokens());
        assertEquals(2, counts.missingTotalUsage());
        var empty = service(0).read(new AiMonitoringFilter("2025-01-01", "2025-01-01", "")).report().totals();
        assertEquals(0, empty.admitted());
        assertNull(empty.totalTokens());
        assertNull(empty.p95Ms());
        assertNull(empty.successPercent());
    }

    @Test @DisplayName("REQ-AI-008: recent STARTED records stay pending and unresolved records are never rewritten")
    void distinguishesRecentAndUnresolvedStartedRecords() {
        row("TRIAGE", "STARTED", "2026-01-02T04:00:00Z", 0, 0, null, null, null);
        admin.update("INSERT INTO facilityflow.ai_invocation (actor_id, correlation_id, feature, model, template_version, "
                        + "status, latency_ms, retry_count) VALUES (?, ?, 'TRIAGE', 'synthetic-model', 'v1', 'STARTED', 0, 0)",
                actor, UUID.randomUUID());
        var result = service(0).read(new AiMonitoringFilter("", "", "")).report().totals();
        assertEquals(2, result.pending());
        assertEquals(1, result.unresolved());
        assertEquals(0, result.finished());
        assertNull(result.successPercent());
        assertNull(result.p95Ms());
        assertNull(result.totalTokens());
        assertEquals(2, app.queryForObject("SELECT count(*) FROM facilityflow.ai_invocation WHERE status = 'STARTED'", Integer.class));
    }

    @Test @DisplayName("REQ-SH-002 / REQ-AI-008: persisted role, active state and session version protect aggregate access")
    void rejectsRevokedManagerAndInvalidFiltersDoNotReachSql() {
        assertFalse(service(0).read(new AiMonitoringFilter("2026-02-30", "", "TRIAGE' OR true --")).errors().isEmpty());
        assertNull(service(0).read(new AiMonitoringFilter("", "", "unknown")).report());
        app.update("UPDATE facilityflow.user_account SET session_version = 1 WHERE id = ?", actor);
        assertThrows(AccessDeniedException.class, () -> service(0).read(new AiMonitoringFilter("", "", "")));
        app.update("UPDATE facilityflow.user_account SET active = false WHERE id = ?", actor);
        assertThrows(AccessDeniedException.class, () -> service(1).read(new AiMonitoringFilter("", "", "")));
        app.update("UPDATE facilityflow.user_account SET active = true, role = 'REQUESTER' WHERE id = ?", actor);
        assertThrows(AccessDeniedException.class, () -> service(1).read(new AiMonitoringFilter("", "", "")));
    }

    @Test @DisplayName("REQ-AI-009: reconciliation preserves original records and reports missing coverage and signed differences")
    void comparesPortalSnapshotWithoutRewritingUsage() {
        row("SMART_REPORT", "SUCCEEDED", "2026-01-02T00:00:00Z", 50, 0, 10L, 20L, 30L);
        row("WORK_PLAN", "AI_TIMEOUT", "2026-01-02T00:01:00Z", 100, 1, null, null, null);
        var filter = new AiMonitoringFilter("2026-01-02", "2026-01-02", "");
        var comparison = service(0).reconcile(filter, new AiUsageComparison("12", "24", "36"));
        assertTrue(comparison.errors().isEmpty());
        assertEquals(new BigDecimal("6"), comparison.result().totalDifference());
        assertTrue(comparison.result().incomplete());
        assertEquals(new BigDecimal("30"), service(0).read(filter).report().totals().totalTokens());
        assertEquals(2, app.queryForObject("SELECT count(*) FROM facilityflow.ai_invocation", Integer.class));
        assertTrue(service(0).reconcile(new AiMonitoringFilter("", "", ""),
                new AiUsageComparison("1", "2", "3")).errors().containsKey("period"));
    }

    @Test @DisplayName("AI-SEC-017/018: STARTED evidence survives failed finish and terminal transition applies only once")
    void recordsExactlyOneDurableLifecycle() {
        var telemetry = new JdbcAiInvocationTelemetry(new NamedParameterJdbcTemplate(appSource));
        var correlation = UUID.randomUUID();
        var invocation = telemetry.start(new AiInvocationTelemetry.StartRequest(actor, correlation,
                AiInvocationTelemetry.Feature.WORK_PLAN, "synthetic-model", "work-plan-v1"));
        assertEquals(correlation, invocation.correlationId());
        admin.execute("ALTER TABLE facilityflow.ai_invocation ADD CONSTRAINT reject_success CHECK (status <> 'SUCCEEDED') NOT VALID");
        var terminal = new AiInvocationTelemetry.Terminal(AiInvocationTelemetry.Status.SUCCEEDED, 20, 0, null, null, null);
        try { assertThrows(DataAccessException.class, () -> telemetry.finish(invocation, terminal)); }
        finally { admin.execute("ALTER TABLE facilityflow.ai_invocation DROP CONSTRAINT reject_success"); }
        assertEquals("STARTED", app.queryForObject("SELECT status FROM facilityflow.ai_invocation WHERE id = ?", String.class, invocation.id()));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(JdbcAiInvocationTelemetry.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> telemetry.finish(invocation, terminal)).join();
            var event = events.list.getFirst();
            assertNull(event.getThrowableProxy());
            assertTrue(event.getKeyValuePairs().stream().anyMatch(value -> value.key.equals("correlation_id")
                    && correlation.equals(value.value)));
            assertTrue(event.getKeyValuePairs().stream().anyMatch(value -> value.key.equals("outcome")
                    && terminal.status().equals(value.value)));
            assertTrue(event.getKeyValuePairs().stream().noneMatch(value -> java.util.Set.of("prompt", "response", "request")
                    .contains(value.key)));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
        assertThrows(IllegalStateException.class, () -> telemetry.finish(invocation, terminal));
        assertEquals(1, service(0).read(new AiMonitoringFilter("", "", "")).report().totals().admitted());
    }

    private AiMonitoring service(long version) {
        return new AiMonitoring(() -> new CurrentAccount(actor, "synthetic", "Synthetic", AccountRole.FACILITIES_MANAGER, version),
                new JdbcAccountRepository(new NamedParameterJdbcTemplate(appSource)), new NamedParameterJdbcTemplate(appSource),
                AiProviderLifecycle.disabled(), new DataSourceTransactionManager(appSource));
    }

    private void row(String feature, String status, String time, long latency, int retries, Long input, Long output, Long total) {
        admin.update("INSERT INTO facilityflow.ai_invocation (actor_id, correlation_id, feature, model, template_version, status, "
                        + "latency_ms, retry_count, input_tokens, output_tokens, total_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                actor, UUID.randomUUID(), feature, "synthetic-model", "v1", status, latency, retries, input, output, total, OffsetDateTime.parse(time));
    }

    private static DataSource source(String role) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[]{"localhost"}); source.setPortNumbers(new int[]{postgres.getPort()});
        source.setDatabaseName("postgres"); source.setUser(role); return source;
    }
}
