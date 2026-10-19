package sg.edu.nus.facilityflow.identity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-SH-006/014: identity mutations and their audit records share one transaction. */
@Repository
public class JdbcIdentityOperations {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcIdentityOperations(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void changePassword(Account expected, String hash) {
        transactions.executeWithoutResult(transaction -> {
            int changed = jdbc.update("""
                    UPDATE facilityflow.user_account
                       SET password_hash = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND active = true AND role = ? AND session_version = ? AND password_hash = ?
                    """, hash, expected.id(), expected.role().name(), expected.sessionVersion(), expected.passwordHash());
            if (changed != 1) throw new AccessDeniedException("Account changed. Reload and try again.");
            appendAudit(expected.id(), "PASSWORD_CHANGED");
        });
    }

    public void recordLogin(CurrentAccountPrincipal principal) {
        transactions.executeWithoutResult(transaction -> {
            var valid = jdbc.queryForList("""
                    SELECT id FROM facilityflow.user_account
                     WHERE id = ? AND active = true AND role = ? AND session_version = ? FOR UPDATE
                    """, UUID.class, principal.id(), principal.role().name(), principal.sessionVersion());
            if (valid.isEmpty()) throw new AccessDeniedException("Sign in again.");
            appendAudit(principal.id(), "LOGIN_SUCCESS");
        });
    }

    private void appendAudit(UUID actor, String action) {
        jdbc.update("""
                INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id)
                VALUES (?, ?, 'ACCOUNT', ?)
                """, actor, action, actor);
    }
}
