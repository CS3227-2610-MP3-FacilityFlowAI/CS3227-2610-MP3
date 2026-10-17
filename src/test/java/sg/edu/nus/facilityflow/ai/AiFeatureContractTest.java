package sg.edu.nus.facilityflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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
}
