package sg.edu.nus.facilityflow.requester;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

/** Called inside the service transaction using the same transaction manager/data source. */
public interface RequesterRequestRepository {
    void requireRequester(CurrentAccount account, boolean writing);
    List<RequesterRequests.Category> categories();
    List<RequesterRequests.Summary> list(UUID owner);
    Optional<RequesterRequests.Detail> detail(UUID owner, String displayId);
    boolean submitted(UUID owner, UUID command);
    boolean activeCategory(UUID category);
    Created insert(UUID owner, RequestDraft draft);
    void auditCreation(UUID owner, UUID request);
    void recordSubmission(UUID owner, UUID command, UUID request);
    Optional<Locked> lockOwned(UUID owner, String displayId);
    void edit(Locked request, RequestDraft draft);
    void cancel(Locked request);
    void addUpdate(UUID owner, Locked request, String note);
    void auditMutation(UUID owner, UUID request, String action, String reason);

    record Created(UUID id, String displayId) { }
    record Locked(UUID id, String status, long version) { }
}
