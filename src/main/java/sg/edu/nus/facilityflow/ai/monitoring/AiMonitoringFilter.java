package sg.edu.nus.facilityflow.ai.monitoring;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** REQ-AI-008/009, REQ-SH-009: inclusive Singapore dates over UTC invocation times. */
public record AiMonitoringFilter(String from, String to, String feature) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
    public static final Set<String> FEATURES = Set.of("SMART_REPORT", "WORK_PLAN", "TRIAGE");

    public AiMonitoringFilter {
        from = clean(from);
        to = clean(to);
        feature = clean(feature);
    }

    public Map<String, String> errors() {
        var errors = new LinkedHashMap<String, String>();
        LocalDate first = date(from, "from", errors);
        LocalDate last = date(to, "to", errors);
        if (first != null && last != null && last.isBefore(first)) {
            errors.put("to", "End date must be on or after the start date.");
        }
        if (!feature.isEmpty() && !FEATURES.contains(feature)) {
            errors.put("feature", "Choose a listed assistant.");
        }
        return Map.copyOf(errors);
    }

    public OffsetDateTime start() { return from.isEmpty() ? null : LocalDate.parse(from).atStartOfDay(ZONE).toOffsetDateTime(); }
    public OffsetDateTime before() { return to.isEmpty() ? null : LocalDate.parse(to).plusDays(1).atStartOfDay(ZONE).toOffsetDateTime(); }

    private static LocalDate date(String value, String field, Map<String, String> errors) {
        if (value.isEmpty()) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
                throw new IllegalArgumentException();
            }
            return LocalDate.parse(value);
        } catch (RuntimeException invalid) {
            errors.put(field, "Enter a valid date in YYYY-MM-DD format (years 0001–9999).");
            return null;
        }
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
