package sg.edu.nus.facilityflow.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;

class TriageCandidateRankerTest {

    private static final UUID ALPHA_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALPHA_LATER_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ZETA_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("REQ-MGR-023 filters inactive candidates and applies every deterministic tie-breaker")
    void ranksActiveCandidatesByOptionalMatchesWorkloadUsernameAndAccountId() {
        TriageResult triage = triage(List.of("Electrical"), List.of("Plumbing", "HVAC"));
        TechnicianCandidate zeta = candidate(ZETA_ACCOUNT, "zeta", true,
                Set.of("Electrical", "Plumbing", "HVAC"), 9);
        TechnicianCandidate alphaLater = candidate(ALPHA_LATER_ACCOUNT, "alpha", true,
                Set.of("Electrical"), 0);
        TechnicianCandidate alpha = candidate(ALPHA_ACCOUNT, "Alpha", true,
                Set.of("Electrical"), 0);
        TechnicianCandidate inactive = candidate(UUID.randomUUID(), "inactive", false,
                Set.of("Electrical", "Plumbing", "HVAC"), 0);

        TriageRankingResult result = new TriageCandidateRanker().rank(
                triage, List.of(alphaLater, inactive, zeta, alpha));

        assertEquals(List.of(zeta, alpha, alphaLater), result.rankedCandidates());
        assertTrue(result.hasCandidate());
        assertTrue(result.explanation().contains("advisory"));
    }

    @Test
    @DisplayName("REQ-MGR-023 explains the manual path when no active Technician matches")
    void noCandidateReturnsManualGuidance() {
        TriageRankingResult result = new TriageCandidateRanker().rank(
                triage(List.of("Electrical"), List.of("Plumbing")),
                List.of(candidate(ZETA_ACCOUNT, "zeta", true, Set.of("Plumbing"), 0)));

        assertFalse(result.hasCandidate());
        assertTrue(result.rankedCandidates().isEmpty());
        assertTrue(result.explanation().contains("choose manually"));
        assertTrue(result.explanation().contains("adjust accepted skill tags"));
    }

    @Test
    void candidateFactsAndRankingInputAreImmutable() {
        Set<String> skills = new HashSet<>(Set.of("Electrical"));
        List<TechnicianCandidate> input = new ArrayList<>();
        TechnicianCandidate candidate = candidate(ALPHA_ACCOUNT, "alpha", true, skills, 1);
        input.add(candidate);
        skills.add("Plumbing");

        TriageRankingResult result = new TriageCandidateRanker().rank(
                triage(List.of("Electrical"), List.of()), input);
        input.clear();

        assertEquals(Set.of("Electrical"), candidate.skillTags());
        assertEquals(List.of(candidate), result.rankedCandidates());
        assertThrows(UnsupportedOperationException.class,
                () -> result.rankedCandidates().add(candidate));
    }

    @Test
    void invalidWorkloadIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> candidate(ALPHA_ACCOUNT, "alpha", true, Set.of(), -1));
    }

    private static TechnicianCandidate candidate(UUID accountId, String username, boolean active,
            Set<String> skills, int workload) {
        return new TechnicianCandidate(accountId, username, active, skills, workload);
    }

    private static TriageResult triage(List<String> required, List<String> optional) {
        return new TriageResult("HVAC", "High", List.of(), required, optional, "Review the request.");
    }
}
