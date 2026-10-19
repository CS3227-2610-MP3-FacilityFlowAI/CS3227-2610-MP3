package sg.edu.nus.facilityflow.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import sg.edu.nus.facilityflow.ai.monitoring.AiMonitoringFilter;
import sg.edu.nus.facilityflow.ai.monitoring.AiUsageComparison;
import sg.edu.nus.facilityflow.ai.monitoring.AiMonitoring;

@Controller
public final class ManagerAiMonitoringController {
    private final AiMonitoring service;

    public ManagerAiMonitoringController(AiMonitoring service) { this.service = service; }

    @GetMapping("/manager/ai-monitoring")
    String page(@ModelAttribute("filters") AiMonitoringFilter filters, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            var page = service.read(filters);
            model.addAttribute("page", page);
            response.setStatus(page.errors().isEmpty() ? 200 : 400);
        } catch (DataAccessException | TransactionException unavailable) {
            response.setStatus(503);
            model.addAttribute("monitoringError", "AI monitoring is temporarily unavailable. Refresh to try again.");
        }
        return "manager/ai-monitoring";
    }

    @PostMapping("/manager/ai-monitoring/reconcile")
    String reconcile(@ModelAttribute("filters") AiMonitoringFilter filters,
            @ModelAttribute("portal") AiUsageComparison portal, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            var comparison = service.reconcile(filters, portal);
            model.addAttribute("page", comparison.page());
            model.addAttribute("comparisonErrors", comparison.errors());
            model.addAttribute("comparison", comparison.result());
            response.setStatus(comparison.errors().isEmpty() ? 200 : 400);
        } catch (DataAccessException | TransactionException unavailable) {
            response.setStatus(503);
            model.addAttribute("monitoringError", "AI monitoring is temporarily unavailable. Refresh to try again.");
        }
        return "manager/ai-monitoring";
    }
}
