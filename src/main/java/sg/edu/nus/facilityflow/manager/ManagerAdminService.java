package sg.edu.nus.facilityflow.manager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public final class ManagerAdminService implements ManagerAdminViews {
    private static final tools.jackson.databind.ObjectMapper JSON = JsonMapper.builder().build();
    private final CurrentAccountProvider accounts;
    private final ManagerAdminRepository repository;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;

    public ManagerAdminService(CurrentAccountProvider accounts, ManagerAdminRepository repository,
            PasswordEncoder passwords, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.repository = repository;
        this.passwords = passwords;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public AccountsPage accounts() {
        return authorized(false, actor -> new AccountsPage(repository.accounts(actor.id())));
    }

    @Override
    public void createAccount(ManagerAdminCommands.CreateAccount command) {
        authorized(true, actor -> {
            validate(command.errors());
            if (repository.usernameExists(command.username().strip())) {
                throw validation("username", "That username is already in use.");
            }
            UUID id = repository.insertAccount(command, passwords.encode(command.password()));
            repository.audit(actor.id(), "ACCOUNT_CREATED", "ACCOUNT", id,
                    json(Map.of("role", command.role())));
            return null;
        });
    }

    @Override
    public void setAccountState(UUID accountId, ManagerAdminCommands.AccountState command) {
        authorized(true, actor -> {
            validate(command.errors());
            var target = account(accountId, command.version());
            boolean active = Boolean.parseBoolean(command.active());
            if (actor.id().equals(target.id())) {
                throw validation("account", "Use your profile settings for your own account.");
            }
            if (!active && target.role().equals("TECHNICIAN") && target.activeAssignments() > 0) {
                throw validation("account", "Reassign or cancel this Technician's active requests first.");
            }
            if (!active && target.active() && target.role().equals("FACILITIES_MANAGER")) {
                requireAnotherManager();
            }
            changed(repository.updateAccountState(target, active));
            repository.audit(actor.id(), active ? "ACCOUNT_REACTIVATED" : "ACCOUNT_DEACTIVATED",
                    "ACCOUNT", target.id(), "{}");
            return null;
        });
    }

    @Override
    public void changeAccountRole(UUID accountId, ManagerAdminCommands.AccountRole command) {
        authorized(true, actor -> {
            validate(command.errors());
            var target = account(accountId, command.version());
            if (actor.id().equals(target.id())) {
                throw validation("account", "You cannot change your own role.");
            }
            if (target.role().equals("TECHNICIAN") && !command.role().equals("TECHNICIAN")
                    && target.activeAssignments() > 0) {
                throw validation("account", "Reassign or cancel this Technician's active requests first.");
            }
            if (target.active() && target.role().equals("FACILITIES_MANAGER")
                    && !command.role().equals("FACILITIES_MANAGER")) {
                requireAnotherManager();
            }
            changed(repository.updateAccountRole(target, command.role()));
            repository.audit(actor.id(), "ACCOUNT_ROLE_CHANGED", "ACCOUNT", target.id(),
                    json(Map.of("fromRole", target.role(), "toRole", command.role())));
            return null;
        });
    }

    @Override
    public void resetPassword(UUID accountId, ManagerAdminCommands.PasswordReset command) {
        authorized(true, actor -> {
            validate(command.errors());
            var target = account(accountId, command.version());
            if (actor.id().equals(target.id())) {
                throw validation("account", "Use Change password for your own account.");
            }
            changed(repository.updatePassword(target, passwords.encode(command.password())));
            repository.audit(actor.id(), "ACCOUNT_PASSWORD_RESET", "ACCOUNT", target.id(), "{}");
            return null;
        });
    }

    @Override
    public CategoriesPage categories() {
        return authorized(false, actor -> new CategoriesPage(
                repository.categories(), repository.activeCategories()));
    }

    @Override
    public void createCategory(String name) {
        authorized(true, actor -> {
            String normalized = catalogueName(name);
            if (repository.categoryNameExists(normalized, null)) {
                throw validation("name", "That category name is already in use.");
            }
            UUID id = repository.insertCategory(normalized);
            repository.audit(actor.id(), "CATEGORY_CREATED", "CATEGORY", id, "{}");
            return null;
        });
    }

    @Override
    public void renameCategory(UUID categoryId, ManagerAdminCommands.CatalogueName command) {
        authorized(true, actor -> {
            validate(command.errors());
            var category = category(categoryId, command.version());
            String name = command.name().strip();
            if (!category.active()) throw validation("category", "Inactive categories cannot be renamed.");
            if (repository.categoryNameExists(name, category.id())) {
                throw validation("name", "That category name is already in use.");
            }
            changed(repository.renameCategory(category, name));
            repository.audit(actor.id(), "CATEGORY_RENAMED", "CATEGORY", category.id(),
                    json(Map.of("fromName", category.name(), "toName", name)));
            return null;
        });
    }

    @Override
    public void removeCategory(UUID categoryId, ManagerAdminCommands.CategoryRemoval command) {
        authorized(true, actor -> {
            validate(command.errors());
            var category = category(categoryId, command.version());
            UUID replacement = UUID.fromString(command.replacementId());
            if (!category.active()) throw validation("category", "This category is already inactive.");
            if (category.id().equals(replacement) || !repository.activeCategory(replacement)) {
                throw validation("replacementId", "Choose a different active replacement category.");
            }
            changed(repository.migrateCategory(category, replacement, actor.id()));
            repository.audit(actor.id(), "CATEGORY_REMOVED", "CATEGORY", category.id(),
                    json(Map.of("replacementCategoryId", replacement)));
            return null;
        });
    }

    @Override
    public SkillsPage skills() {
        return authorized(false, actor -> new SkillsPage(repository.skills(), repository.technicianSkills()));
    }

    @Override
    public void createSkill(String name) {
        authorized(true, actor -> {
            String normalized = catalogueName(name);
            if (repository.skillNameExists(normalized, null)) {
                throw validation("name", "That skill name is already in use.");
            }
            UUID id = repository.insertSkill(normalized);
            repository.audit(actor.id(), "SKILL_CREATED", "SKILL", id, "{}");
            return null;
        });
    }

    @Override
    public void renameSkill(UUID skillId, ManagerAdminCommands.CatalogueName command) {
        authorized(true, actor -> {
            validate(command.errors());
            var skill = skill(skillId, command.version());
            String name = command.name().strip();
            if (!skill.active()) throw validation("skill", "Inactive skills cannot be renamed.");
            if (repository.skillNameExists(name, skill.id())) {
                throw validation("name", "That skill name is already in use.");
            }
            changed(repository.renameSkill(skill, name));
            repository.audit(actor.id(), "SKILL_RENAMED", "SKILL", skill.id(),
                    json(Map.of("fromName", skill.name(), "toName", name)));
            return null;
        });
    }

    @Override
    public void deactivateSkill(UUID skillId, ManagerAdminCommands.SkillDeactivation command) {
        authorized(true, actor -> {
            validate(command.errors());
            var skill = skill(skillId, command.version());
            if (!skill.active()) throw validation("skill", "This skill is already inactive.");
            changed(repository.deactivateSkill(skill));
            repository.audit(actor.id(), "SKILL_DEACTIVATED", "SKILL", skill.id(),
                    json(Map.of("name", skill.name())));
            return null;
        });
    }

    @Override
    public void updateTechnicianSkills(UUID technicianId, ManagerAdminCommands.TechnicianSkills command) {
        authorized(true, actor -> {
            validate(command.errors());
            var technician = repository.lockActiveTechnician(technicianId)
                    .orElseThrow(() -> validation("technician", "Choose an active Technician."));
            requireVersion(technician.version(), command.version());
            Set<UUID> skillIds = new LinkedHashSet<>();
            command.skillIds().forEach(value -> skillIds.add(UUID.fromString(value)));
            if (!repository.activeSkills(skillIds)) throw validation("skillIds", "Choose only active skills.");
            changed(repository.updateTechnicianSkills(technician, skillIds));
            repository.audit(actor.id(), "TECHNICIAN_SKILLS_UPDATED", "ACCOUNT", technician.id(),
                    json(Map.of("activeSkillIds", skillIds)));
            return null;
        });
    }

    private ManagerAdminRepository.LockedAccount account(UUID id, String version) {
        var account = repository.lockAccount(id).orElseThrow(() -> validation("account", "Account not found."));
        requireVersion(account.version(), version);
        return account;
    }

    private ManagerAdminRepository.LockedCatalogue category(UUID id, String version) {
        var item = repository.lockCategory(id).orElseThrow(() -> validation("category", "Category not found."));
        requireVersion(item.version(), version);
        return item;
    }

    private ManagerAdminRepository.LockedCatalogue skill(UUID id, String version) {
        var item = repository.lockSkill(id).orElseThrow(() -> validation("skill", "Skill not found."));
        requireVersion(item.version(), version);
        return item;
    }

    private void requireAnotherManager() {
        repository.lockManagerAccounts();
        if (repository.activeManagerCount() <= 1) {
            throw validation("account", "At least one active Facilities Manager must remain.");
        }
    }

    private static String catalogueName(String raw) {
        var command = new ManagerAdminCommands.CatalogueName("0", raw);
        validate(command.errors());
        return raw.strip();
    }

    private static void requireVersion(long actual, String rawExpected) {
        if (actual != ManagerAdminCommands.parsedVersion(rawExpected)) {
            throw validation("version", "This item changed after the page loaded. Refresh and try again.");
        }
    }

    private static void changed(int count) {
        if (count != 1) throw validation("version", "This item changed after the page loaded. Refresh and try again.");
    }

    private static void validate(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new ManagerValidationException(errors);
    }

    private static ManagerValidationException validation(String field, String message) {
        return new ManagerValidationException(Map.of(field, message));
    }

    private static String json(Object details) {
        try { return JSON.writeValueAsString(details); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Audit details could not be encoded", exception); }
    }

    private <T> T authorized(boolean writing, Function<CurrentAccount, T> operation) {
        CurrentAccount actor = accounts.requireCurrentAccount();
        if (actor.role() != AccountRole.FACILITIES_MANAGER) {
            throw new AccessDeniedException("The requested operation is not available");
        }
        return transactions.execute(transaction -> {
            repository.requireManager(actor.id(), actor.username(), writing);
            if (writing) repository.lockManagerMutation();
            return operation.apply(actor);
        });
    }
}
