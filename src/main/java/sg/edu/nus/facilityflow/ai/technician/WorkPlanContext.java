package sg.edu.nus.facilityflow.ai.technician;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Fixed allowlisted server-side context for one eligible Technician Request. */
public record WorkPlanContext(
        UUID requestId,
        String displayId,
        UUID assignedTechnicianId,
        String title,
        String description,
        String location,
        String category,
        String reportedUrgency,
        String managerPriority,
        String status,
        long requestVersion,
        List<RequesterUpdate> requesterUpdates) {

    public WorkPlanContext {
        Objects.requireNonNull(requestId, "requestId");
        displayId = bounded(displayId, "displayId", 20);
        Objects.requireNonNull(assignedTechnicianId, "assignedTechnicianId");
        title = bounded(title, "title", 100);
        description = bounded(description, "description", 2000);
        location = bounded(location, "location", 120);
        category = bounded(category, "category", 50);
        reportedUrgency = bounded(reportedUrgency, "reportedUrgency", 20);
        managerPriority = bounded(managerPriority, "managerPriority", 20);
        status = bounded(status, "status", 20);
        if (!status.equals("ASSIGNED") && !status.equals("IN_PROGRESS")) {
            throw new IllegalArgumentException("Work Plan requires an assigned active Request.");
        }
        if (requestVersion < 0) {
            throw new IllegalArgumentException("requestVersion must be non-negative.");
        }
        requesterUpdates = List.copyOf(Objects.requireNonNull(requesterUpdates, "requesterUpdates"));
        if (requesterUpdates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("requesterUpdates cannot contain null values.");
        }
    }

    /** Selects the latest ten by descending time/id, then sends them oldest-first. */
    public List<RequesterUpdate> selectedRequesterUpdates() {
        return requesterUpdates.stream()
                .sorted(Comparator.comparing(RequesterUpdate::createdAt).reversed()
                        .thenComparing(RequesterUpdate::id, Comparator.reverseOrder()))
                .limit(10)
                .sorted(Comparator.comparing(RequesterUpdate::createdAt)
                        .thenComparing(RequesterUpdate::id))
                .toList();
    }

    private static String bounded(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String normalized = Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'),
                Normalizer.Form.NFC).trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException(name + " is invalid.");
        }
        if (normalized.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\t')) {
            throw new IllegalArgumentException(name + " contains a disallowed control character.");
        }
        return normalized;
    }
}
