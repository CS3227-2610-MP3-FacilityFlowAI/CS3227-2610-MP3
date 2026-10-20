package sg.edu.nus.facilityflow.ai.monitoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Aggregate projections only; no invocation, actor, correlation or Request identifiers. */
public record AiMonitoringReport(Counts totals, List<Breakdown> breakdown, List<Outcome> outcomes) {
    public AiMonitoringReport {
        breakdown = List.copyOf(breakdown);
        outcomes = List.copyOf(outcomes);
    }

    public record Counts(long admitted, long finished, long succeeded, long pending, long unresolved,
                         long retries, BigDecimal inputTokens, BigDecimal outputTokens, BigDecimal totalTokens,
                         long missingInputUsage, long missingOutputUsage, long missingTotalUsage,
                         Double medianMs, Double p95Ms) {
        public BigDecimal successPercent() {
            return finished == 0 ? null : BigDecimal.valueOf(succeeded).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(finished), 1, RoundingMode.HALF_UP);
        }
    }
    public record Breakdown(String feature, String model, String templateVersion, Counts counts) {}
    public record Outcome(String status, long count) {}
}
