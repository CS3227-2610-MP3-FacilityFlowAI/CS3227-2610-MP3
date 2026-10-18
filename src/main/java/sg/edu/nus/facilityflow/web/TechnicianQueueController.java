package sg.edu.nus.facilityflow.web;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.technician.TechnicianQueryException;
import sg.edu.nus.facilityflow.technician.TechnicianQueryService;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.ManagerPriority;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.QueueFilter;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.RequestDetail;
import sg.edu.nus.facilityflow.technician.TechnicianReadModels.RequestStatus;

/** Assigned-only Technician queue and Request read pages. */
@Controller
@RequestMapping("/technician/requests")
public final class TechnicianQueueController {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Singapore");
    private static final String QUEUE_VIEW = "technician/queue";
    private static final String DETAIL_VIEW = "technician/request-detail";

    private final TechnicianQueryService service;

    public TechnicianQueueController(TechnicianQueryService service) {
        this.service = service;
    }

    @GetMapping
    public String queue(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "priority", required = false) String priority,
            Model model,
            HttpServletResponse response) {
        return renderQueue(principal, q, status, category, priority, model, response, "Assigned Requests");
    }

    /** Reused by the coordinator-owned existing /technician dashboard mapping. */
    public String workspace(
            CurrentAccountPrincipal principal,
            Model model,
            HttpServletResponse response) {
        return renderQueue(principal, null, null, null, null, model, response, "Technician workspace");
    }

    @GetMapping("/{displayId}")
    public String detail(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId,
            Model model,
            HttpServletResponse response) {
        prepareIdentity(model, principal, "Request details");
        noStore(response);
        try {
            RequestDetail detail = service.detail(displayId);
            model.addAttribute("detail", detail);
        } catch (TechnicianQueryException exception) {
            if (exception.code() == TechnicianQueryException.Code.UNAVAILABLE) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                model.addAttribute("pageError", "Assigned work is temporarily unavailable. Please try again.");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("pageError", "The requested work is not available.");
            }
        }
        return DETAIL_VIEW;
    }

    private String renderQueue(
            CurrentAccountPrincipal principal,
            String q,
            String status,
            String category,
            String priority,
            Model model,
            HttpServletResponse response,
            String title) {
        prepareIdentity(model, principal, title);
        noStore(response);
        SubmittedFilters submitted = new SubmittedFilters(valueOrEmpty(q), valueOrEmpty(status),
                valueOrEmpty(category), valueOrEmpty(priority));
        model.addAttribute("submittedFilters", submitted);
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        RequestStatus parsedStatus = parseStatus(submitted.status(), fieldErrors);
        UUID parsedCategory = parseCategory(submitted.category(), fieldErrors);
        ManagerPriority parsedPriority = parsePriority(submitted.priority(), fieldErrors);

        if (!fieldErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("fieldErrors", Map.copyOf(fieldErrors));
            model.addAttribute("queryErrors", List.copyOf(fieldErrors.values()));
            model.addAttribute("preserveCategoryOption", !submitted.category().isEmpty());
            return QUEUE_VIEW;
        }

        try {
            var queue = service.queue(new QueueFilter(submitted.q(), parsedStatus,
                    parsedCategory, parsedPriority));
            model.addAttribute("queue", queue);
            model.addAttribute("preserveCategoryOption", !submitted.category().isEmpty()
                    && queue.categories().stream().noneMatch(option -> option.id().toString().equals(submitted.category())));
            model.addAttribute("queryErrors", List.of());
            model.addAttribute("fieldErrors", Map.of());
        } catch (TechnicianQueryException exception) {
            if (exception.code() == TechnicianQueryException.Code.UNAVAILABLE) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                model.addAttribute("pageError", "Assigned work is temporarily unavailable. Please try again.");
                model.addAttribute("queryErrors", List.of());
                model.addAttribute("preserveCategoryOption", !submitted.category().isEmpty());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                model.addAttribute("queryErrors", List.of("Check the selected filters and try again."));
                model.addAttribute("preserveCategoryOption", !submitted.category().isEmpty());
            }
        }
        return QUEUE_VIEW;
    }

    private static RequestStatus parseStatus(String raw, Map<String, String> errors) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return RequestStatus.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            errors.put("status", "Choose a valid status.");
            return null;
        }
    }

    private static UUID parseCategory(String raw, Map<String, String> errors) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            UUID categoryId = UUID.fromString(raw);
            if (!categoryId.toString().equals(raw)) {
                throw new IllegalArgumentException("noncanonical UUID");
            }
            return categoryId;
        } catch (IllegalArgumentException exception) {
            errors.put("category", "Choose a valid category.");
            return null;
        }
    }

    private static ManagerPriority parsePriority(String raw, Map<String, String> errors) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return ManagerPriority.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            errors.put("priority", "Choose a valid Manager priority.");
            return null;
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void prepareIdentity(Model model, CurrentAccountPrincipal principal, String title) {
        model.addAttribute("title", title);
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
        model.addAttribute("displayZone", DISPLAY_ZONE);
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    private record SubmittedFilters(String q, String status, String category, String priority) {}
}
