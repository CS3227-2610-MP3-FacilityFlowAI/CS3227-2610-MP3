package sg.edu.nus.facilityflow.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private static final String SELECT_ACCOUNT = """
            SELECT id, username, display_name, password_hash, role, active,
                   session_version, created_at, updated_at
              FROM user_account
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findByNormalizedUsername(String normalizedUsername) {
        return jdbc.query(
                        SELECT_ACCOUNT + " WHERE normalized_username = :username",
                        Map.of("username", normalizedUsername),
                        JdbcAccountRepository::mapAccount)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jdbc.query(
                        SELECT_ACCOUNT + " WHERE id = :id",
                        Map.of("id", id),
                        JdbcAccountRepository::mapAccount)
                .stream()
                .findFirst();
    }

    private static Account mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Account(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("display_name"),
                resultSet.getString("password_hash"),
                AccountRole.valueOf(resultSet.getString("role")),
                resultSet.getBoolean("active"),
                resultSet.getLong("session_version"),
                resultSet.getObject("created_at", Instant.class),
                resultSet.getObject("updated_at", Instant.class));
    }
}
