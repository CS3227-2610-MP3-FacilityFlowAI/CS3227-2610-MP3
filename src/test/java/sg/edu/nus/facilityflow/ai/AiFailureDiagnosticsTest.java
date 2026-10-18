package sg.edu.nus.facilityflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.manager.ManagerTriageResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiFailureDiagnosticsTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void reqAi005AllFeatureFailuresSerializeStableCodeAndReadableMessage() {
        for (WorkPlanFailureCode code : WorkPlanFailureCode.values()) {
            assertResponse(WorkPlanResponse.failure(code), code.name());
        }
        for (SmartReportFailureCode code : SmartReportFailureCode.values()) {
            assertResponse(SmartReportResponse.failure(code), code.name());
        }
        for (TriageFailureCode code : TriageFailureCode.values()) {
            assertResponse(TriageResponse.failure(code), code.name());
            assertResponse(ManagerTriageResponse.failure(code), code.name());
        }
    }

    @Test
    void reqAi005FailureMessagesDistinguishFormatTimeoutLimitsAndConfiguration() {
        assertTrue(WorkPlanResponse.failure(WorkPlanFailureCode.AI_INVALID_OUTPUT).message()
                .contains("invalid format"));
        assertTrue(WorkPlanResponse.failure(WorkPlanFailureCode.AI_TIMEOUT).message().contains("timed out"));
        assertTrue(WorkPlanResponse.failure(WorkPlanFailureCode.AI_LIMITED).message().contains("limit"));
        assertTrue(WorkPlanResponse.failure(WorkPlanFailureCode.AI_CONFIGURATION_ERROR).message()
                .contains("authentication"));
        assertTrue(WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE).message()
                .contains("unavailable"));
    }

    private void assertResponse(Object response, String expectedCode) {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));
        assertEquals(expectedCode, json.get("code").textValue());
        assertTrue(json.get("message").isTextual());
        assertFalse(json.get("message").textValue().isBlank());
        assertEquals(2, json.size());
    }
}
