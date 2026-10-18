package sg.edu.nus.facilityflow.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.manager.ManagerAdminCommands;
import sg.edu.nus.facilityflow.manager.ManagerAdminViews;
import sg.edu.nus.facilityflow.manager.ManagerValidationException;

@Controller
@RequestMapping("/manager")
public final class ManagerAdminController {
    private final ManagerAdminViews admin;

    public ManagerAdminController(ManagerAdminViews admin) {
        this.admin = admin;
    }

    @GetMapping("/accounts")
    String accounts(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        return accountsPage(principal, Map.of(), null, null, model);
    }

    @PostMapping("/accounts")
    String createAccount(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @ModelAttribute ManagerAdminCommands.CreateAccount command,
            Model model, RedirectAttributes redirect) {
        return accountAction(principal, "create", command, "Account created.", model, redirect,
                () -> admin.createAccount(command));
    }

    @PostMapping("/accounts/{id}/state")
    String accountState(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.AccountState command,
            Model model, RedirectAttributes redirect) {
        return accountAction(principal, "state-" + id, command,
                Boolean.parseBoolean(command.active()) ? "Account reactivated." : "Account deactivated.",
                model, redirect, () -> admin.setAccountState(id, command));
    }

    @PostMapping("/accounts/{id}/role")
    String accountRole(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.AccountRole command,
            Model model, RedirectAttributes redirect) {
        return accountAction(principal, "role-" + id, command, "Account role updated.",
                model, redirect, () -> admin.changeAccountRole(id, command));
    }

    @PostMapping("/accounts/{id}/password")
    String passwordReset(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.PasswordReset command,
            Model model, RedirectAttributes redirect) {
        return accountAction(principal, "password-" + id, null, "Password reset; existing sessions were invalidated.",
                model, redirect, () -> admin.resetPassword(id, command));
    }

    @GetMapping("/categories")
    String categories(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        return categoriesPage(principal, Map.of(), null, null, model);
    }

    @PostMapping("/categories")
    String createCategory(@AuthenticationPrincipal CurrentAccountPrincipal principal, @RequestParam String name,
            Model model, RedirectAttributes redirect) {
        return categoryAction(principal, "create", name, "Category created.", model, redirect,
                () -> admin.createCategory(name));
    }

    @PostMapping("/categories/{id}/rename")
    String renameCategory(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.CatalogueName command,
            Model model, RedirectAttributes redirect) {
        return categoryAction(principal, "rename-" + id, command, "Category renamed.", model, redirect,
                () -> admin.renameCategory(id, command));
    }

    @PostMapping("/categories/{id}/remove")
    String removeCategory(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.CategoryRemoval command,
            Model model, RedirectAttributes redirect) {
        return categoryAction(principal, "remove-" + id, command,
                "Category removed and existing requests migrated.", model, redirect,
                () -> admin.removeCategory(id, command));
    }

    @GetMapping("/skills")
    String skills(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        return skillsPage(principal, Map.of(), null, null, model);
    }

    @PostMapping("/skills")
    String createSkill(@AuthenticationPrincipal CurrentAccountPrincipal principal, @RequestParam String name,
            Model model, RedirectAttributes redirect) {
        return skillAction(principal, "create", name, "Skill created.", model, redirect,
                () -> admin.createSkill(name));
    }

    @PostMapping("/skills/{id}/rename")
    String renameSkill(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.CatalogueName command,
            Model model, RedirectAttributes redirect) {
        return skillAction(principal, "rename-" + id, command, "Skill renamed.", model, redirect,
                () -> admin.renameSkill(id, command));
    }

    @PostMapping("/skills/{id}/deactivate")
    String deactivateSkill(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.SkillDeactivation command,
            Model model, RedirectAttributes redirect) {
        return skillAction(principal, "deactivate-" + id, command, "Skill deactivated.", model, redirect,
                () -> admin.deactivateSkill(id, command));
    }

    @PostMapping("/technicians/{id}/skills")
    String technicianSkills(@AuthenticationPrincipal CurrentAccountPrincipal principal, @PathVariable UUID id,
            @ModelAttribute ManagerAdminCommands.TechnicianSkills command,
            Model model, RedirectAttributes redirect) {
        return skillAction(principal, "technician-" + id, command, "Technician skills updated.",
                model, redirect, () -> admin.updateTechnicianSkills(id, command));
    }

    private String accountAction(CurrentAccountPrincipal principal, String action, Object form, String notice,
            Model model, RedirectAttributes redirect, Runnable operation) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", notice);
            return "redirect:/manager/accounts";
        } catch (ManagerValidationException exception) {
            return accountsPage(principal, exception.errors(), action, form, model);
        } catch (DataAccessException | TransactionException exception) {
            return accountsPage(principal, storageError(), action, form, model);
        }
    }

    private String categoryAction(CurrentAccountPrincipal principal, String action, Object form, String notice,
            Model model, RedirectAttributes redirect, Runnable operation) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", notice);
            return "redirect:/manager/categories";
        } catch (ManagerValidationException exception) {
            return categoriesPage(principal, exception.errors(), action, form, model);
        } catch (DataAccessException | TransactionException exception) {
            return categoriesPage(principal, storageError(), action, form, model);
        }
    }

    private String skillAction(CurrentAccountPrincipal principal, String action, Object form, String notice,
            Model model, RedirectAttributes redirect, Runnable operation) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", notice);
            return "redirect:/manager/skills";
        } catch (ManagerValidationException exception) {
            return skillsPage(principal, exception.errors(), action, form, model);
        } catch (DataAccessException | TransactionException exception) {
            return skillsPage(principal, storageError(), action, form, model);
        }
    }

    private String accountsPage(CurrentAccountPrincipal principal, Map<String, String> errors,
            String action, Object form, Model model) {
        identity(model, principal); model.addAttribute("page", admin.accounts());
        common(model, errors, action, form); return "manager/accounts";
    }

    private String categoriesPage(CurrentAccountPrincipal principal, Map<String, String> errors,
            String action, Object form, Model model) {
        identity(model, principal); model.addAttribute("page", admin.categories());
        common(model, errors, action, form); return "manager/categories";
    }

    private String skillsPage(CurrentAccountPrincipal principal, Map<String, String> errors,
            String action, Object form, Model model) {
        identity(model, principal); model.addAttribute("page", admin.skills());
        common(model, errors, action, form); return "manager/skills";
    }

    private static void common(Model model, Map<String, String> errors, String action, Object form) {
        model.addAttribute("errors", errors); model.addAttribute("failedAction", action); model.addAttribute("form", form);
    }

    private static Map<String, String> storageError() {
        return Map.of("save", "The change could not be saved. Your non-password input has been retained; refresh if the item changed, then try again.");
    }

    private static void identity(Model model, CurrentAccountPrincipal principal) {
        model.addAttribute("username", principal.username()); model.addAttribute("displayName", principal.displayName());
    }
}
