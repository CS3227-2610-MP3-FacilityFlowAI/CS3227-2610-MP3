package sg.edu.nus.facilityflow.identity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class CurrentAccountPrincipal
        implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final String displayName;
    private String password;
    private final AccountRole role;
    private final boolean active;
    private final long sessionVersion;

    private CurrentAccountPrincipal(
            UUID id,
            String username,
            String displayName,
            String password,
            AccountRole role,
            boolean active,
            long sessionVersion) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.password = password;
        this.role = role;
        this.active = active;
        this.sessionVersion = sessionVersion;
    }

    public static CurrentAccountPrincipal from(Account account) {
        return new CurrentAccountPrincipal(
                account.id(),
                account.username(),
                account.displayName(),
                account.passwordHash(),
                account.role(),
                account.active(),
                account.sessionVersion());
    }

    public UUID id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public AccountRole role() {
        return role;
    }

    public long sessionVersion() {
        return sessionVersion;
    }

    public CurrentAccount asCurrentAccount() {
        return new CurrentAccount(id, username, displayName, role, sessionVersion);
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
