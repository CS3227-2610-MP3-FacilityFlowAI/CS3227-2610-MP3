package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Objects;

/** Deterministic advisory ranking; it does not perform assignment or persistence. */
public record TriageRankingResult(List<TechnicianCandidate> rankedCandidates, String explanation) {

    public TriageRankingResult {
        Objects.requireNonNull(rankedCandidates, "rankedCandidates");
        if (rankedCandidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("rankedCandidates cannot contain null values.");
        }
        rankedCandidates = List.copyOf(rankedCandidates);
        explanation = Objects.requireNonNull(explanation, "explanation");
    }

    public boolean hasCandidate() {
        return !rankedCandidates.isEmpty();
    }

    public static TriageRankingResult noCandidate() {
        return new TriageRankingResult(List.of(),
                "No active Technician matches every required skill tag. "
                        + "The Manager must choose manually or adjust accepted skill tags.");
    }
}
