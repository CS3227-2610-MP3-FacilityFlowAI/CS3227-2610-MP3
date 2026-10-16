package sg.edu.nus.facilityflow.requester;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;
import sg.edu.nus.facilityflow.requestcore.RequestValidationException;

@Service
public class RequesterRequestService implements RequesterRequests {
    private final CurrentAccountProvider accounts;
    private final RequesterRequestRepository requests;
    private final TransactionTemplate transactions;

    public RequesterRequestService(CurrentAccountProvider accounts, RequesterRequestRepository requests,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.requests = requests;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<Category> categories() {
        return authorized(false, actor -> requests.categories());
    }

    @Override
    public List<Summary> list() {
        return authorized(false, actor -> requests.list(actor.id()));
    }

    @Override
    public Optional<Detail> detail(String displayId) {
        return authorized(false, actor -> displayId.matches("FF-[0-9]{6}")
                ? requests.detail(actor.id(), displayId) : Optional.empty());
    }

    @Override
    public String create(RequestDraft draft, String submissionId) {
        return authorized(true, actor -> {
            var errors = draft.errors();
            if (!RequestDraft.validUuid(submissionId)) errors.put("submissionId", "Reload the form before submitting.");
            if (!errors.isEmpty()) throw new RequestValidationException(errors);
            var command = UUID.fromString(submissionId);
            if (requests.submitted(actor.id(), command)) throw new DuplicateSubmissionException();
            if (!requests.activeCategory(UUID.fromString(draft.categoryId()))) {
                throw new RequestValidationException(Map.of("categoryId", "This category is no longer active. Choose another category."));
            }
            var created = requests.insert(actor.id(), draft);
            requests.auditCreation(actor.id(), created.id());
            requests.recordSubmission(actor.id(), command, created.id());
            return created.displayId();
        });
    }

    private <T> T authorized(boolean creating, Function<CurrentAccount, T> operation) {
        var actor = accounts.requireCurrentAccount();
        return transactions.execute(transaction -> {
            requests.requireRequester(actor, creating);
            return operation.apply(actor);
        });
    }
}
