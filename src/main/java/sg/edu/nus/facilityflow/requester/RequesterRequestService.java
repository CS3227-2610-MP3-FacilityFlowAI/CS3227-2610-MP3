package sg.edu.nus.facilityflow.requester;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.BiConsumer;
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
    public Dashboard dashboard() {
        return authorized(false, actor -> {
            var counts = requests.statusCounts(actor.id());
            return new Dashboard(STATUSES.stream().map(status -> new StatusCount(status, counts.getOrDefault(status, 0L))).toList(),
                    requests.list(actor.id(), RequestFilters.empty(), 5));
        });
    }

    @Override
    public ListPage list(RequestFilters filters) {
        return authorized(false, actor -> {
            var errors = filters.errors();
            var categories = requests.filterCategories();
            if (!filters.categoryId().isEmpty() && !errors.containsKey("categoryId")
                    && categories.stream().noneMatch(category -> category.id().equals(UUID.fromString(filters.categoryId())))) {
                errors.put("categoryId", "Choose a listed category.");
            }
            boolean hasRequests = !requests.statusCounts(actor.id()).isEmpty();
            return new ListPage(errors.isEmpty() ? requests.list(actor.id(), filters, 0) : List.of(),
                    categories, hasRequests, Map.copyOf(errors));
        });
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

    /** REQ-RQ-006–011: state/version checks and audit stay within the locked transaction. */
    @Override
    public String edit(String displayId, RequestDraft draft, String version) {
        return change(displayId, version, true, (actor, request) -> {
            var errors = draft.errors();
            if (!errors.isEmpty()) throw new RequestValidationException(errors);
            if (!requests.activeCategory(UUID.fromString(draft.categoryId()))) {
                throw new RequestValidationException(Map.of("categoryId", "This category is no longer active. Choose another category."));
            }
            requests.edit(request, draft);
            requests.auditMutation(actor.id(), request.id(), "REQUEST_EDITED", null);
        });
    }

    @Override
    public String cancel(String displayId, String reason, boolean confirmed, String version) {
        return change(displayId, version, true, (actor, request) -> {
            String validReason = RequestDraft.validatedText("reason", "Cancellation reason", reason, 5, 500);
            if (!confirmed) throw new RequestValidationException(Map.of("confirmed", "Confirm that you want to cancel this request."));
            requests.cancel(request);
            requests.auditMutation(actor.id(), request.id(), "REQUEST_CANCELLED", validReason);
        });
    }

    @Override
    public String addUpdate(String displayId, String note, String version) {
        return change(displayId, version, false, (actor, request) -> {
            String validNote = RequestDraft.validatedText("note", "Update", note, 1, 1000);
            requests.addUpdate(actor.id(), request, validNote);
            requests.auditMutation(actor.id(), request.id(), "REQUESTER_UPDATE_ADDED", null);
        });
    }

    private String change(String displayId, String version, boolean openOnly,
            BiConsumer<CurrentAccount, RequesterRequestRepository.Locked> operation) {
        return authorized(true, actor -> {
            var request = requests.lockOwned(actor.id(), displayId).orElseThrow(RequestUnavailableException::new);
            long expected;
            try {
                expected = Long.parseLong(version);
                if (expected < 0) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                throw new RequestValidationException(Map.of("version", "Reload the current request before submitting."));
            }
            if (request.version() != expected || (openOnly && !request.status().equals("OPEN"))
                    || request.status().equals("CLOSED") || request.status().equals("CANCELLED")) {
                throw new StaleRequestException();
            }
            operation.accept(actor, request);
            return displayId;
        });
    }

    private <T> T authorized(boolean writing, Function<CurrentAccount, T> operation) {
        var actor = accounts.requireCurrentAccount();
        return transactions.execute(transaction -> {
            requests.requireRequester(actor, writing);
            return operation.apply(actor);
        });
    }
}
