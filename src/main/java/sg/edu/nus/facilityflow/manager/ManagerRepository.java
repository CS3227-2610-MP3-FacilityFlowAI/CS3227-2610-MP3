package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import sg.edu.nus.facilityflow.identity.CurrentAccount;

/** Called only inside the Manager service transaction. */
public interface ManagerRepository {
    void requireManager(CurrentAccount actor, boolean writing);

    void lockManagerMutation();

    Map<String, Long> statusCounts();

    Map<String, Long> priorityCounts();

    List<ManagerViews.TechnicianWorkload> technicianWorkloads();

    List<ManagerViews.Option> activeRequesters();

    List<ManagerViews.Option> categories(boolean activeOnly);

    List<ManagerViews.Option> technicians(boolean activeOnly);

    List<ManagerViews.RequestSummary> requests(ManagerRequestFilters filters);

    Optional<ManagerViews.RequestDetail> request(String displayId);

    Optional<LockedRequest> lockRequest(String displayId);

    List<ManagerViews.AuditRow> audit(ManagerAuditFilters filters);

    boolean hasAuditEvents();

    boolean activeRequester(UUID requesterId);

    boolean activeCategory(UUID categoryId);

    boolean activeTechnician(UUID technicianId);

    String triageSnapshotFingerprint();

    boolean activeSkills(Set<String> skillNames);

    boolean technicianHasSkills(UUID technicianId, Set<String> requiredSkills);

    Created insert(UUID actorId, UUID requesterId, UUID categoryId, ManagerRequestDraft draft);

    void auditCreation(UUID actorId, UUID requestId, UUID requesterId);

    int assign(LockedRequest request, UUID technicianId, String priority);

    int assignTriage(LockedRequest request, UUID categoryId, UUID technicianId, String priority);

    int reassign(LockedRequest request, UUID technicianId);

    int cancel(LockedRequest request);

    int close(LockedRequest request);

    int returnCompleted(LockedRequest request, UUID technicianId);

    int reopen(LockedRequest request, UUID technicianId);

    int correct(LockedRequest request, UUID categoryId, ManagerRequestCommands.Correction command);

    void auditRequestAction(UUID actorId, UUID requestId, String action, Map<String, ?> details);

    record Created(UUID id, String displayId) { }

    record LockedRequest(UUID id, String displayId, String status, long version,
                         UUID assignedTechnicianId) { }
}
