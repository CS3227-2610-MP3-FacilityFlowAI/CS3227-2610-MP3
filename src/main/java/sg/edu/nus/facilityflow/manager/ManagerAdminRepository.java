package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface ManagerAdminRepository {
    void requireManager(UUID actorId, String username, boolean writing);
    void lockManagerMutation();
    List<ManagerAdminViews.AccountRow> accounts(UUID actorId);
    Optional<LockedAccount> lockAccount(UUID id);
    void lockManagerAccounts();
    long activeManagerCount();
    boolean usernameExists(String username);
    UUID insertAccount(ManagerAdminCommands.CreateAccount command, String passwordHash);
    int updateAccountState(LockedAccount account, boolean active);
    int updateAccountRole(LockedAccount account, String role);
    int updatePassword(LockedAccount account, String passwordHash);
    void audit(UUID actorId, String action, String targetType, UUID targetId, String detailsJson);

    List<ManagerAdminViews.CategoryRow> categories();
    List<ManagerViews.Option> activeCategories();
    boolean categoryNameExists(String name, UUID except);
    UUID insertCategory(String name);
    Optional<LockedCatalogue> lockCategory(UUID id);
    int renameCategory(LockedCatalogue category, String name);
    boolean activeCategory(UUID id);
    int migrateCategory(LockedCatalogue category, UUID replacementId, UUID actorId);

    List<ManagerAdminViews.SkillRow> skills();
    List<ManagerAdminViews.TechnicianSkillsRow> technicianSkills();
    boolean skillNameExists(String name, UUID except);
    UUID insertSkill(String name);
    Optional<LockedCatalogue> lockSkill(UUID id);
    int renameSkill(LockedCatalogue skill, String name);
    int deactivateSkill(LockedCatalogue skill);
    boolean activeSkills(Set<UUID> ids);
    Optional<LockedAccount> lockActiveTechnician(UUID id);
    int updateTechnicianSkills(LockedAccount technician, Set<UUID> activeSkillIds);

    record LockedAccount(UUID id, String username, String role, boolean active,
                         long version, long activeAssignments) { }
    record LockedCatalogue(UUID id, String name, boolean active, long version) { }
}
