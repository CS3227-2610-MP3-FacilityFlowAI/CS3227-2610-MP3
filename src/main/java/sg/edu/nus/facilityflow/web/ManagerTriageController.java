package sg.edu.nus.facilityflow.web;

import java.util.concurrent.CompletionStage;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.facilityflow.manager.ManagerTriageResponse;
import sg.edu.nus.facilityflow.manager.ManagerTriageService;

/** POST-only, non-cacheable HTTP seam for Manager triage suggestions. */
@RestController
@RequestMapping("/manager")
public final class ManagerTriageController {
    private final ManagerTriageService service;

    public ManagerTriageController(ManagerTriageService service) {
        this.service = service;
    }

    @PostMapping("/requests/{displayId}/triage")
    public CompletionStage<ResponseEntity<ManagerTriageResponse>> triage(
            @PathVariable String displayId) {
        return service.request(displayId).thenApply(result -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result));
    }
}
