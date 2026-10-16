package sg.edu.nus.facilityflow.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;
import sg.edu.nus.facilityflow.requestcore.RequestValidationException;
import sg.edu.nus.facilityflow.requester.DuplicateSubmissionException;
import sg.edu.nus.facilityflow.requester.RequesterRequests;
import sg.edu.nus.facilityflow.requester.RequestUnavailableException;
import sg.edu.nus.facilityflow.requester.StaleRequestException;

@Controller
public class RequesterRequestController {
    private final RequesterRequests requests;

    public RequesterRequestController(RequesterRequests requests) {
        this.requests = requests;
    }

    @GetMapping("/requester/requests")
    String list(Model model) {
        model.addAttribute("requests", requests.list());
        return "requester/list";
    }

    @GetMapping("/requester/requests/new")
    String form(Model model) {
        model.addAttribute("form", new RequestDraft("", "", "", "", "NORMAL"));
        model.addAttribute("submissionId", UUID.randomUUID().toString());
        model.addAttribute("errors", Map.of());
        model.addAttribute("editing", false);
        model.addAttribute("categories", requests.categories());
        return "requester/create";
    }

    @PostMapping("/requester/requests")
    String create(@ModelAttribute("form") RequestDraft draft,
            @RequestParam(defaultValue = "") String submissionId, Model model, HttpServletResponse response) {
        model.addAttribute("submissionId", submissionId);
        model.addAttribute("editing", false);
        model.addAttribute("errors", Map.of());
        String result = submit(() -> requests.create(draft, submissionId), model, response);
        if (result != null) return result;
        categories(model, response);
        return "requester/create";
    }

    @GetMapping("/requester/requests/{displayId}/edit")
    String editForm(@PathVariable String displayId, Model model) {
        var request = own(displayId);
        if (!request.status().equals("OPEN")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only OPEN requests can be edited.");
        model.addAttribute("form", new RequestDraft(request.title(), request.description(), request.location(), request.categoryId(), request.reportedUrgency()));
        model.addAttribute("editing", true);
        model.addAttribute("displayId", displayId);
        model.addAttribute("version", Long.toString(request.version()));
        model.addAttribute("errors", Map.of());
        model.addAttribute("categories", requests.categories());
        return "requester/create";
    }

    @PostMapping("/requester/requests/{displayId}/edit")
    String edit(@PathVariable String displayId, @ModelAttribute("form") RequestDraft draft,
            @RequestParam(defaultValue = "") String version, Model model, HttpServletResponse response) {
        model.addAttribute("editing", true);
        model.addAttribute("displayId", displayId);
        model.addAttribute("version", version);
        model.addAttribute("errors", Map.of());
        String result = submit(() -> requests.edit(displayId, draft, version), model, response);
        if (result != null) return result;
        categories(model, response);
        return "requester/create";
    }

    @GetMapping("/requester/requests/{displayId}/cancel")
    String cancelForm(@PathVariable String displayId, Model model) {
        return actionForm(displayId, "cancel", model);
    }

    @GetMapping("/requester/requests/{displayId}/updates/new")
    String updateForm(@PathVariable String displayId, Model model) {
        return actionForm(displayId, "updates", model);
    }

    private String actionForm(String displayId, String operation, Model model) {
        var request = own(displayId);
        if ((operation.equals("cancel") && !request.status().equals("OPEN"))
                || request.status().equals("CLOSED") || request.status().equals("CANCELLED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This action is not available for the current request.");
        }
        actionModel(model, displayId, operation, Long.toString(request.version()), "", false);
        model.addAttribute("requestTitle", request.title());
        return "requester/action";
    }

    @PostMapping("/requester/requests/{displayId}/cancel")
    String cancel(@PathVariable String displayId, @RequestParam(defaultValue = "") String version,
            @RequestParam(defaultValue = "") String reason, @RequestParam(defaultValue = "") String confirmed,
            @RequestParam(defaultValue = "keep") String decision, Model model, HttpServletResponse response) {
        if (!decision.equals("cancel")) return "redirect:/requester/requests/" + displayId;
        actionModel(model, displayId, "cancel", version, reason, confirmed.equals("true"));
        String result = submit(() -> requests.cancel(displayId, reason, confirmed.equals("true"), version), model, response);
        return result == null ? "requester/action" : result;
    }

    @PostMapping("/requester/requests/{displayId}/updates")
    String addUpdate(@PathVariable String displayId, @RequestParam(defaultValue = "") String version,
            @RequestParam(defaultValue = "") String note, Model model, HttpServletResponse response) {
        actionModel(model, displayId, "updates", version, note, false);
        String result = submit(() -> requests.addUpdate(displayId, note, version), model, response);
        return result == null ? "requester/action" : result;
    }

    private static void actionModel(Model model, String displayId, String operation, String version, String text, boolean confirmed) {
        model.addAttribute("displayId", displayId);
        model.addAttribute("operation", operation);
        model.addAttribute("version", version);
        model.addAttribute("text", text);
        model.addAttribute("confirmed", confirmed);
        model.addAttribute("errors", Map.of());
    }

    private String submit(Supplier<String> operation, Model model, HttpServletResponse response) {
        try {
            return "redirect:/requester/requests/" + operation.get();
        } catch (RequestValidationException exception) {
            response.setStatus(422);
            model.addAttribute("errors", exception.errors());
        } catch (DuplicateSubmissionException exception) {
            response.setStatus(409);
            model.addAttribute("message", exception.getMessage());
        } catch (StaleRequestException exception) {
            response.setStatus(409);
            model.addAttribute("message", exception.getMessage());
        } catch (RequestUnavailableException exception) {
            response.setStatus(404);
            model.addAttribute("message", "Request unavailable. Your input is below.");
        } catch (AccessDeniedException exception) {
            response.setStatus(403);
            model.addAttribute("message", "Your account access changed. Sign in again before submitting. Your input is below.");
        } catch (DataAccessException exception) {
            response.setStatus(503);
            model.addAttribute("message", "The request could not be saved. Your input is below. Please try again; if this persists, contact support.");
        }
        return null;
    }

    private void categories(Model model, HttpServletResponse response) {
        if (response.getStatus() == 403 || response.getStatus() == 404) {
            model.addAttribute("categories", java.util.List.of());
            return;
        }
        try {
            model.addAttribute("categories", requests.categories());
        } catch (AccessDeniedException exception) {
            response.setStatus(403);
            model.addAttribute("categories", java.util.List.of());
            model.addAttribute("message", "Your account access changed. Sign in again before submitting. Your input is below.");
        } catch (DataAccessException exception) {
            response.setStatus(503);
            model.addAttribute("categories", java.util.List.of());
            model.addAttribute("message", "Categories are temporarily unavailable. Your input is below. Please retry when the service recovers.");
        }
    }

    @GetMapping("/requester/requests/{displayId}")
    String detail(@PathVariable String displayId, Model model) {
        model.addAttribute("request", own(displayId));
        return "requester/detail";
    }

    private RequesterRequests.Detail own(String displayId) {
        return requests.detail(displayId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request unavailable."));
    }
}
