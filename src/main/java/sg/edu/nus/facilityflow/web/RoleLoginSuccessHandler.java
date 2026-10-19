package sg.edu.nus.facilityflow.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.identity.JdbcIdentityOperations;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class RoleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JdbcIdentityOperations operations;

    public RoleLoginSuccessHandler(JdbcIdentityOperations operations) {
        this.operations = operations;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        CurrentAccountPrincipal principal = (CurrentAccountPrincipal) authentication.getPrincipal();
        try {
            operations.recordLogin(principal);
        } catch (DataAccessException | AccessDeniedException exception) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        String destination = switch (principal.role()) {
            case REQUESTER -> "/requester";
            case TECHNICIAN -> "/technician";
            case FACILITIES_MANAGER -> "/manager";
        };
        response.sendRedirect(request.getContextPath() + destination);
    }
}
