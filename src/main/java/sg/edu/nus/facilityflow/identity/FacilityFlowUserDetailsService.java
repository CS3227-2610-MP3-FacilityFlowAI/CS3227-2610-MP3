package sg.edu.nus.facilityflow.identity;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class FacilityFlowUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    public FacilityFlowUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        Account account = accounts.findByNormalizedUsername(normalized)
                .filter(Account::active)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return CurrentAccountPrincipal.from(account);
    }
}
