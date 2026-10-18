package sg.edu.nus.facilityflow.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.TransactionException;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.manager.ManagerAuditFilters;
import sg.edu.nus.facilityflow.manager.ManagerRequestDraft;
import sg.edu.nus.facilityflow.manager.ManagerRequestCommands;
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

    @GetMapping("/requests/{displayId}")
    String request(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, Model model) {
        return requestPage(principal, displayId, java.util.Map.of(), null, null, model);
    }

    @PostMapping("/requests/{displayId}/assign")
    String assign(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.Assignment command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "assign", "Request assigned.", model, redirect,
                () -> manager.assign(displayId, command));
    }

    @PostMapping("/requests/{displayId}/reassign")
    String reassign(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.Reassignment command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "reassign", "Request reassigned.", model, redirect,
                () -> manager.reassign(displayId, command));
    }

    @PostMapping("/requests/{displayId}/cancel")
    String cancel(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.Cancellation command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "cancel", "Request cancelled.", model, redirect,
                () -> manager.cancel(displayId, command));
    }

    @PostMapping("/requests/{displayId}/close")
    String close(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @RequestParam String version,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, version, "close", "Completed work accepted and request closed.",
                model, redirect, () -> manager.close(displayId, version));
    }

    @PostMapping("/requests/{displayId}/return")
    String returnCompleted(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.ReturnCompleted command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "return", "Request returned to assigned work.",
                model, redirect, () -> manager.returnCompleted(displayId, command));
    }

    @PostMapping("/requests/{displayId}/reopen")
    String reopen(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.Reopen command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "reopen", "Request reopened and assigned.",
                model, redirect, () -> manager.reopen(displayId, command));
    }

    @PostMapping("/requests/{displayId}/correct")
    String correct(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.Correction command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "correct", "Request details corrected.",
                model, redirect, () -> manager.correct(displayId, command));
    }

    @PostMapping("/requests/{displayId}/triage-decision")
    String saveTriage(@AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId, @ModelAttribute ManagerRequestCommands.TriageDecision command,
            Model model, RedirectAttributes redirect) {
        return action(principal, displayId, command, "triage",
                "Reviewed triage choices saved and request assigned.", model, redirect,
                () -> manager.saveTriage(displayId, command));
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

    private String action(CurrentAccountPrincipal principal, String displayId, Object form,
            String failedAction, String notice, Model model, RedirectAttributes redirect,
            Runnable operation) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", notice);
            return "redirect:/manager/requests/" + displayId;
        } catch (ManagerValidationException exception) {
            return requestPage(principal, displayId, exception.errors(), form, failedAction, model);
        } catch (DataAccessException | TransactionException exception) {
            return requestPage(principal, displayId, java.util.Map.of("save",
                    "The action could not be saved. Your input has been retained; refresh if the request changed, then try again."),
                    form, failedAction, model);
        }
    }

    private String requestPage(CurrentAccountPrincipal principal, String displayId,
            java.util.Map<String, String> errors, Object form, String failedAction, Model model) {
        identity(model, principal);
        model.addAttribute("request", manager.request(displayId));
        model.addAttribute("errors", errors);
        model.addAttribute("form", form);
        model.addAttribute("failedAction", failedAction);
        return "manager/detail";
    }

    private static void identity(Model model, CurrentAccountPrincipal principal) {
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
    }
}
