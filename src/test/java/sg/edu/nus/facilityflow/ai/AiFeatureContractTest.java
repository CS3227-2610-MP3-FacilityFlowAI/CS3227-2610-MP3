package sg.edu.nus.facilityflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.manager.TriageAssistant;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.ai.requester.SmartReportAssistant;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;

/** Contract tests for the shared feature-specific gateway seams. */
class AiFeatureContractTest {

    @Test
    void unavailableFeatureSeamsReturnTypedSafeFailures() {
        SmartReportContext report = new SmartReportContext(
                null, "The air conditioner is leaking water.", "Block A", null, null, List.of("HVAC"));
        SmartReportResponse reportResponse = SmartReportAssistant.unavailable()
                .request(report).toCompletableFuture().join();
        assertEquals(SmartReportFailureCode.AI_UNAVAILABLE,
                assertInstanceOf(SmartReportResponse.Failure.class, reportResponse).code());

        TriageContext triage = new TriageContext(
                "Leaking air conditioner", "Water is pooling below the unit.", "Block A", "HVAC",
                "High", "Medium", "OPEN", List.of(), List.of("HVAC"), List.of("Electrical"));
        TriageResponse triageResponse = TriageAssistant.unavailable()
                .request(triage).toCompletableFuture().join();
        assertEquals(TriageFailureCode.AI_UNAVAILABLE,
                assertInstanceOf(TriageResponse.Failure.class, triageResponse).code());
    }

    @Test
    void contextsRejectMissingInputAndTerminalTriageState() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmartReportContext(null, "", null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SmartReportContext("rough", null, null, null, "Urgent", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new TriageContext("title", "description", "location", "category", "High",
                        "Medium", "CLOSED", List.of(), List.of(), List.of()));
    }

    @Test
    void typedResultsEnforceClosedPriorityAndUniqueSkillContracts() {
        SmartReportResult report = new SmartReportResult(
                "Leaking air conditioner", null, null, "HVAC", "High", List.of(), "Needs inspection.");
        assertEquals("High", report.suggestedUrgency());

        assertThrows(IllegalArgumentException.class, () -> new SmartReportResult(
                null, null, null, null, "Urgent", List.of("What happened?"), "Clarify the issue."));
        assertThrows(IllegalArgumentException.class, () -> new TriageResult(
                "HVAC", "Urgent", List.of(), List.of("Electrical"), List.of(), "Review the request."));
        assertThrows(IllegalArgumentException.class, () -> new TriageResult(
                "HVAC", "High", List.of(), List.of("Electrical"), List.of("Electrical"), "Review the request."));
    }

    @Test
    void delegatingFeatureSeamsForwardAbsoluteDeadline() {
        Instant deadline = Instant.parse("2026-10-17T00:00:30Z");
        AtomicReference<Instant> workPlanDeadline = new AtomicReference<>();
        WorkPlanContextFixture workPlan = new WorkPlanContextFixture();
        sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant workPlanDelegate =
                new sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant() {
                    @Override
                    public CompletionStage<sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse> request(
                            sg.edu.nus.facilityflow.ai.technician.WorkPlanContext context) {
                        return CompletableFuture.completedFuture(
                                sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse.failure(
                                        sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode.AI_UNAVAILABLE));
                    }

                    @Override
                    public CompletionStage<sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse> request(
                            sg.edu.nus.facilityflow.ai.technician.WorkPlanContext context,
                            Instant absoluteDeadline) {
                        workPlanDeadline.set(absoluteDeadline);
                        return request(context);
                    }
                };
        sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant.delegating(workPlanDelegate)
                .request(workPlan.context, deadline).toCompletableFuture().join();
        assertEquals(deadline, workPlanDeadline.get());
    }

    private static final class WorkPlanContextFixture {
        private final sg.edu.nus.facilityflow.ai.technician.WorkPlanContext context =
                new sg.edu.nus.facilityflow.ai.technician.WorkPlanContext(
                        java.util.UUID.randomUUID(), "FF-000001", java.util.UUID.randomUUID(),
                        "Pump issue", "The pump is noisy.", "Plant room", "Mechanical",
                        "High", "Normal", "ASSIGNED", 1, List.of());
    }
}
