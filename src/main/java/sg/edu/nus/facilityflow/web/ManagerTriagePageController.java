package sg.edu.nus.facilityflow.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.manager.ManagerTriageResponse;
import sg.edu.nus.facilityflow.manager.ManagerTriageService;

/** Server-rendered Manager review surface for the advisory Triage Assistant. */
@Controller
@RequestMapping("/manager/requests/{displayId}/triage-assistant")
public final class ManagerTriagePageController {
    private static final String VIEW = "manager/triage";
    private static final String SAFE_UNAVAILABLE =
            "The Triage Assistant is temporarily unavailable. No request details were changed; "
                    + "continue with manual triage or try again.";

    private final ManagerTriageService service;

    public ManagerTriagePageController(ManagerTriageService service) {
        this.service = service;
    }

    @GetMapping
    ModelAndView page(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId,
            HttpServletResponse response) {
        noStore(response);
        return baseView(principal, requireDisplayId(displayId));
    }

    @PostMapping
    CompletionStage<ModelAndView> suggest(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @PathVariable String displayId,
            HttpServletResponse response) {
        noStore(response);
        String safeDisplayId = requireDisplayId(displayId);
        CompletionStage<ManagerTriageResponse> suggestion;
        try {
            suggestion = service.request(safeDisplayId);
        } catch (org.springframework.security.access.AccessDeniedException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            return CompletableFuture.completedFuture(
                    baseView(principal, safeDisplayId).addObject("assistantError", SAFE_UNAVAILABLE));
        }
        return suggestion.handle((result, failure) -> renderResult(principal, safeDisplayId, result, failure));
    }

    private static ModelAndView renderResult(
            CurrentAccountPrincipal principal,
            String displayId,
            ManagerTriageResponse response,
            Throwable failure) {
        ModelAndView page = baseView(principal, displayId);
        if (failure != null || response == null) {
            return page.addObject("assistantError", SAFE_UNAVAILABLE);
        }
        if (response instanceof ManagerTriageResponse.Success success) {
            return page.addObject("suggestion", success.result())
                    .addObject("ranking", success.ranking());
        }
        ManagerTriageResponse.Failure failed = (ManagerTriageResponse.Failure) response;
        return page.addObject("assistantError", failed.message());
    }

    private static ModelAndView baseView(CurrentAccountPrincipal principal, String displayId) {
        return new ModelAndView(VIEW)
                .addObject("displayId", displayId)
                .addObject("displayName", principal.displayName())
                .addObject("username", principal.username());
    }

    private static String requireDisplayId(String displayId) {
        if (displayId == null || !displayId.matches("FF-[0-9]{6}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return displayId;
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
