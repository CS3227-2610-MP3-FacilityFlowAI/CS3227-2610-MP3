package sg.edu.nus.facilityflow.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.edu.nus.facilityflow.identity.Account;
import sg.edu.nus.facilityflow.identity.AccountRepository;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;

/** Revalidates active state, role and session version for every protected request. */
@Component
public class PersistentAccountVerificationFilter extends OncePerRequestFilter {

    private final AccountRepository accounts;

    public PersistentAccountVerificationFilter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentAccountPrincipal principal) {
            Optional<Account> persisted = accounts.findById(principal.id());
            if (persisted.isEmpty()
                    || !persisted.get().active()
                    || persisted.get().sessionVersion() != principal.sessionVersion()) {
                expireSession(request);
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
            refreshAuthentication(authentication, principal, persisted.get());
        }
        filterChain.doFilter(request, response);
    }

    private static void refreshAuthentication(
            Authentication authentication,
            CurrentAccountPrincipal principal,
            Account persisted) {
        if (persisted.role() == principal.role()
                && persisted.username().equals(principal.username())
                && persisted.displayName().equals(principal.displayName())) {
            return;
        }
        CurrentAccountPrincipal refreshed = CurrentAccountPrincipal.from(persisted);
        refreshed.eraseCredentials();
        UsernamePasswordAuthenticationToken replacement =
                new UsernamePasswordAuthenticationToken(
                        refreshed, authentication.getCredentials(), refreshed.getAuthorities());
        replacement.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(replacement);
    }

    private static void expireSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
