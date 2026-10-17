package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkPlanSafetyPolicyTest {

    @Test
    @DisplayName("AI-SEC-012 policy is versioned and rejects multilingual dangerous advice")
    void versionedPolicyRejectsMultilingualDangerousAdvice() {
        assertTrue(WorkPlanSafetyPolicy.VERSION.startsWith("work-plan-safety-"));
        assertTrue(WorkPlanSafetyPolicy.rejects(List.of("Work on l1ve equipment without PPE.")));
        assertTrue(WorkPlanSafetyPolicy.rejects(List.of("Trabajar con equipo energizado.")));
        assertTrue(WorkPlanSafetyPolicy.rejects(List.of("Bekerja pada peralatan berarus.")));
    }

    @Test
    @DisplayName("AI-SEC-012 policy catches separators and leetspeak while respecting direct negation")
    void obfuscationAndNegationAreHandledDeterministically() {
        assertTrue(WorkPlanSafetyPolicy.rejects(List.of("b y p a s s the interlock.")));
        assertFalse(WorkPlanSafetyPolicy.rejects(List.of("Do not bypass the interlock.")));
        assertFalse(WorkPlanSafetyPolicy.rejects(List.of("Jangan pintas interlock.")));
        assertTrue(WorkPlanSafetyPolicy.rejects(
                List.of("Do not ignore this warning: work on live equipment.")));
        assertTrue(WorkPlanSafetyPolicy.rejects(
                List.of("Do not bypass the interlock unless supervisor approval is documented.")));
    }

    @Test
    @DisplayName("AI-SEC-012 stop guidance requires a concrete hazard")
    void stopGuidanceRequiresHazard() {
        assertTrue(WorkPlanSafetyPolicy.hasStopEscalation(List.of("Stop and escalate if energized.")));
        assertTrue(WorkPlanSafetyPolicy.hasStopEscalation(List.of("Detenga y escale si hay incendio.")));
        assertTrue(WorkPlanSafetyPolicy.hasStopEscalation(List.of("Hentikan kerja jika berlaku kebocoran gas.")));
        assertFalse(WorkPlanSafetyPolicy.hasStopEscalation(List.of("Continue as planned.")));
    }
}
