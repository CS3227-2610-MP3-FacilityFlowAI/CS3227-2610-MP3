package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ManagerAdminViews {
    AccountsPage accounts();
    void createAccount(ManagerAdminCommands.CreateAccount command);
    void setAccountState(UUID accountId, ManagerAdminCommands.AccountState command);
    void changeAccountRole(UUID accountId, ManagerAdminCommands.AccountRole command);
    void resetPassword(UUID accountId, ManagerAdminCommands.PasswordReset command);

    CategoriesPage categories();
    void createCategory(String name);
    void renameCategory(UUID categoryId, ManagerAdminCommands.CatalogueName command);
    void removeCategory(UUID categoryId, ManagerAdminCommands.CategoryRemoval command);

    SkillsPage skills();
    void createSkill(String name);
    void renameSkill(UUID skillId, ManagerAdminCommands.CatalogueName command);
    void deactivateSkill(UUID skillId, ManagerAdminCommands.SkillDeactivation command);
    void updateTechnicianSkills(UUID technicianId, ManagerAdminCommands.TechnicianSkills command);

    record AccountRow(UUID id, String username, String displayName, String role,
                      boolean active, long version, long activeAssignments, boolean self) { }
    record AccountsPage(List<AccountRow> accounts) { }

    record CategoryRow(UUID id, String name, boolean active, long version, long requestCount) { }
    record CategoriesPage(List<CategoryRow> categories, List<ManagerViews.Option> activeCategories) { }

    record SkillRow(UUID id, String name, boolean active, long version) { }
    record TechnicianSkillsRow(UUID id, String label, long version, Set<UUID> activeSkillIds) { }
    record SkillsPage(List<SkillRow> skills, List<TechnicianSkillsRow> technicians) { }
}
