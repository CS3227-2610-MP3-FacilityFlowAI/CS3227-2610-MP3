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

    default RequestDetail request(String displayId) { throw new UnsupportedOperationException(); }

    AuditPage audit(ManagerAuditFilters filters);

    CreationOptions creationOptions();

    String createOnBehalf(ManagerRequestDraft draft);

    default void assign(String displayId, ManagerRequestCommands.Assignment command) { throw new UnsupportedOperationException(); }

    default void reassign(String displayId, ManagerRequestCommands.Reassignment command) { throw new UnsupportedOperationException(); }

    default void cancel(String displayId, ManagerRequestCommands.Cancellation command) { throw new UnsupportedOperationException(); }

    default void close(String displayId, String version) { throw new UnsupportedOperationException(); }

    default void returnCompleted(String displayId, ManagerRequestCommands.ReturnCompleted command) { throw new UnsupportedOperationException(); }

    default void reopen(String displayId, ManagerRequestCommands.Reopen command) { throw new UnsupportedOperationException(); }

    default void correct(String displayId, ManagerRequestCommands.Correction command) { throw new UnsupportedOperationException(); }

    default void saveTriage(String displayId, ManagerRequestCommands.TriageDecision command) { throw new UnsupportedOperationException(); }

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

    record RequestDetail(
            UUID id, String displayId, String title, String description, String location,
            UUID categoryId, String category, String urgency, String priority, String status,
            String requester, UUID technicianId, String technician, String resolutionSummary,
            long version, Instant createdAt, Instant updatedAt, List<Option> categories,
            List<Option> technicians, List<HistoryEntry> requesterUpdates,
            List<WorkLogEntry> workLogs, List<CompletionEntry> completions,
            List<AuditEntry> auditEvents) {
        public String createdTime() { return DISPLAY_TIME.format(createdAt); }
        public String updatedTime() { return DISPLAY_TIME.format(updatedAt); }
        public boolean canAssign() { return status.equals("OPEN"); }
        public boolean canReassign() { return status.equals("ASSIGNED") || status.equals("IN_PROGRESS"); }
        public boolean canCancel() { return status.equals("OPEN") || canReassign(); }
        public boolean canReview() { return status.equals("COMPLETED"); }
        public boolean canReopen() { return status.equals("CLOSED"); }
        public boolean canTriage() { return !status.equals("CANCELLED") && !status.equals("CLOSED"); }
    }

    record HistoryEntry(String author, String note, Instant createdAt) {
        public String createdTime() { return DISPLAY_TIME.format(createdAt); }
    }

    record WorkLogEntry(String author, String note, int minutes, Instant createdAt) {
        public String createdTime() { return DISPLAY_TIME.format(createdAt); }
    }

    record CompletionEntry(String technician, String resolutionSummary, Instant completedAt) {
        public String completedTime() { return DISPLAY_TIME.format(completedAt); }
    }

    record AuditEntry(String actor, String action, String details, Instant occurredAt) {
        public String occurredTime() { return DISPLAY_TIME.format(occurredAt); }
    }
}
