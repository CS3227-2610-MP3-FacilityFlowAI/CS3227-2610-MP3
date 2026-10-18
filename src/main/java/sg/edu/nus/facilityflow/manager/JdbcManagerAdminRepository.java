package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcManagerAdminRepository implements ManagerAdminRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcManagerAdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void requireManager(UUID actorId, String username, boolean writing) {
        String lock = writing ? " FOR UPDATE" : " FOR SHARE";
        Long count = jdbc.queryForObject("SELECT count(*) FROM (SELECT id FROM facilityflow.user_account "
                + "WHERE id = :id AND lower(username) = lower(:username) "
                + "AND role = 'FACILITIES_MANAGER' AND active" + lock + ") manager",
                Map.of("id", actorId, "username", username), Long.class);
        if (count == null || count != 1L) throw new AccessDeniedException("The requested operation is not available");
    }

    @Override
    public void lockManagerMutation() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(322726100025)", Map.of(), Object.class);
    }

    @Override
    public List<ManagerAdminViews.AccountRow> accounts(UUID actorId) {
        return jdbc.query("""
                SELECT a.id, a.username, a.display_name, a.role, a.active, a.version,
                       count(r.id) AS active_assignments
                  FROM facilityflow.user_account a
                  LEFT JOIN facilityflow.maintenance_request r
                    ON r.assigned_technician_id = a.id
                   AND r.status IN ('ASSIGNED', 'IN_PROGRESS')
                 GROUP BY a.id, a.username, a.display_name, a.role, a.active, a.version
                 ORDER BY a.active DESC, lower(a.display_name), lower(a.username), a.id
                """, Map.of(), (row, number) -> new ManagerAdminViews.AccountRow(
                row.getObject("id", UUID.class), row.getString("username"), row.getString("display_name"),
                row.getString("role"), row.getBoolean("active"), row.getLong("version"),
                row.getLong("active_assignments"), actorId.equals(row.getObject("id", UUID.class))));
    }

    @Override
    public Optional<LockedAccount> lockAccount(UUID id) {
        return jdbc.query("SELECT id, username, role, active, version FROM facilityflow.user_account "
                + "WHERE id = :id FOR UPDATE", Map.of("id", id), (row, number) -> {
                    Long assignments = jdbc.queryForObject("SELECT count(*) FROM facilityflow.maintenance_request "
                            + "WHERE assigned_technician_id = :id AND status IN ('ASSIGNED','IN_PROGRESS')",
                            Map.of("id", id), Long.class);
                    return new LockedAccount(row.getObject("id", UUID.class), row.getString("username"),
                            row.getString("role"), row.getBoolean("active"), row.getLong("version"),
                            assignments == null ? 0 : assignments);
                }).stream().findFirst();
    }

    @Override
    public void lockManagerAccounts() {
        jdbc.query("SELECT id FROM facilityflow.user_account WHERE role = 'FACILITIES_MANAGER' FOR UPDATE",
                Map.of(), (row, number) -> row.getObject("id", UUID.class));
    }

    @Override
    public long activeManagerCount() {
        Long value = jdbc.queryForObject("SELECT count(*) FROM facilityflow.user_account "
                + "WHERE role = 'FACILITIES_MANAGER' AND active", Map.of(), Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public boolean usernameExists(String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM facilityflow.user_account "
                + "WHERE lower(username) = lower(:name))", Map.of("name", username), Boolean.class));
    }

    @Override
    public UUID insertAccount(ManagerAdminCommands.CreateAccount command, String passwordHash) {
        return jdbc.queryForObject("""
                INSERT INTO facilityflow.user_account (username, display_name, password_hash, role)
                VALUES (:username, :displayName, :passwordHash, :role) RETURNING id
                """, new MapSqlParameterSource()
                .addValue("username", command.username().strip())
                .addValue("displayName", command.displayName().strip())
                .addValue("passwordHash", passwordHash).addValue("role", command.role()), UUID.class);
    }

    @Override
    public int updateAccountState(LockedAccount account, boolean active) {
        return jdbc.update("UPDATE facilityflow.user_account SET active = :active, session_version = session_version + 1, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND version = :version",
                Map.of("active", active, "id", account.id(), "version", account.version()));
    }

    @Override
    public int updateAccountRole(LockedAccount account, String role) {
        return jdbc.update("UPDATE facilityflow.user_account SET role = :role, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND version = :version",
                Map.of("role", role, "id", account.id(), "version", account.version()));
    }

    @Override
    public int updatePassword(LockedAccount account, String passwordHash) {
        return jdbc.update("UPDATE facilityflow.user_account SET password_hash = :hash, session_version = session_version + 1, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND version = :version",
                Map.of("hash", passwordHash, "id", account.id(), "version", account.version()));
    }

    @Override
    public void audit(UUID actorId, String action, String targetType, UUID targetId, String detailsJson) {
        jdbc.update("INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details) "
                + "VALUES (:actor, :action, :type, :target, CAST(:details AS jsonb))",
                Map.of("actor", actorId, "action", action, "type", targetType,
                        "target", targetId, "details", detailsJson));
    }

    @Override
    public List<ManagerAdminViews.CategoryRow> categories() {
        return jdbc.query("""
                SELECT c.id, c.name, c.active, c.version, count(r.id) AS request_count
                  FROM facilityflow.category c
                  LEFT JOIN facilityflow.maintenance_request r ON r.category_id = c.id
                 GROUP BY c.id, c.name, c.active, c.version
                 ORDER BY c.active DESC, lower(c.name), c.id
                """, Map.of(), (row, number) -> new ManagerAdminViews.CategoryRow(
                row.getObject("id", UUID.class), row.getString("name"), row.getBoolean("active"),
                row.getLong("version"), row.getLong("request_count")));
    }

    @Override
    public List<ManagerViews.Option> activeCategories() {
        return jdbc.query("SELECT id, name FROM facilityflow.category WHERE active ORDER BY lower(name), id",
                Map.of(), (row, number) -> new ManagerViews.Option(
                row.getObject("id", UUID.class), row.getString("name")));
    }

    @Override
    public boolean categoryNameExists(String name, UUID except) {
        return nameExists("category", name, except);
    }

    @Override
    public UUID insertCategory(String name) {
        return jdbc.queryForObject("INSERT INTO facilityflow.category (name) VALUES (:name) RETURNING id",
                Map.of("name", name), UUID.class);
    }

    @Override
    public Optional<LockedCatalogue> lockCategory(UUID id) {
        return lockCatalogue("category", id);
    }

    @Override
    public int renameCategory(LockedCatalogue category, String name) {
        return renameCatalogue("category", category, name);
    }

    @Override
    public boolean activeCategory(UUID id) {
        return Boolean.TRUE.equals(jdbc.query("SELECT active FROM facilityflow.category WHERE id = :id FOR SHARE",
                Map.of("id", id), result -> result.next() && result.getBoolean("active")));
    }

    @Override
    public int migrateCategory(LockedCatalogue category, UUID replacementId, UUID actorId) {
        jdbc.update("""
                INSERT INTO facilityflow.audit_event (actor_id, action, target_type, target_id, details)
                SELECT :actor, 'REQUEST_CATEGORY_MIGRATED', 'REQUEST', id,
                       jsonb_build_object('fromCategoryId', CAST(:source AS text),
                                          'replacementCategoryId', CAST(:replacement AS text))
                  FROM facilityflow.maintenance_request WHERE category_id = :source
                """, Map.of("actor", actorId, "source", category.id(), "replacement", replacementId));
        jdbc.update("UPDATE facilityflow.maintenance_request SET category_id = :replacement, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE category_id = :source",
                Map.of("source", category.id(), "replacement", replacementId));
        return jdbc.update("UPDATE facilityflow.category SET active = false, version = version + 1, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = :id AND version = :version AND active",
                Map.of("id", category.id(), "version", category.version()));
    }

    @Override
    public List<ManagerAdminViews.SkillRow> skills() {
        return jdbc.query("SELECT id, name, active, version FROM facilityflow.skill_tag "
                + "ORDER BY active DESC, lower(name), id", Map.of(), (row, number) -> new ManagerAdminViews.SkillRow(
                row.getObject("id", UUID.class), row.getString("name"), row.getBoolean("active"), row.getLong("version")));
    }

    @Override
    public List<ManagerAdminViews.TechnicianSkillsRow> technicianSkills() {
        return jdbc.query("SELECT id, display_name || ' (' || username || ')' AS label, version "
                + "FROM facilityflow.user_account WHERE role = 'TECHNICIAN' AND active "
                + "ORDER BY lower(display_name), lower(username), id", Map.of(), (row, number) -> {
                    UUID id = row.getObject("id", UUID.class);
                    Set<UUID> skills = Set.copyOf(jdbc.query("SELECT ts.skill_tag_id FROM facilityflow.technician_skill ts "
                            + "JOIN facilityflow.skill_tag s ON s.id = ts.skill_tag_id "
                            + "WHERE ts.technician_id = :id AND s.active",
                            Map.of("id", id), (skillsRow, skillNumber) -> skillsRow.getObject("skill_tag_id", UUID.class)));
                    return new ManagerAdminViews.TechnicianSkillsRow(
                            id, row.getString("label"), row.getLong("version"), skills);
                });
    }

    @Override
    public boolean skillNameExists(String name, UUID except) {
        return nameExists("skill_tag", name, except);
    }

    @Override
    public UUID insertSkill(String name) {
        return jdbc.queryForObject("INSERT INTO facilityflow.skill_tag (name) VALUES (:name) RETURNING id",
                Map.of("name", name), UUID.class);
    }

    @Override
    public Optional<LockedCatalogue> lockSkill(UUID id) {
        return lockCatalogue("skill_tag", id);
    }

    @Override
    public int renameSkill(LockedCatalogue skill, String name) {
        return renameCatalogue("skill_tag", skill, name);
    }

    @Override
    public int deactivateSkill(LockedCatalogue skill) {
        return jdbc.update("UPDATE facilityflow.skill_tag SET active = false, version = version + 1, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = :id AND version = :version AND active",
                Map.of("id", skill.id(), "version", skill.version()));
    }

    @Override
    public boolean activeSkills(Set<UUID> ids) {
        if (ids.isEmpty()) return true;
        List<UUID> matches = jdbc.query("SELECT id FROM facilityflow.skill_tag "
                + "WHERE id IN (:ids) AND active FOR SHARE", new MapSqlParameterSource("ids", ids),
                (row, number) -> row.getObject("id", UUID.class));
        return matches.size() == ids.size();
    }

    @Override
    public Optional<LockedAccount> lockActiveTechnician(UUID id) {
        return lockAccount(id).filter(account -> account.active() && account.role().equals("TECHNICIAN"));
    }

    @Override
    public int updateTechnicianSkills(LockedAccount technician, Set<UUID> activeSkillIds) {
        jdbc.update("DELETE FROM facilityflow.technician_skill ts USING facilityflow.skill_tag s "
                + "WHERE ts.skill_tag_id = s.id AND s.active AND ts.technician_id = :technician",
                Map.of("technician", technician.id()));
        for (UUID skill : activeSkillIds) {
            jdbc.update("INSERT INTO facilityflow.technician_skill (technician_id, skill_tag_id) "
                    + "VALUES (:technician, :skill) ON CONFLICT DO NOTHING",
                    Map.of("technician", technician.id(), "skill", skill));
        }
        return jdbc.update("UPDATE facilityflow.user_account SET version = version + 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = :id AND version = :version",
                Map.of("id", technician.id(), "version", technician.version()));
    }

    private boolean nameExists(String table, String name, UUID except) {
        var params = new MapSqlParameterSource("name", name);
        String exclusion = "";
        if (except != null) {
            exclusion = " AND id <> :except";
            params.addValue("except", except);
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM facilityflow." + table
                + " WHERE lower(name) = lower(:name)" + exclusion + ")", params, Boolean.class));
    }

    private Optional<LockedCatalogue> lockCatalogue(String table, UUID id) {
        return jdbc.query("SELECT id, name, active, version FROM facilityflow." + table
                + " WHERE id = :id FOR UPDATE", Map.of("id", id), (row, number) -> new LockedCatalogue(
                row.getObject("id", UUID.class), row.getString("name"), row.getBoolean("active"),
                row.getLong("version"))).stream().findFirst();
    }

    private int renameCatalogue(String table, LockedCatalogue item, String name) {
        return jdbc.update("UPDATE facilityflow." + table + " SET name = :name, version = version + 1, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = :id AND version = :version AND active",
                Map.of("name", name, "id", item.id(), "version", item.version()));
    }
}
