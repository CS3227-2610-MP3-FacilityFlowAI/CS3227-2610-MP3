package sg.edu.nus.facilityflow.ai.monitoring;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** REQ-AI-009: bounded numeric portal snapshot, never inferred usage. */
public record AiUsageComparison(String portalInput, String portalOutput, String portalTotal) {
    public AiUsageComparison {
        portalInput = clean(portalInput);
        portalOutput = clean(portalOutput);
        portalTotal = clean(portalTotal);
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        check(errors, "portalInput", portalInput);
        check(errors, "portalOutput", portalOutput);
        check(errors, "portalTotal", portalTotal);
        return Map.copyOf(errors);
    }

    public BigDecimal input() { return new BigDecimal(portalInput); }
    public BigDecimal output() { return new BigDecimal(portalOutput); }
    public BigDecimal total() { return new BigDecimal(portalTotal); }
    private static void check(Map<String, String> errors, String field, String value) {
        if (!value.matches("[0-9]{1,19}")) errors.put(field, "Enter a non-negative whole token count (up to 19 digits).");
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }

    public record Result(BigDecimal inputDifference, BigDecimal outputDifference, BigDecimal totalDifference,
                         boolean incomplete) {
        public static Result compare(AiMonitoringReport.Counts app, AiUsageComparison portal) {
            return new Result(difference(portal.input(), app.inputTokens()), difference(portal.output(), app.outputTokens()),
                    difference(portal.total(), app.totalTokens()), app.pending() > 0 || app.missingInputUsage() > 0
                    || app.missingOutputUsage() > 0 || app.missingTotalUsage() > 0);
        }
        private static BigDecimal difference(BigDecimal portal, BigDecimal application) {
            return application == null ? null : portal.subtract(application);
        }
    }
}
