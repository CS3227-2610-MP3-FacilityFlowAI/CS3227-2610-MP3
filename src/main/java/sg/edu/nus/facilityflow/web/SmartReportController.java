package sg.edu.nus.facilityflow.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.facilityflow.ai.SmartReportInput;
import sg.edu.nus.facilityflow.ai.SmartReportService;
import sg.edu.nus.facilityflow.requester.*;

/** AI-SEC-029: ordinary role security and CSRF apply to these POST-only suggestion routes. */
@RestController
public class SmartReportController {
    private final SmartReportService service;
    public SmartReportController(SmartReportService service) { this.service = service; }

    @PostMapping("/requester/requests/assistant")
    ResponseEntity<SmartReportService.Reply> create(@ModelAttribute SmartReportInput input) {
        return response(service.suggest(null, null, input));
    }

    @PostMapping("/requester/requests/{displayId}/assistant")
    ResponseEntity<SmartReportService.Reply> edit(@PathVariable String displayId,
            @RequestParam(defaultValue = "") String version, @ModelAttribute SmartReportInput input) {
        return response(service.suggest(displayId, version, input));
    }

    private static ResponseEntity<SmartReportService.Reply> response(SmartReportService.Reply reply) {
        int status = switch (reply.status()) {
            case "SUCCEEDED" -> 200;
            case "AI_INVALID_REQUEST" -> 422;
            case "AI_LIMITED" -> 429;
            case "AI_TIMEOUT" -> 504;
            case "AI_INVALID_OUTPUT" -> 502;
            case "DISCARDED_ACCESS_CHANGED" -> 409;
            default -> 503;
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(reply);
    }

    @ExceptionHandler({AccessDeniedException.class, RequestUnavailableException.class, StaleRequestException.class, DataAccessException.class})
    ResponseEntity<SmartReportService.Reply> denied(RuntimeException exception) {
        int status = exception instanceof AccessDeniedException ? 403 : exception instanceof RequestUnavailableException ? 404
                : exception instanceof StaleRequestException ? 409 : 503;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new SmartReportService.Reply(
                "AI_UNAVAILABLE", null, "AI suggestions are unavailable. Keep your manual input; reload or sign in again if your access changed."));
    }
}
