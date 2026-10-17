package sg.edu.nus.facilityflow.requester;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;

/** Authorization-first context and freshness seam for REQ-RQ-012/013/017 and AI-SEC-028. */
public interface RequesterSmartReportRepository {
    Optional<Eligibility> probeCreate(CurrentAccountSnapshot requester);

    Optional<Eligibility> probeEdit(String displayId, CurrentAccountSnapshot requester);

    ContextSnapshot loadContext(Eligibility eligibility, RequesterSmartReportInput input);

    boolean isStillEligible(
            ContextSnapshot context, CurrentAccountSnapshot requester, SmartReportResult result);

    record Eligibility(
            UUID requesterId, UUID requestId, String displayId, Long requestVersion) {
        public Eligibility {
            Objects.requireNonNull(requesterId, "requesterId");
            boolean create = requestId == null && displayId == null && requestVersion == null;
            boolean edit = requestId != null && displayId != null && requestVersion != null;
            if (!create && !edit) {
                throw new IllegalArgumentException("eligibility must represent create or edit");
            }
            if (displayId != null && !displayId.matches("FF-[0-9]{6}")) {
                throw new IllegalArgumentException("displayId is invalid");
            }
            if (requestVersion != null && requestVersion < 0) {
                throw new IllegalArgumentException("requestVersion cannot be negative");
            }
        }

        public boolean persistedEdit() {
            return requestId != null;
        }
    }

    record CategorySnapshot(UUID id, String name, long version) {
        public CategorySnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            if (name.isBlank() || version < 0) {
                throw new IllegalArgumentException("invalid category snapshot");
            }
        }
    }

    record ContextSnapshot(
            Eligibility eligibility,
            SmartReportContext assistantContext,
            List<CategorySnapshot> activeCategories) {
        public ContextSnapshot {
            Objects.requireNonNull(eligibility, "eligibility");
            Objects.requireNonNull(assistantContext, "assistantContext");
            activeCategories = List.copyOf(Objects.requireNonNull(activeCategories, "activeCategories"));
        }
    }
}
