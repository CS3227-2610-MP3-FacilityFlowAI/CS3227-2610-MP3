package sg.edu.nus.facilityflow.web;

import java.util.concurrent.CompletionStage;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportInput;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportService;

/** POST-only, non-cacheable HTTP seam for temporary Requester Smart Report suggestions. */
@RestController
@RequestMapping("/requester")
public final class RequesterSmartReportController {
    private final RequesterSmartReportService service;

    public RequesterSmartReportController(RequesterSmartReportService service) {
        this.service = service;
    }

    @PostMapping("/smart-report")
    public CompletionStage<ResponseEntity<SmartReportResponse>> create(
            @RequestBody RequesterSmartReportInput input) {
        return response(service.requestCreate(input));
    }

    @PostMapping("/requests/{displayId}/smart-report")
    public CompletionStage<ResponseEntity<SmartReportResponse>> edit(
            @PathVariable String displayId,
            @RequestBody RequesterSmartReportInput input) {
        return response(service.requestEdit(displayId, input));
    }

    private static CompletionStage<ResponseEntity<SmartReportResponse>> response(
            CompletionStage<SmartReportResponse> stage) {
        return stage.thenApply(result -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result));
    }
}
