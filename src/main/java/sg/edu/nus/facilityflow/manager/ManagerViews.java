package sg.edu.nus.facilityflow.manager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ManagerViews {
    DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a VV")
            .withZone(ZoneId.of("Asia/Singapore"));

    Overview overview();

    RequestPage requests(ManagerRequestFilters filters);

    AuditPage audit(ManagerAuditFilters filters);

    CreationOptions creationOptions();

    String createOnBehalf(ManagerRequestDraft draft);

    record Count(String label, long count) { }

    record TechnicianWorkload(UUID id, String username, String displayName, long activeRequests) { }

    record Overview(List<Count> statuses, List<Count> priorities,
                    List<TechnicianWorkload> technicianWorkloads) { }

    record Option(UUID id, String label) { }

    record RequestSummary(
            String displayId,
            String title,
            String location,
            String category,
            String urgency,
            String priority,
            String status,
            String requester,
            String technician,
            Instant createdAt,
            Instant updatedAt) {
        public String createdTime() {
            return DISPLAY_TIME.format(createdAt);
        }

        public String updatedTime() {
            return DISPLAY_TIME.format(updatedAt);
        }
    }

    record RequestPage(List<RequestSummary> rows, List<Option> categories,
                       List<Option> technicians, boolean hasRequests,
                       Map<String, String> errors) { }

    record AuditRow(Instant occurredAt, String actor, String action,
                    String targetType, String requestDisplayId) {
        public String occurredTime() {
            return DISPLAY_TIME.format(occurredAt);
        }
    }

    record AuditPage(List<AuditRow> rows, boolean hasEvents, Map<String, String> errors) { }

    record CreationOptions(List<Option> requesters, List<Option> categories) { }
}
