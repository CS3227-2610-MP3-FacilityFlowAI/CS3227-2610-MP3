package sg.edu.nus.facilityflow.web;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import sg.edu.nus.facilityflow.identity.PasswordChangeService;

@Controller
public class PasswordController {
    private final PasswordChangeService passwords;

    public PasswordController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    @GetMapping("/account/password")
    String form() {
        return "password";
    }

    @PostMapping("/account/password")
    String change(@RequestParam(defaultValue = "") String currentPassword,
            @RequestParam(defaultValue = "") String newPassword, Model model) {
        try {
            passwords.changePassword(currentPassword, newPassword);
            return "redirect:/account/password?changed";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (DataAccessException exception) {
            model.addAttribute("error", "Password was not changed. Please try again.");
        }
        return "password";
    }
}
