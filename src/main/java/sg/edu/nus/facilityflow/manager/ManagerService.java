package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;

@Service
public class ManagerService implements ManagerViews {
    private static final List<String> STATUSES = List.of(
            "OPEN", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED");
    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final CurrentAccountProvider accounts;
    private final ManagerRepository repository;
    private final TransactionTemplate transactions;

    public ManagerService(CurrentAccountProvider accounts, ManagerRepository repository,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Overview overview() {
        return authorized(false, actor -> {
            var statuses = repository.statusCounts();
            var priorities = repository.priorityCounts();
            return new Overview(
                    STATUSES.stream().map(value -> new Count(value, statuses.getOrDefault(value, 0L))).toList(),
                    PRIORITIES.stream().map(value -> new Count(value, priorities.getOrDefault(value, 0L))).toList(),
                    repository.technicianWorkloads());
        });
    }

    @Override
    public RequestPage requests(ManagerRequestFilters filters) {
        return authorized(false, actor -> {
            var errors = filters.errors();
            return new RequestPage(errors.isEmpty() ? repository.requests(filters) : List.of(),
                    repository.categories(false), repository.technicians(false),
                    !repository.statusCounts().isEmpty(), errors);
        });
    }

    @Override
    public AuditPage audit(ManagerAuditFilters filters) {
        return authorized(false, actor -> {
            var errors = filters.errors();
            return new AuditPage(errors.isEmpty() ? repository.audit(filters) : List.of(),
                    repository.hasAuditEvents(), errors);
        });
    }

    @Override
    public CreationOptions creationOptions() {
        return authorized(false, actor -> new CreationOptions(
                repository.activeRequesters(), repository.categories(true)));
    }

    @Override
    public String createOnBehalf(ManagerRequestDraft draft) {
        return authorized(true, actor -> {
            var errors = draft.errors();
            if (!errors.isEmpty()) {
                throw new ManagerValidationException(errors);
            }
            UUID requester = UUID.fromString(draft.requesterId());
            UUID category = UUID.fromString(draft.categoryId());
            if (!repository.activeRequester(requester)) {
                throw new ManagerValidationException(java.util.Map.of(
                        "requesterId", "Choose an active Requester."));
            }
            if (!repository.activeCategory(category)) {
                throw new ManagerValidationException(java.util.Map.of(
                        "categoryId", "Choose an active category."));
            }
            var created = repository.insert(actor.id(), requester, category, draft);
            repository.auditCreation(actor.id(), created.id(), requester);
            return created.displayId();
        });
    }

    private <T> T authorized(boolean writing, Function<CurrentAccount, T> operation) {
        CurrentAccount actor = accounts.requireCurrentAccount();
        if (actor.role() != AccountRole.FACILITIES_MANAGER) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return transactions.execute(transaction -> {
            repository.requireManager(actor, writing);
            return operation.apply(actor);
        });
    }
}
