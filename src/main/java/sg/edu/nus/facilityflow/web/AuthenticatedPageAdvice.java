package sg.edu.nus.facilityflow.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;

@ControllerAdvice
public class AuthenticatedPageAdvice {
    @ModelAttribute
    void identity(@AuthenticationPrincipal CurrentAccountPrincipal principal, Model model) {
        if (principal == null) return;
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
        model.addAttribute("workspace", switch (principal.role()) {
            case REQUESTER -> "/requester";
            case TECHNICIAN -> "/technician";
            case FACILITIES_MANAGER -> "/manager";
        });
    }
}
