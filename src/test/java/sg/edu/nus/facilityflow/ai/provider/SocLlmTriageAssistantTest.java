package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.config.AiProviderRuntime;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.FakeProviderPort;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualExecutor;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;

/** Requirement tests for the bounded Manager Triage provider seam. */
class SocLlmTriageAssistantTest {

    @Test
    @DisplayName("REQ-MGR-021/022 prompt is allowlisted and result is typed advisory data")
    void validResponseUsesAllowlistedContextWithoutIdentitiesOrWorkloads() {
        Fixture fixture = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> providerCompletion = fixture.port.enqueueChat();
        CompletionStage<TriageResponse> response = fixture.runtime.request(context());

        fixture.provider.runNext();
        SocLlmProviderPort.ProviderChatRequest request = fixture.port.requests().get(0);
        String userData = request.userDataMessage();
        assertTrue(userData.contains("Water is pooling"));
        assertTrue(userData.contains("HVAC"));
        assertTrue(userData.contains("Electrical"));
        assertTrue(userData.contains("<request-data>"));
        assertFalse(userData.contains("accountId"));
        assertFalse(userData.contains("technician"));
        assertFalse(userData.contains("workload"));

        providerCompletion.complete(ProviderTestControls.completion(validJson()));
        TriageResponse.Success success = assertInstanceOf(TriageResponse.Success.class, await(response));
        TriageResult result = success.result();
        assertEquals("HVAC", result.suggestedCategory());
        assertEquals("High", result.suggestedPriority());
        assertEquals(List.of("Electrical"), result.requiredSkillTags());
        assertEquals(List.of("Plumbing"), result.optionalSkillTags());
        fixture.runtime.close();
    }

    @Test
    @DisplayName("AI-SEC-010 inactive catalogue, duplicate and markup output is rejected")
    void invalidOutputIsRejectedWithoutPartialResult() {
        for (String content : List.of(
                "{\"suggestedCategory\":\"Plumbing\",\"suggestedPriority\":\"High\",\"clarificationQuestions\":[],\"requiredSkillTags\":[],\"optionalSkillTags\":[],\"rationale\":\"Review.\"}",
                "{\"suggestedCategory\":\"HVAC\",\"suggestedPriority\":\"High\",\"clarificationQuestions\":[],\"requiredSkillTags\":[\"Electrical\"],\"optionalSkillTags\":[\"Electrical\"],\"rationale\":\"Review.\"}",
                "{\"suggestedCategory\":\"HVAC\",\"suggestedPriority\":\"Urgent\",\"clarificationQuestions\":[],\"requiredSkillTags\":[],\"optionalSkillTags\":[],\"rationale\":\"Review.\"}",
                "{\"suggestedCategory\":\"HVAC\",\"suggestedPriority\":\"High\",\"clarificationQuestions\":[],\"requiredSkillTags\":[\"Electrical\",\"Electrical\"],\"optionalSkillTags\":[],\"rationale\":\"Review.\"}",
                "{\"suggestedCategory\":\"HVAC\",\"suggestedPriority\":\"High\",\"clarificationQuestions\":[\"<b>Need access?</b>\"],\"requiredSkillTags\":[],\"optionalSkillTags\":[],\"rationale\":\"Review.\"}")) {
            Fixture fixture = readyFixture();
            CompletableFuture<SocLlmProviderPort.ProviderCompletion> providerCompletion = fixture.port.enqueueChat();
            CompletionStage<TriageResponse> response = fixture.runtime.request(context());
            fixture.provider.runNext();
            providerCompletion.complete(ProviderTestControls.completion(content));
            assertEquals(TriageFailureCode.AI_INVALID_OUTPUT, failureCode(await(response)));
            fixture.runtime.close();
        }
    }

    @Test
    @DisplayName("REQ-MGR-020 cancelled and closed Requests are rejected before provider use")
    void terminalContextFailsBeforeProviderCall() {
        assertThrows(IllegalArgumentException.class, () -> new TriageContext(
                "title", "description", "location", "HVAC", "High", "Medium", "CLOSED",
                List.of(), List.of("HVAC"), List.of("Electrical")));
    }

    private static Fixture readyFixture() {
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<SocLlmProviderPort.ProviderModels> models = port.enqueueModels();
        AiProviderRuntime runtime = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider, scheduler);
        discovery.runNext();
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        awaitReadiness(runtime.readiness());
        return new Fixture(runtime, port, provider);
    }

    private static TriageContext context() {
        return new TriageContext(
                "Leaking air conditioner", "Water is pooling below the unit.", "Block A", "HVAC",
                "High", "Medium", "OPEN", List.of("The leak worsened today."),
                List.of("Electrical", "HVAC"), List.of("Electrical", "Plumbing"));
    }

    private static String validJson() {
        return "{"
                + "\"suggestedCategory\":\"HVAC\","
                + "\"suggestedPriority\":\"High\","
                + "\"clarificationQuestions\":[\"When did the leak begin?\"],"
                + "\"requiredSkillTags\":[\"Electrical\"],"
                + "\"optionalSkillTags\":[\"Plumbing\"],"
                + "\"rationale\":\"The symptoms suggest an HVAC inspection.\""
                + "}";
    }

    private static TriageFailureCode failureCode(TriageResponse response) {
        return assertInstanceOf(TriageResponse.Failure.class, response).code();
    }

    private static TriageResponse await(CompletionStage<TriageResponse> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Triage response did not complete", failure);
        }
    }

    private static AiProviderLifecycle.State awaitReadiness(CompletionStage<AiProviderLifecycle.State> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("provider readiness did not complete", failure);
        }
    }

    private record Fixture(AiProviderRuntime runtime, FakeProviderPort port, ManualExecutor provider) {}
}
