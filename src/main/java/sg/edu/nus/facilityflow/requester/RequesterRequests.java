package sg.edu.nus.facilityflow.requester;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

/** REQ-RQ-002/004/005/009: role-scoped use cases and safe page models. */
public interface RequesterRequests {
    List<String> STATUSES = List.of("OPEN", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED");
    ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Singapore");
    List<Category> categories();
    Dashboard dashboard();
    ListPage list(RequestFilters filters);
    Optional<Detail> detail(String displayId);
    String create(RequestDraft draft, String submissionId);
    String edit(String displayId, RequestDraft draft, String version);
    String cancel(String displayId, String reason, boolean confirmed, String version);
    String addUpdate(String displayId, String note, String version);

    record Category(UUID id, String name) { }
    record StatusCount(String status, long count) { }
    record Dashboard(List<StatusCount> counts, List<Summary> recent) { }
    record ListPage(List<Summary> requests, List<Category> categories, boolean hasRequests, Map<String, String> errors) { }

    record Summary(String displayId, String title, String location, String category,
            String reportedUrgency, String status, String managerPriority, Instant createdAt) {
        public String createdTime() { return localTime(createdAt); }
    }

    record Detail(String displayId, String title, String description, String location,
            String category, String reportedUrgency, String status, String managerPriority,
            Instant createdAt, Instant updatedAt, String categoryId, long version, List<History> history) {
        public String createdTime() { return localTime(createdAt); }
        public String updatedTime() { return localTime(updatedAt); }
    }

    record History(String event, String note, String author, Instant occurredAt) {
        public String time() { return localTime(occurredAt); }
    }

    static String localTime(Instant time) {
        return DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss VV")
                .format(time.atZone(DISPLAY_ZONE));
    }
}
