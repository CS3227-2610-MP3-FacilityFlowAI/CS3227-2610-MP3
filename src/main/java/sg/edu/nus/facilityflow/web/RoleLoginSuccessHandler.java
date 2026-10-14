package sg.edu.nus.facilityflow.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;

@Component
public class RoleLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        CurrentAccountPrincipal principal = (CurrentAccountPrincipal) authentication.getPrincipal();
        String destination = switch (principal.role()) {
            case REQUESTER -> "/requester";
            case TECHNICIAN -> "/technician";
            case FACILITIES_MANAGER -> "/manager";
        };
        response.sendRedirect(request.getContextPath() + destination);
    }
}
