package sg.edu.nus.facilityflow.ai.technician;

import java.util.List;
import java.util.Objects;

/** Strict typed Work Plan Assistant output shown as advisory text only. */
public record WorkPlanResult(
        List<String> diagnosticSteps,
        List<String> safetyPrecautions,
        List<String> suggestedTools,
        List<String> requesterQuestions,
        List<String> evidenceChecklist,
        List<String> stopOrEscalateWhen,
        String rationale) {

    public WorkPlanResult {
        diagnosticSteps = copy(diagnosticSteps, "diagnosticSteps");
        safetyPrecautions = copy(safetyPrecautions, "safetyPrecautions");
        suggestedTools = copy(suggestedTools, "suggestedTools");
        requesterQuestions = copy(requesterQuestions, "requesterQuestions");
        evidenceChecklist = copy(evidenceChecklist, "evidenceChecklist");
        stopOrEscalateWhen = copy(stopOrEscalateWhen, "stopOrEscalateWhen");
        rationale = Objects.requireNonNull(rationale, "rationale");
    }

    private static List<String> copy(List<String> value, String name) {
        return List.copyOf(Objects.requireNonNull(value, name));
    }
}
