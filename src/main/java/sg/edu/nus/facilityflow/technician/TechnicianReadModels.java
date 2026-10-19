package sg.edu.nus.facilityflow.technician;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable role-specific reads for REQ-TECH-001–003/009. */
public final class TechnicianReadModels {
    private TechnicianReadModels() {}

    public enum RequestStatus { OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CLOSED, CANCELLED }
    public enum ManagerPriority { LOW, MEDIUM, HIGH, CRITICAL }
    public enum ReportedUrgency { LOW, NORMAL, HIGH, EMERGENCY }

    public record QueueFilter(String q, RequestStatus status, UUID categoryId, ManagerPriority priority) {
        public QueueFilter {
            q = q == null ? "" : q.strip();
        }
    }

    public record DashboardCounts(long assigned, long inProgress, long completed) {}
    public record CategoryOption(UUID id, String name) {}
    public record QueueRow(String displayId, String title, String location, UUID categoryId,
                           String category, RequestStatus status, ManagerPriority priority,
                           ReportedUrgency urgency, Instant assignedAt) {}

    public record QueueView(QueueFilter filter, DashboardCounts counts, List<CategoryOption> categories,
                            long assignedTotal, List<QueueRow> rows) {
        public QueueView {
            Objects.requireNonNull(filter, "filter");
            Objects.requireNonNull(counts, "counts");
            categories = List.copyOf(categories);
            rows = List.copyOf(rows);
        }
    }

    public record RequesterUpdateView(String note, String authorDisplayName, Instant createdAt) {}
    public record WorkLogView(String note, int minutes, String authorDisplayName, Instant createdAt) {}
    public record RequestDetail(QueueRow request, String description, Instant createdAt, Instant updatedAt,
                                long version, String resolutionSummary, Instant completedAt,
                                List<RequesterUpdateView> requesterUpdates, List<WorkLogView> workLogs) {
        public RequestDetail {
            Objects.requireNonNull(request, "request");
            requesterUpdates = List.copyOf(requesterUpdates);
            workLogs = List.copyOf(workLogs);
        }
    }
}
