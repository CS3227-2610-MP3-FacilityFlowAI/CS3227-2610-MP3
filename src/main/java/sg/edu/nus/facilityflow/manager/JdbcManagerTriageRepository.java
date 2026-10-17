package sg.edu.nus.facilityflow.manager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** JDBC-backed allowlisted Manager triage context and freshness checks. */
@Repository
public class JdbcManagerTriageRepository implements ManagerTriageRepository {
    private static final String ELIGIBLE = """
            SELECT r.id, r.display_id, r.title, r.description, r.location,
                   c.name AS category, r.reported_urgency, r.manager_priority,
                   r.status, r.version
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.category c ON c.id = r.category_id
              JOIN facilityflow.user_account a ON a.id = :managerId
             WHERE r.display_id = :displayId
               AND r.status NOT IN ('CANCELLED', 'CLOSED')
               AND a.active = true
               AND a.role = 'FACILITIES_MANAGER'
               AND (:sessionVersion < 0 OR a.session_version = :sessionVersion)
            """;
    private static final String REQUESTER_UPDATES = """
            SELECT id, note
              FROM facilityflow.requester_update
             WHERE request_id = :requestId
             ORDER BY created_at DESC, id DESC
             LIMIT 10
            """;
    private static final String REQUEST_CONTEXT = """
            SELECT r.title, r.description, r.location, c.name AS category,
                   r.reported_urgency, r.manager_priority, r.status
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.category c ON c.id = r.category_id
             WHERE r.display_id = :displayId
               AND r.status NOT IN ('CANCELLED', 'CLOSED')
            """;
    private static final String ACTIVE_CATEGORIES = """
            SELECT id, name, version
              FROM facilityflow.category
             WHERE active = true
             ORDER BY lower(name), id
            """;
    private static final String ACTIVE_SKILLS = """
            SELECT id, name, version
              FROM facilityflow.skill_tag
             WHERE active = true
             ORDER BY lower(name), id
            """;
    private static final String TECHNICIANS = """
            SELECT a.id, a.username, a.active, COUNT(DISTINCT r.id) AS workload
              FROM facilityflow.user_account a
              LEFT JOIN facilityflow.technician_skill ts ON ts.technician_id = a.id
              LEFT JOIN facilityflow.skill_tag st ON st.id = ts.skill_tag_id AND st.active = true
              LEFT JOIN facilityflow.maintenance_request r
                ON r.assigned_technician_id = a.id
               AND r.status IN ('ASSIGNED', 'IN_PROGRESS')
             WHERE a.role = 'TECHNICIAN'
             GROUP BY a.id, a.username, a.active
            """;
    private static final String TECHNICIAN_SKILLS = """
            SELECT st.name
              FROM facilityflow.technician_skill ts
              JOIN facilityflow.skill_tag st ON st.id = ts.skill_tag_id
             WHERE ts.technician_id = :technicianId
               AND st.active = true
             ORDER BY lower(st.name), st.id
            """;
    private static final String FRESH_REQUEST = """
            SELECT count(*)
              FROM facilityflow.maintenance_request r
              JOIN facilityflow.user_account a ON a.id = :managerId
             WHERE r.id = :requestId
               AND r.display_id = :displayId
               AND r.version = :requestVersion
               AND r.status NOT IN ('CANCELLED', 'CLOSED')
               AND a.active = true
               AND a.role = 'FACILITIES_MANAGER'
               AND (:sessionVersion < 0 OR a.session_version = :sessionVersion)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcManagerTriageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Eligibility> probe(String displayId, CurrentAccountSnapshot manager) {
        List<Eligibility> matches = jdbc.query(
                ELIGIBLE,
                Map.of("displayId", displayId, "managerId", manager.id(),
                        "sessionVersion", manager.sessionVersion()),
                (rs, row) -> new Eligibility(
                        rs.getObject("id", UUID.class), rs.getString("display_id"),
                        rs.getLong("version")));
        return matches.stream().findFirst();
    }

    @Override
    public ContextSnapshot loadContext(Eligibility eligibility) {
        Map<String, Object> request = Map.of("requestId", eligibility.requestId());
        RequestFacts facts = jdbc.queryForObject(
                REQUEST_CONTEXT,
                Map.of("displayId", eligibility.displayId()),
                (rs, row) -> new RequestFacts(
                        rs.getString("title"), rs.getString("description"), rs.getString("location"),
                        rs.getString("category"), rs.getString("reported_urgency"),
                        rs.getString("manager_priority"), rs.getString("status")));
        if (facts == null) {
            throw new IllegalStateException("triage request disappeared");
        }
        List<String> updates = jdbc.query(
                REQUESTER_UPDATES, request, (rs, row) -> rs.getString("note"));
        java.util.Collections.reverse(updates);
        List<CatalogueSnapshot> categories = jdbc.query(
                ACTIVE_CATEGORIES, Map.of(), JdbcManagerTriageRepository::mapCatalogue);
        List<CatalogueSnapshot> skills = jdbc.query(
                ACTIVE_SKILLS, Map.of(), JdbcManagerTriageRepository::mapCatalogue);
        List<TechnicianSnapshot> technicians = technicians();
        TriageContext context = new TriageContext(
                facts.title(), facts.description(), facts.location(), facts.category(),
                facts.reportedUrgency(), facts.managerPriority(), facts.status(), updates,
                categories.stream().map(CatalogueSnapshot::name).toList(),
                skills.stream().map(CatalogueSnapshot::name).toList());
        return new ContextSnapshot(eligibility, context, categories, skills, technicians);
    }

    @Override
    public boolean isStillEligible(
            ContextSnapshot context, CurrentAccountSnapshot manager, TriageResult result) {
        Number matches = jdbc.queryForObject(
                FRESH_REQUEST,
                Map.of("requestId", context.eligibility().requestId(),
                        "displayId", context.eligibility().displayId(),
                        "requestVersion", context.eligibility().requestVersion(),
                        "managerId", manager.id(), "sessionVersion", manager.sessionVersion()),
                Number.class);
        if (matches == null || matches.longValue() != 1L) {
            return false;
        }
        List<CatalogueSnapshot> categories = jdbc.query(
                ACTIVE_CATEGORIES, Map.of(), JdbcManagerTriageRepository::mapCatalogue);
        List<CatalogueSnapshot> skills = jdbc.query(
                ACTIVE_SKILLS, Map.of(), JdbcManagerTriageRepository::mapCatalogue);
        if (!categories.equals(context.activeCategories()) || !skills.equals(context.activeSkills())) {
            return false;
        }
        if (categories.stream().noneMatch(value -> value.name().equals(result.suggestedCategory()))) {
            return false;
        }
        Set<String> activeSkillNames = skills.stream().map(CatalogueSnapshot::name).collect(java.util.stream.Collectors.toSet());
        if (!activeSkillNames.containsAll(result.requiredSkillTags())
                || !activeSkillNames.containsAll(result.optionalSkillTags())) {
            return false;
        }
        return technicians().equals(context.technicians());
    }

    private List<TechnicianSnapshot> technicians() {
        return jdbc.query(TECHNICIANS, Map.of(), (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            List<String> skills = jdbc.query(TECHNICIAN_SKILLS, Map.of("technicianId", id),
                    (skillRs, skillRow) -> skillRs.getString("name"));
            return new TechnicianSnapshot(new TechnicianCandidate(
                    id, rs.getString("username"), rs.getBoolean("active"),
                    Set.copyOf(skills), rs.getInt("workload")));
        });
    }

    private static CatalogueSnapshot mapCatalogue(ResultSet rs, int row) throws SQLException {
        return new CatalogueSnapshot(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getLong("version"));
    }

    private record RequestFacts(String title, String description, String location, String category,
            String reportedUrgency, String managerPriority, String status) {}
}
