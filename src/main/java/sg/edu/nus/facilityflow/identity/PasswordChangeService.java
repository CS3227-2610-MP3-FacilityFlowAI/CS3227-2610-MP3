package sg.edu.nus.facilityflow.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** REQ-SH-002/004/006/014: own-password changes retain the current session. */
@Service
public class PasswordChangeService {
    private final CurrentAccountProvider currentAccounts;
    private final AccountRepository accounts;
    private final JdbcIdentityOperations operations;
    private final PasswordEncoder passwords;

    public PasswordChangeService(CurrentAccountProvider currentAccounts, AccountRepository accounts,
            JdbcIdentityOperations operations, PasswordEncoder passwords) {
        this.currentAccounts = currentAccounts;
        this.accounts = accounts;
        this.operations = operations;
        this.passwords = passwords;
    }

    public void changePassword(String currentPassword, String newPassword) {
        var current = currentAccounts.requireCurrentAccount();
        var account = accounts.findById(current.id()).orElseThrow(() -> new AccessDeniedException("Sign in again."));
        if (!account.active() || account.sessionVersion() != current.sessionVersion()
                || account.role() != current.role()) {
            throw new AccessDeniedException("Sign in again.");
        }
        if (newPassword == null || newPassword.codePointCount(0, newPassword.length()) < 8
                || newPassword.codePointCount(0, newPassword.length()) > 24) {
            throw new IllegalArgumentException("New password must contain 8–24 characters.");
        }
        if (currentPassword == null || currentPassword.codePointCount(0, currentPassword.length()) > 24
                || !passwords.matches(currentPassword, account.passwordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        // Hash before opening the short database transaction; the update rejects changed credentials.
        operations.changePassword(account, passwords.encode(newPassword));
    }
}
