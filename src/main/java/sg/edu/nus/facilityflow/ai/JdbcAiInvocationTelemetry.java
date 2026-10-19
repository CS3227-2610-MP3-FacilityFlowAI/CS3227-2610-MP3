package sg.edu.nus.facilityflow.ai;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for append-once AI invocation lifecycle evidence (REQ-AI-007/008, AI-SEC-017/018). */
@Repository
public class JdbcAiInvocationTelemetry implements AiInvocationTelemetry {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JdbcAiInvocationTelemetry.class);
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAiInvocationTelemetry(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Invocation start(StartRequest request) {
        Objects.requireNonNull(request, "request");
        Invocation invocation = jdbc.queryForObject(
                """
                INSERT INTO facilityflow.ai_invocation
                    (actor_id, correlation_id, feature, model, template_version,
                     status, latency_ms, retry_count)
                VALUES (:actorId, :correlationId, :feature, :model, :templateVersion,
                        'STARTED', 0, 0)
                RETURNING id, created_at
                """,
                new MapSqlParameterSource()
                        .addValue("actorId", request.actorId())
                        .addValue("correlationId", request.correlationId())
                        .addValue("feature", request.feature().name())
                        .addValue("model", request.model())
                        .addValue("templateVersion", request.templateVersion()),
                (resultSet, rowNum) -> Invocation.started(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("created_at", java.time.OffsetDateTime.class).toInstant(), request));
        event(invocation).addKeyValue("event", "AI_INVOCATION_STARTED").log("AI invocation started");
        return invocation;
    }

    @Override
    public void finish(Invocation invocation, Terminal terminal) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(terminal, "terminal");
        int updated = jdbc.update(
                """
                UPDATE facilityflow.ai_invocation
                   SET status = :status,
                       latency_ms = :latencyMs,
                       retry_count = :retryCount,
                       input_tokens = :inputTokens,
                       output_tokens = :outputTokens,
                       total_tokens = :totalTokens
                 WHERE id = :id AND status = 'STARTED'
                """,
                new MapSqlParameterSource()
                        .addValue("status", terminal.status().name())
                        .addValue("latencyMs", terminal.latencyMs())
                        .addValue("retryCount", terminal.retryCount())
                        .addValue("inputTokens", terminal.inputTokens())
                        .addValue("outputTokens", terminal.outputTokens())
                        .addValue("totalTokens", terminal.totalTokens())
                        .addValue("id", invocation.id()));
        if (updated != 1) {
            throw new IllegalStateException("AI invocation terminal transition was not applied");
        }
        event(invocation).addKeyValue("event", "AI_INVOCATION_FINISHED")
                .addKeyValue("outcome", terminal.status()).addKeyValue("latency_ms", terminal.latencyMs())
                .addKeyValue("retry_count", terminal.retryCount()).addKeyValue("input_tokens", terminal.inputTokens())
                .addKeyValue("output_tokens", terminal.outputTokens()).addKeyValue("total_tokens", terminal.totalTokens())
                .log("AI invocation finished");
    }

    private static org.slf4j.spi.LoggingEventBuilder event(Invocation invocation) {
        return LOGGER.atInfo().addKeyValue("invocation_id", invocation.id()).addKeyValue("actor_id", invocation.actorId())
                .addKeyValue("correlation_id", invocation.correlationId()).addKeyValue("feature", invocation.feature())
                .addKeyValue("model", invocation.model()).addKeyValue("template_version", invocation.templateVersion());
    }
}
