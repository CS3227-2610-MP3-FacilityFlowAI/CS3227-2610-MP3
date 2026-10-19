package sg.edu.nus.facilityflow.ai;

import java.util.Set;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

/** REQ-RQ-013: current unsaved input may be incomplete, but never exceed field limits. */
public record SmartReportInput(String roughText, String title, String description, String location,
        String categoryId, String reportedUrgency) {
    public SmartReportInput {
        roughText = trim(roughText);
        title = trim(title);
        description = trim(description);
        location = trim(location);
        categoryId = trim(categoryId);
        reportedUrgency = trim(reportedUrgency);
    }

    public boolean valid() {
        return bounded(roughText, 2000) && bounded(title, 100) && bounded(description, 2000) && bounded(location, 120)
                && !(roughText + title + description + location).isBlank()
                && (categoryId.isEmpty() || RequestDraft.validUuid(categoryId))
                && (reportedUrgency.isEmpty() || Set.of("LOW", "NORMAL", "HIGH", "EMERGENCY").contains(reportedUrgency));
    }

    private static boolean bounded(String value, int maximum) {
        return value.codePointCount(0, value.length()) <= maximum
                && value.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }
}
