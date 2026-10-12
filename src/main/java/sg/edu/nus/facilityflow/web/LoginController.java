package sg.edu.nus.facilityflow.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;

@Controller
public class LoginController {

    @GetMapping("/login")
    String login(
            Authentication authentication,
            Model model,
            jakarta.servlet.http.HttpServletRequest request) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentAccountPrincipal principal) {
            return "redirect:" + roleHome(principal);
        }
        if (request.getParameter("error") != null) {
            model.addAttribute("error", "Invalid username or password");
        } else if (request.getParameter("expired") != null) {
            model.addAttribute("error", "Your session is no longer valid. Please sign in again.");
        }
        model.addAttribute("loggedOut", request.getParameter("logout") != null);
        return "login";
    }

    @GetMapping("/")
    String home(Authentication authentication) {
        return "redirect:" + roleHome((CurrentAccountPrincipal) authentication.getPrincipal());
    }

    private static String roleHome(CurrentAccountPrincipal principal) {
        return switch (principal.role()) {
            case REQUESTER -> "/requester";
            case TECHNICIAN -> "/technician";
            case FACILITIES_MANAGER -> "/manager";
        };
    }
}
