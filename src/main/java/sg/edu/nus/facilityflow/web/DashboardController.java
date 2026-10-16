package sg.edu.nus.facilityflow.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;

@Controller
public class DashboardController {

    @GetMapping("/about")
    String about() {
        return "about";
    }

    @GetMapping("/requester")
    String requester(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        addIdentity(model, principal, "Requester workspace");
        return "dashboard";
    }

    @GetMapping("/technician")
    String technician(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        addIdentity(model, principal, "Technician workspace");
        return "dashboard";
    }

    @GetMapping("/manager")
    String manager(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        addIdentity(model, principal, "Facilities Manager workspace");
        return "dashboard";
    }

    private static void addIdentity(
            Model model,
            CurrentAccountPrincipal principal,
            String title) {
        model.addAttribute("title", title);
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
    }
}
