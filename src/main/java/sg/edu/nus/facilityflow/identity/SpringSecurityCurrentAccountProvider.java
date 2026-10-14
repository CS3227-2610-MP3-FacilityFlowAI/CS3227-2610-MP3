package sg.edu.nus.facilityflow.identity;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityCurrentAccountProvider implements CurrentAccountProvider {

    @Override
    public CurrentAccount requireCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentAccountPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return principal.asCurrentAccount();
    }
}
