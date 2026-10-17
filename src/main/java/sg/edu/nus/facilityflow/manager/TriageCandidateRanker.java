package sg.edu.nus.facilityflow.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;

/**
 * Applies the REQ-MGR-023 candidate ordering after typed AI triage output.
 * This class is intentionally mutation-free: a Manager still chooses and submits
 * the ordinary authorized assignment operation separately.
 */
@Component
public final class TriageCandidateRanker {

    private static final String RANKING_EXPLANATION =
            "Candidates are ranked by optional skill matches, workload, username, and Account ID."
                    + " Ranking is advisory; the Manager must choose a Technician explicitly.";

    public TriageRankingResult rank(TriageResult triageResult, List<TechnicianCandidate> candidates) {
        Objects.requireNonNull(triageResult, "triageResult");
        Objects.requireNonNull(candidates, "candidates");
        List<TechnicianCandidate> snapshot = new ArrayList<>(candidates.size());
        for (TechnicianCandidate candidate : candidates) {
            snapshot.add(Objects.requireNonNull(candidate, "candidates cannot contain null values"));
        }

        List<String> required = triageResult.requiredSkillTags();
        List<String> optional = triageResult.optionalSkillTags();
        Comparator<TechnicianCandidate> ordering = Comparator
                .comparingInt((TechnicianCandidate candidate) -> optionalMatches(candidate, optional)).reversed()
                .thenComparingInt(TechnicianCandidate::workload)
                .thenComparing(TechnicianCandidate::username, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TechnicianCandidate::accountId);

        List<TechnicianCandidate> ranked = snapshot.stream()
                .filter(TechnicianCandidate::active)
                .filter(candidate -> candidate.skillTags().containsAll(required))
                .sorted(ordering)
                .toList();
        return ranked.isEmpty()
                ? TriageRankingResult.noCandidate()
                : new TriageRankingResult(ranked, RANKING_EXPLANATION);
    }

    private static int optionalMatches(TechnicianCandidate candidate, List<String> optional) {
        int matches = 0;
        for (String tag : optional) {
            if (candidate.skillTags().contains(tag)) {
                matches++;
            }
        }
        return matches;
    }
}
