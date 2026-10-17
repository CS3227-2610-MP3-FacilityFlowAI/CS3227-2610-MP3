package sg.edu.nus.facilityflow.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.TransactionException;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.manager.ManagerAuditFilters;
import sg.edu.nus.facilityflow.manager.ManagerRequestDraft;
import sg.edu.nus.facilityflow.manager.ManagerRequestFilters;
import sg.edu.nus.facilityflow.manager.ManagerValidationException;
import sg.edu.nus.facilityflow.manager.ManagerViews;

@Controller
@RequestMapping("/manager")
public class ManagerController {
    private final ManagerViews manager;

    public ManagerController(ManagerViews manager) {
        this.manager = manager;
    }

    @GetMapping("/overview")
    String overview(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        identity(model, principal);
        model.addAttribute("overview", manager.overview());
        return "manager/overview";
    }

    @GetMapping("/requests")
    String requests(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @ModelAttribute ManagerRequestFilters filters, Model model) {
        identity(model, principal);
        model.addAttribute("filters", filters);
        model.addAttribute("page", manager.requests(filters));
        return "manager/requests";
    }

    @GetMapping("/audit")
    String audit(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @ModelAttribute ManagerAuditFilters filters, Model model) {
        identity(model, principal);
        model.addAttribute("filters", filters);
        model.addAttribute("page", manager.audit(filters));
        return "manager/audit";
    }

    @GetMapping("/requests/new")
    String newRequest(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        return creationPage(principal, ManagerRequestDraft.empty(), java.util.Map.of(), model);
    }

    @PostMapping("/requests")
    String create(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @ModelAttribute ManagerRequestDraft draft, Model model, RedirectAttributes redirect) {
        try {
            String displayId = manager.createOnBehalf(draft);
            redirect.addFlashAttribute("notice", "Request " + displayId + " was recorded.");
            return "redirect:/manager/requests";
        } catch (ManagerValidationException exception) {
            return creationPage(principal, draft, exception.errors(), model);
        } catch (DataAccessException | TransactionException exception) {
            return creationPage(principal, draft, java.util.Map.of(
                    "save", "The request could not be saved. Your input has been retained; try again."), model);
        }
    }

    private String creationPage(CurrentAccountPrincipal principal, ManagerRequestDraft draft,
            java.util.Map<String, String> errors, Model model) {
        identity(model, principal);
        model.addAttribute("draft", draft);
        var visibleErrors = new java.util.LinkedHashMap<>(errors);
        ManagerViews.CreationOptions options;
        try {
            options = manager.creationOptions();
        } catch (DataAccessException | TransactionException exception) {
            options = new ManagerViews.CreationOptions(java.util.List.of(), java.util.List.of());
            visibleErrors.put("save", "The form options are temporarily unavailable. Your input has been retained; try again.");
        }
        model.addAttribute("errors", java.util.Map.copyOf(visibleErrors));
        model.addAttribute("options", options);
        return "manager/create";
    }

    private static void identity(Model model, CurrentAccountPrincipal principal) {
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
    }
}
