package sg.edu.nus.facilityflow.requester;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;

/** Called inside the service transaction using the same transaction manager/data source. */
public interface RequesterRequestRepository {
    void requireRequester(CurrentAccount account, boolean creating);
    List<RequesterRequests.Category> categories();
    List<RequesterRequests.Summary> list(UUID owner);
    Optional<RequesterRequests.Detail> detail(UUID owner, String displayId);
    boolean submitted(UUID owner, UUID command);
    boolean activeCategory(UUID category);
    Created insert(UUID owner, RequestDraft draft);
    void auditCreation(UUID owner, UUID request);
    void recordSubmission(UUID owner, UUID command, UUID request);

    record Created(UUID id, String displayId) { }
}
