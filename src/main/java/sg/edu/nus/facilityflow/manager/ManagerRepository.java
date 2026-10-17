package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import sg.edu.nus.facilityflow.identity.CurrentAccount;

/** Called only inside the Manager service transaction. */
public interface ManagerRepository {
    void requireManager(CurrentAccount actor, boolean writing);

    Map<String, Long> statusCounts();

    Map<String, Long> priorityCounts();

    List<ManagerViews.TechnicianWorkload> technicianWorkloads();

    List<ManagerViews.Option> activeRequesters();

    List<ManagerViews.Option> categories(boolean activeOnly);

    List<ManagerViews.Option> technicians(boolean activeOnly);

    List<ManagerViews.RequestSummary> requests(ManagerRequestFilters filters);

    List<ManagerViews.AuditRow> audit(ManagerAuditFilters filters);

    boolean hasAuditEvents();

    boolean activeRequester(UUID requesterId);

    boolean activeCategory(UUID categoryId);

    Created insert(UUID actorId, UUID requesterId, UUID categoryId, ManagerRequestDraft draft);

    void auditCreation(UUID actorId, UUID requestId, UUID requesterId);

    record Created(UUID id, String displayId) { }
}
