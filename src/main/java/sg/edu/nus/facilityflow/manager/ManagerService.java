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
    public RequestDetail request(String displayId) {
        return authorized(false, actor -> repository.request(displayId)
                .orElseThrow(ManagerRequestNotFoundException::new));
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

    @Override
    public void assign(String displayId, ManagerRequestCommands.Assignment command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), "OPEN");
            UUID technician = UUID.fromString(command.technicianId());
            requireActiveTechnician(technician);
            changed(repository.assign(request, technician, command.managerPriority()));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_ASSIGNED", java.util.Map.of(
                    "technicianId", technician, "managerPriority", command.managerPriority()));
            return null;
        });
    }

    @Override
    public void reassign(String displayId, ManagerRequestCommands.Reassignment command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), "ASSIGNED", "IN_PROGRESS");
            UUID technician = UUID.fromString(command.technicianId());
            requireActiveTechnician(technician);
            if (technician.equals(request.assignedTechnicianId())) {
                throw new ManagerValidationException(java.util.Map.of(
                        "technicianId", "Choose a different active Technician."));
            }
            changed(repository.reassign(request, technician));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_REASSIGNED", java.util.Map.of(
                    "technicianId", technician, "reason", command.reason().strip()));
            return null;
        });
    }

    @Override
    public void cancel(String displayId, ManagerRequestCommands.Cancellation command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), "OPEN", "ASSIGNED", "IN_PROGRESS");
            changed(repository.cancel(request));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_CANCELLED",
                    java.util.Map.of("reason", command.reason().strip()));
            return null;
        });
    }

    @Override
    public void close(String displayId, String version) {
        authorized(true, actor -> {
            var request = locked(displayId, version, "COMPLETED");
            changed(repository.close(request));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_CLOSED", java.util.Map.of());
            return null;
        });
    }

    @Override
    public void returnCompleted(String displayId, ManagerRequestCommands.ReturnCompleted command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), "COMPLETED");
            UUID technician = UUID.fromString(command.technicianId());
            requireActiveTechnician(technician);
            changed(repository.returnCompleted(request, technician));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_RETURNED", java.util.Map.of(
                    "technicianId", technician, "reason", command.reason().strip()));
            return null;
        });
    }

    @Override
    public void reopen(String displayId, ManagerRequestCommands.Reopen command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), "CLOSED");
            UUID technician = UUID.fromString(command.technicianId());
            requireActiveTechnician(technician);
            changed(repository.reopen(request, technician));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_REOPENED", java.util.Map.of(
                    "technicianId", technician, "reason", command.reason().strip()));
            return null;
        });
    }

    @Override
    public void correct(String displayId, ManagerRequestCommands.Correction command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), STATUSES.toArray(String[]::new));
            if ((request.status().equals("ASSIGNED") || request.status().equals("IN_PROGRESS")
                    || request.status().equals("COMPLETED") || request.status().equals("CLOSED"))
                    && (command.managerPriority() == null || command.managerPriority().isBlank())) {
                throw new ManagerValidationException(java.util.Map.of(
                        "managerPriority", "A Manager priority is required in this request state."));
            }
            UUID category = UUID.fromString(command.categoryId());
            if (!repository.activeCategory(category)) {
                throw new ManagerValidationException(java.util.Map.of(
                        "categoryId", "Choose an active category."));
            }
            changed(repository.correct(request, category, command));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_CORRECTED", java.util.Map.of(
                    "reason", command.reason().strip()));
            return null;
        });
    }

    @Override
    public void saveTriage(String displayId, ManagerRequestCommands.TriageDecision command) {
        authorized(true, actor -> {
            validate(command.errors());
            var request = locked(displayId, command.version(), "OPEN");
            String currentFingerprint = repository.triageSnapshotFingerprint();
            if (!java.security.MessageDigest.isEqual(
                    currentFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    command.snapshotFingerprint().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new ManagerValidationException(java.util.Map.of("version",
                        "The request, catalogue or Technician availability changed. Generate a fresh suggestion."));
            }
            UUID category = UUID.fromString(command.categoryId());
            UUID technician = UUID.fromString(command.technicianId());
            java.util.Set<String> requiredSkills = java.util.Set.copyOf(command.acceptedRequiredSkills());
            java.util.Set<String> optionalSkills = java.util.Set.copyOf(command.acceptedOptionalSkills());
            var acceptedSkills = new java.util.HashSet<>(requiredSkills);
            acceptedSkills.addAll(optionalSkills);
            if (!repository.activeCategory(category)) {
                throw new ManagerValidationException(java.util.Map.of("categoryId", "Choose an active category."));
            }
            requireActiveTechnician(technician);
            if (!repository.activeSkills(java.util.Set.copyOf(acceptedSkills))) {
                throw new ManagerValidationException(java.util.Map.of("acceptedRequiredSkills",
                        "The selected skills changed. Generate a fresh suggestion."));
            }
            if (!repository.technicianHasSkills(technician, requiredSkills)) {
                throw new ManagerValidationException(java.util.Map.of("technicianId",
                        "Choose an active Technician who has every accepted required skill."));
            }
            changed(repository.assignTriage(request, category, technician, command.managerPriority()));
            repository.auditRequestAction(actor.id(), request.id(), "REQUEST_ASSIGNED", java.util.Map.of(
                    "categoryId", category, "technicianId", technician,
                    "managerPriority", command.managerPriority(), "triageReviewed", true,
                    "acceptedRequiredSkills", requiredSkills, "acceptedOptionalSkills", optionalSkills));
            return null;
        });
    }

    private ManagerRepository.LockedRequest locked(String displayId, String version, String... states) {
        long expected = ManagerRequestCommands.version(version);
        var request = repository.lockRequest(displayId).orElseThrow(ManagerRequestNotFoundException::new);
        if (request.version() != expected) {
            throw new ManagerValidationException(java.util.Map.of("version",
                    "This request changed after the page was loaded. Refresh and try again."));
        }
        if (java.util.Arrays.stream(states).noneMatch(request.status()::equals)) {
            throw new ManagerValidationException(java.util.Map.of("state",
                    "This action is not available in the request's current state. Refresh the page."));
        }
        return request;
    }

    private void requireActiveTechnician(UUID technician) {
        if (!repository.activeTechnician(technician)) {
            throw new ManagerValidationException(java.util.Map.of(
                    "technicianId", "Choose an active Technician."));
        }
    }

    private static void validate(java.util.Map<String, String> errors) {
        if (!errors.isEmpty()) throw new ManagerValidationException(errors);
    }

    private static void changed(int rows) {
        if (rows != 1) {
            throw new ManagerValidationException(java.util.Map.of("version",
                    "This request changed after the page was loaded. Refresh and try again."));
        }
    }

    private <T> T authorized(boolean writing, Function<CurrentAccount, T> operation) {
        CurrentAccount actor = accounts.requireCurrentAccount();
        if (actor.role() != AccountRole.FACILITIES_MANAGER) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return transactions.execute(transaction -> {
            repository.requireManager(actor, writing);
            if (writing) repository.lockManagerMutation();
            return operation.apply(actor);
        });
    }
}
