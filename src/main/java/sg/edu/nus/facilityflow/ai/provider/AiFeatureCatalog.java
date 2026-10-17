package sg.edu.nus.facilityflow.ai.provider;

import java.util.Map;
import java.util.Objects;

/** Closed provider-owned feature matrix; callers cannot select arbitrary policies. */
final class AiFeatureCatalog {
    private static final Map<Feature, Definition> DEFINITIONS = Map.of(
            Feature.SMART_REPORT, new Definition("smart-report-v1", "REQUESTER"),
            Feature.WORK_PLAN, new Definition("work-plan-v1", "TECHNICIAN"),
            Feature.TRIAGE, new Definition("triage-v1", "MANAGER"));

    private AiFeatureCatalog() {}

    static Definition definition(Feature feature) {
        return DEFINITIONS.get(Objects.requireNonNull(feature, "feature"));
    }

    enum Feature {
        SMART_REPORT,
        WORK_PLAN,
        TRIAGE
    }

    record Definition(String templateVersion, String role) {
        Definition {
            Objects.requireNonNull(templateVersion, "templateVersion");
            Objects.requireNonNull(role, "role");
        }
    }
}
