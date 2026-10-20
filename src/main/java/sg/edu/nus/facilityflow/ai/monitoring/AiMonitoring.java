package sg.edu.nus.facilityflow.ai.monitoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.identity.AccountRepository;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;

/** REQ-AI-008/009: one module for authorized usage aggregation and portal comparisons. */
@Service
public final class AiMonitoring {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiMonitoring.class);
    private static final String COUNTS = """
            count(*) AS admitted,
            count(*) FILTER (WHERE status <> 'STARTED') AS finished,
            count(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
            count(*) FILTER (WHERE status = 'STARTED') AS pending,
            count(*) FILTER (WHERE status = 'STARTED' AND created_at < CURRENT_TIMESTAMP - interval '1 minute') AS unresolved,
            coalesce(sum(retry_count), 0) AS retries,
            sum(input_tokens) AS input_tokens, sum(output_tokens) AS output_tokens, sum(total_tokens) AS total_tokens,
            count(*) FILTER (WHERE status <> 'STARTED' AND input_tokens IS NULL) AS missing_input,
            count(*) FILTER (WHERE status <> 'STARTED' AND output_tokens IS NULL) AS missing_output,
            count(*) FILTER (WHERE status <> 'STARTED' AND total_tokens IS NULL) AS missing_total,
            percentile_cont(0.5) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE status <> 'STARTED') AS median_ms,
            percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE status <> 'STARTED') AS p95_ms
            """;

    private final CurrentAccountProvider currentAccounts;
    private final AccountRepository accounts;
    private final NamedParameterJdbcTemplate jdbc;
    private final AiProviderLifecycle lifecycle;
    private final TransactionTemplate transactions;

    public AiMonitoring(CurrentAccountProvider currentAccounts, AccountRepository accounts,
            NamedParameterJdbcTemplate jdbc, AiProviderLifecycle lifecycle, PlatformTransactionManager manager) {
        this.currentAccounts = currentAccounts;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.lifecycle = lifecycle;
        transactions = new TransactionTemplate(manager);
        transactions.setReadOnly(true);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactions.setTimeout(10);
    }

    public Page read(AiMonitoringFilter filter) {
        var actor = currentAccounts.requireCurrentAccount();
        if (actor.role() != AccountRole.FACILITIES_MANAGER) throw denied();
        return transactions.execute(tx -> {
            var persisted = accounts.findById(actor.id()).orElseThrow(AiMonitoring::denied);
            if (!persisted.active() || persisted.role() != AccountRole.FACILITIES_MANAGER
                    || persisted.sessionVersion() != actor.sessionVersion()) throw denied();
            Map<String, String> errors = filter.errors();
            return new Page(errors.isEmpty() ? aggregate(filter) : null, errors, lifecycle.state(),
                    OffsetDateTime.now(AiMonitoringFilter.ZONE));
        });
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("The requested operation is not available"); }

    public ComparisonPage reconcile(AiMonitoringFilter filter, AiUsageComparison portal) {
        Page page = read(filter);
        var errors = new java.util.LinkedHashMap<>(page.errors());
        errors.putAll(portal.errors());
        if (filter.from().isEmpty() || filter.to().isEmpty()) {
            errors.put("period", "Select both period dates before recording a comparison.");
        }
        if (!filter.feature().isEmpty()) {
            errors.put("feature", "Choose all assistants to compare the application's full portal scope.");
        }
        if (!errors.isEmpty()) return new ComparisonPage(page, Map.copyOf(errors), null);
        var result = AiUsageComparison.Result.compare(page.report().totals(), portal);
        var counts = page.report().totals();
        LOGGER.atInfo()
                .addKeyValue("event", "AI_USAGE_RECONCILED")
                .addKeyValue("correlation_id", sg.edu.nus.facilityflow.operations.RequestCorrelationFilter.currentId())
                .addKeyValue("period_from_singapore", filter.from()).addKeyValue("period_to_singapore", filter.to())
                .addKeyValue("portal_input_tokens", portal.input()).addKeyValue("portal_output_tokens", portal.output())
                .addKeyValue("portal_total_tokens", portal.total()).addKeyValue("app_input_tokens", counts.inputTokens())
                .addKeyValue("app_output_tokens", counts.outputTokens()).addKeyValue("app_total_tokens", counts.totalTokens())
                .addKeyValue("input_difference", result.inputDifference()).addKeyValue("output_difference", result.outputDifference())
                .addKeyValue("total_difference", result.totalDifference()).addKeyValue("incomplete", result.incomplete())
                .addKeyValue("pending_count", counts.pending()).addKeyValue("missing_input_usage", counts.missingInputUsage())
                .addKeyValue("missing_output_usage", counts.missingOutputUsage()).addKeyValue("missing_total_usage", counts.missingTotalUsage())
                .log("Application and portal token snapshot compared");
        return new ComparisonPage(page, Map.of(), result);
    }

    public record ComparisonPage(Page page, Map<String, String> errors, AiUsageComparison.Result result) {}

    public record Page(AiMonitoringReport report, Map<String, String> errors,
                       AiProviderLifecycle.State readiness, OffsetDateTime generatedAt) {}
    private AiMonitoringReport aggregate(AiMonitoringFilter filter) {
        var parameters = new MapSqlParameterSource();
        var where = new StringBuilder(" FROM facilityflow.ai_invocation WHERE true");
        if (filter.start() != null) { where.append(" AND created_at >= :start"); parameters.addValue("start", filter.start()); }
        if (filter.before() != null) { where.append(" AND created_at < :before"); parameters.addValue("before", filter.before()); }
        if (!filter.feature().isEmpty()) { where.append(" AND feature = :feature"); parameters.addValue("feature", filter.feature()); }
        var totals = jdbc.queryForObject("SELECT " + COUNTS + where, parameters, (rs, n) -> counts(rs));
        var breakdown = jdbc.query("SELECT feature, model, template_version, " + COUNTS + where
                        + " GROUP BY feature, model, template_version ORDER BY feature, model NULLS FIRST, template_version",
                parameters, (rs, n) -> new AiMonitoringReport.Breakdown(rs.getString("feature"), rs.getString("model"),
                        rs.getString("template_version"), counts(rs)));
        var outcomes = jdbc.query("SELECT status, count(*) AS total" + where + " GROUP BY status ORDER BY status",
                parameters, (rs, n) -> new AiMonitoringReport.Outcome(rs.getString("status"), rs.getLong("total")));
        return new AiMonitoringReport(totals, breakdown, outcomes);
    }

    private static AiMonitoringReport.Counts counts(ResultSet rs) throws SQLException {
        return new AiMonitoringReport.Counts(rs.getLong("admitted"), rs.getLong("finished"), rs.getLong("succeeded"),
                rs.getLong("pending"), rs.getLong("unresolved"), rs.getLong("retries"), rs.getBigDecimal("input_tokens"),
                rs.getBigDecimal("output_tokens"), rs.getBigDecimal("total_tokens"), rs.getLong("missing_input"),
                rs.getLong("missing_output"), rs.getLong("missing_total"),
                (Double) rs.getObject("median_ms"), (Double) rs.getObject("p95_ms"));
    }
}
