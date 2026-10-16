package sg.edu.nus.facilityflow.requester;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

/** REQ-RQ-002/004/005/009: role-scoped use cases and safe page models. */
public interface RequesterRequests {
    List<Category> categories();
    List<Summary> list();
    Optional<Detail> detail(String displayId);
    String create(RequestDraft draft, String submissionId);

    record Category(UUID id, String name) { }

    record Summary(String displayId, String title, String location, String category,
            String reportedUrgency, String status, String managerPriority, Instant createdAt) {
        public String createdTime() { return localTime(createdAt); }
    }

    record Detail(String displayId, String title, String description, String location,
            String category, String reportedUrgency, String status, String managerPriority,
            Instant createdAt, Instant updatedAt, List<History> history) {
        public String createdTime() { return localTime(createdAt); }
        public String updatedTime() { return localTime(updatedAt); }
    }

    record History(String event, String note, String author, Instant occurredAt) {
        public String time() { return localTime(occurredAt); }
    }

    static String localTime(Instant time) {
        return DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss VV")
                .format(time.atZone(ZoneId.of("Asia/Singapore")));
    }
}
