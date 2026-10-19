package sg.edu.nus.facilityflow.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.identity.PasswordChangeException;
import sg.edu.nus.facilityflow.identity.PasswordChangeService;

import java.util.Map;

@Controller
public final class SharedPagesController {
    private final PasswordChangeService passwordChangeService;

    public SharedPagesController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @GetMapping("/account/password")
    public String passwordPage(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(name = "changed", required = false) String changed,
            Model model,
            HttpServletResponse response) {
        identity(model, principal, "Change password");
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("changed", changed != null);
        model.addAttribute("fieldErrors", Map.of());
        return "account/change-password";
    }

    @PostMapping("/account/password")
    public ModelAndView changePassword(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            @RequestParam(name = "currentPassword", required = false) String currentPassword,
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            Model model,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            passwordChangeService.changeOwnPassword(currentPassword, newPassword, confirmPassword);
            RedirectView redirect = new RedirectView("/account/password?changed", true, false);
            redirect.setStatusCode(HttpStatus.SEE_OTHER);
            return new ModelAndView(redirect);
        } catch (PasswordChangeException exception) {
            int status = switch (exception.code()) {
                case INVALID_INPUT, INCORRECT_CURRENT_PASSWORD -> HttpServletResponse.SC_BAD_REQUEST;
                case ACCESS_CHANGED -> HttpServletResponse.SC_FORBIDDEN;
                case UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            };
            response.setStatus(status);
            identity(model, principal, "Change password");
            model.addAttribute("changed", false);
            model.addAttribute("fieldErrors", exception.fieldErrors());
            model.addAttribute("passwordError", switch (exception.code()) {
                case ACCESS_CHANGED -> "Your account access changed. Please sign in again.";
                case UNAVAILABLE -> "Password change is temporarily unavailable. Please try again.";
                case INVALID_INPUT -> exception.fieldErrors().isEmpty()
                        ? "Check the password fields and try again." : null;
                case INCORRECT_CURRENT_PASSWORD -> exception.fieldErrors().isEmpty()
                        ? "The current password is incorrect." : null;
            });
            return new ModelAndView("account/change-password", model.asMap());
        }
    }

    @GetMapping("/about")
    public String about(
            @AuthenticationPrincipal CurrentAccountPrincipal principal,
            Model model,
            HttpServletResponse response) {
        identity(model, principal, "About FacilityFlow AI");
        response.setHeader("Cache-Control", "no-store");
        return "about";
    }

    private static void identity(Model model, CurrentAccountPrincipal principal, String title) {
        model.addAttribute("title", title);
        model.addAttribute("username", principal.username());
        model.addAttribute("displayName", principal.displayName());
        model.addAttribute("role", principal.role().name());
    }
}
