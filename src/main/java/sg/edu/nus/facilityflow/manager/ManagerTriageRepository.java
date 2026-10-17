package sg.edu.nus.facilityflow.manager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Server-side eligibility, bounded context and freshness seam for Manager triage. */
public interface ManagerTriageRepository {
    Optional<Eligibility> probe(String displayId, CurrentAccountSnapshot manager);

    ContextSnapshot loadContext(Eligibility eligibility);

    boolean isStillEligible(
            ContextSnapshot context, CurrentAccountSnapshot manager, TriageResult result);

    record Eligibility(UUID requestId, String displayId, long requestVersion) {
        public Eligibility {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(displayId, "displayId");
            if (!displayId.matches("FF-[0-9]{6}") || requestVersion < 0) {
                throw new IllegalArgumentException("invalid triage eligibility");
            }
        }
    }

    record CatalogueSnapshot(UUID id, String name, long version) {
        public CatalogueSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            if (name.isBlank() || version < 0) {
                throw new IllegalArgumentException("invalid catalogue snapshot");
            }
        }
    }

    record TechnicianSnapshot(TechnicianCandidate candidate) {
        public TechnicianSnapshot {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record ContextSnapshot(
            Eligibility eligibility,
            TriageContext assistantContext,
            List<CatalogueSnapshot> activeCategories,
            List<CatalogueSnapshot> activeSkills,
            List<TechnicianSnapshot> technicians) {
        public ContextSnapshot {
            Objects.requireNonNull(eligibility, "eligibility");
            Objects.requireNonNull(assistantContext, "assistantContext");
            activeCategories = List.copyOf(Objects.requireNonNull(activeCategories, "activeCategories"));
            activeSkills = List.copyOf(Objects.requireNonNull(activeSkills, "activeSkills"));
            technicians = List.copyOf(Objects.requireNonNull(technicians, "technicians"));
        }

        public List<TechnicianCandidate> candidates() {
            return technicians.stream().map(TechnicianSnapshot::candidate).toList();
        }
    }
}
