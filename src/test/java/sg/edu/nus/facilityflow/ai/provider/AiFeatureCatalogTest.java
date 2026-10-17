package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Ensures the internal feature matrix stays closed and source-controlled. */
class AiFeatureCatalogTest {

    @Test
    void catalogContainsOnlyTheThreeApprovedFeatureSeams() {
        assertEquals(Set.of("SMART_REPORT", "WORK_PLAN", "TRIAGE"),
                Arrays.stream(AiFeatureCatalog.Feature.values()).map(Enum::name).collect(Collectors.toSet()));
        for (AiFeatureCatalog.Feature feature : AiFeatureCatalog.Feature.values()) {
            AiFeatureCatalog.Definition definition = AiFeatureCatalog.definition(feature);
            assertNotNull(definition);
            assertNotNull(definition.templateVersion());
            assertNotNull(definition.role());
        }
    }
}
