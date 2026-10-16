package sg.edu.nus.facilityflow.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
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
        model.addAttribute("categories", requests.categories());
        return "requester/create";
    }

    @PostMapping("/requester/requests")
    String create(@ModelAttribute("form") RequestDraft draft,
            @RequestParam(defaultValue = "") String submissionId, Model model, HttpServletResponse response) {
        model.addAttribute("submissionId", submissionId);
        model.addAttribute("errors", Map.of());
        try {
            return "redirect:/requester/requests/" + requests.create(draft, submissionId);
        } catch (RequestValidationException exception) {
            response.setStatus(422);
            model.addAttribute("errors", exception.errors());
        } catch (DuplicateSubmissionException exception) {
            response.setStatus(409);
            model.addAttribute("message", exception.getMessage());
        } catch (AccessDeniedException exception) {
            response.setStatus(403);
            model.addAttribute("categories", java.util.List.of());
            model.addAttribute("message", "Your account access changed. Sign in again before submitting. Your input is below.");
            return "requester/create";
        } catch (DataAccessException exception) {
            response.setStatus(503);
            model.addAttribute("message", "The request could not be saved. Your input is below. Please try again; if this persists, contact support.");
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
        return "requester/create";
    }

    @GetMapping("/requester/requests/{displayId}")
    String detail(@PathVariable String displayId, Model model) {
        model.addAttribute("request", requests.detail(displayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request unavailable.")));
        return "requester/detail";
    }
}
