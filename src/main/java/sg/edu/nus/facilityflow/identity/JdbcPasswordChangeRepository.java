package sg.edu.nus.facilityflow.identity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-SH-002/004/006/014: serialized password replacement with one atomic safe audit. */
@Repository
public class JdbcPasswordChangeRepository implements PasswordChangeRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;

    public JdbcPasswordChangeRepository(DataSource dataSource, PasswordEncoder passwordEncoder) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void changeOwnPassword(CurrentAccountSnapshot actor, String currentPassword, String newPassword) {
        PasswordChangeService.requireSnapshot(actor);
        PasswordChangeService.validatePasswords(currentPassword, newPassword);
        try {
            transaction.executeWithoutResult(status -> {
                requireLockedAccount(actor);
                String storedHash = jdbc.queryForObject("""
                        SELECT password_hash FROM facilityflow.user_account WHERE id = :actorId
                        """, Map.of("actorId", actor.id()), String.class);
                String replacementHash = verifyAndEncode(currentPassword, newPassword, storedHash);
                MapSqlParameterSource parameters = new MapSqlParameterSource("actorId", actor.id())
                        .addValue("passwordHash", replacementHash);
                requireOne(jdbc.update("""
                        UPDATE facilityflow.user_account
                           SET password_hash = :passwordHash, version = version + 1,
                               updated_at = clock_timestamp()
                         WHERE id = :actorId
                        """, parameters));
                requireOne(jdbc.update("""
                        INSERT INTO facilityflow.audit_event
                               (actor_id, action, target_type, target_id, details, occurred_at)
                        VALUES (:actorId, 'ACCOUNT_PASSWORD_CHANGED', 'ACCOUNT', :actorId,
                                jsonb_build_object('changeType', 'OWN'), clock_timestamp())
                        """, Map.of("actorId", actor.id())));
            });
        } catch (PasswordChangeException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Rollback completes before mapping; no driver/transaction/encoder cause escapes.
            throw unavailable();
        }
    }

    private void requireLockedAccount(CurrentAccountSnapshot actor) {
        // Do not fetch password material until persisted authorization passes under this lock.
        List<Boolean> matches = jdbc.query("""
                SELECT active AND role = :role AND session_version = :sessionVersion AS permitted
                  FROM facilityflow.user_account WHERE id = :actorId FOR UPDATE
                """, Map.of("actorId", actor.id(), "role", actor.role().name(),
                        "sessionVersion", actor.sessionVersion()), (rs, row) -> rs.getBoolean("permitted"));
        if (matches.size() != 1 || !matches.getFirst()) {
            throw new PasswordChangeException(PasswordChangeException.Code.ACCESS_CHANGED);
        }
    }

    private String verifyAndEncode(String currentPassword, String newPassword, String storedHash) {
        boolean matches;
        try {
            matches = passwordEncoder.matches(currentPassword, storedHash);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        if (!matches) {
            throw new PasswordChangeException(PasswordChangeException.Code.INCORRECT_CURRENT_PASSWORD,
                    Map.of("currentPassword", "The current password is incorrect."));
        }
        try {
            String encoded = passwordEncoder.encode(newPassword);
            if (encoded == null || encoded.isEmpty()) {
                throw unavailable();
            }
            return encoded;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private static void requireOne(int count) {
        if (count != 1) {
            throw unavailable();
        }
    }

    private static PasswordChangeException unavailable() {
        return new PasswordChangeException(PasswordChangeException.Code.UNAVAILABLE);
    }
}
