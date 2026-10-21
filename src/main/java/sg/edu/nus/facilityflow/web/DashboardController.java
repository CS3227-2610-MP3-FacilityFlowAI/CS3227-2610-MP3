package sg.edu.nus.facilityflow.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;

@Controller
public class DashboardController {

    private final TechnicianQueueController technicianQueue;

    public DashboardController(TechnicianQueueController technicianQueue) {
        this.technicianQueue = technicianQueue;
    }

    @GetMapping("/technician")
    String technician(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            Model model,
            HttpServletResponse response) {
        return technicianQueue.workspace(principal, model, response);
    }

    @GetMapping("/manager")
    String manager() {
        // REQ-SH-001/012: enter the complete Manager workspace instead of the
        // legacy link list, so every Manager capability is immediately reachable.
        return "redirect:/manager/overview";
    }
}
