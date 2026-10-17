package sg.edu.nus.facilityflow.web;

import java.util.concurrent.CompletionStage;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.technician.TechnicianWorkPlanService;

/** Manual HTTP seam for the read-only Technician Work Plan advisory. */
@RestController
@RequestMapping("/technician")
public final class TechnicianWorkPlanController {
    private final TechnicianWorkPlanService service;

    public TechnicianWorkPlanController(TechnicianWorkPlanService service) {
        this.service = service;
    }

    @PostMapping("/requests/{displayId}/work-plan")
    public CompletionStage<ResponseEntity<WorkPlanResponse>> request(@PathVariable String displayId) {
        return service.request(displayId)
                .thenApply(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(response));
    }
}
