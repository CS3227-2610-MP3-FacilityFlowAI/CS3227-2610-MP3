package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.config.AiProviderRuntime;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.FakeProviderPort;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualExecutor;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;

/** Requirement tests for the bounded Requester Smart Report provider seam. */
class SocLlmSmartReportAssistantTest {

    @Test
    @DisplayName("REQ-RQ-013/014 prompt uses only rough input and active labels")
    void validResponseUsesAllowlistedContextAndTypedResult() {
        Fixture fixture = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> providerCompletion = fixture.port.enqueueChat();
        CompletionStage<SmartReportResponse> response = fixture.runtime.request(context());

        fixture.provider.runNext();
        SocLlmProviderPort.ProviderChatRequest request = fixture.port.requests().get(0);
        String userData = request.userDataMessage();
        assertTrue(userData.contains("air conditioner"));
        assertTrue(userData.contains("HVAC"));
        assertTrue(userData.contains("<request-data>"));
        assertFalse(userData.contains("accountId"));
        assertFalse(userData.contains("requestId"));
        assertFalse(userData.contains("technician"));

        providerCompletion.complete(ProviderTestControls.completion(validJson()));
        SmartReportResponse.Success success = assertInstanceOf(SmartReportResponse.Success.class, await(response));
        SmartReportResult result = success.result();
        assertEquals("Leaking air conditioner", result.draftTitle());
        assertEquals("HVAC", result.suggestedCategory());
        assertEquals("High", result.suggestedUrgency());
        fixture.runtime.close();
    }

    @Test
    @DisplayName("AI-SEC-010 malformed, unsafe and inactive-category output is rejected")
    void invalidOutputIsRejectedWithoutPartialResult() {
        for (String content : List.of(
                "{\"unknown\":true}",
                "{\"draftTitle\":\"<script>x</script>\",\"missingInformationQuestions\":[],\"rationale\":\"Needs review.\"}",
                "{\"suggestedCategory\":\"Plumbing\",\"missingInformationQuestions\":[],\"rationale\":\"Needs review.\"}")) {
            Fixture fixture = readyFixture();
            CompletableFuture<SocLlmProviderPort.ProviderCompletion> providerCompletion = fixture.port.enqueueChat();
            CompletionStage<SmartReportResponse> response = fixture.runtime.request(context());
            fixture.provider.runNext();
            providerCompletion.complete(ProviderTestControls.completion(content));
            assertEquals(SmartReportFailureCode.AI_INVALID_OUTPUT, failureCode(await(response)));
            fixture.runtime.close();
        }
    }

    @Test
    @DisplayName("AI-SEC-007 oversized serialized Smart Report prompt fails before provider use")
    void oversizedPromptFailsBeforeProviderCall() {
        Fixture fixture = readyFixture();
        SmartReportContext oversized = new SmartReportContext(
                "rough", "description", "location", null, null,
                java.util.stream.IntStream.range(0, 400)
                        .mapToObj(index -> "Category label " + index + " with enough text")
                        .toList());

        SmartReportResponse response = fixture.runtime.request(oversized).toCompletableFuture().join();

        assertEquals(SmartReportFailureCode.AI_INVALID_REQUEST, failureCode(response));
        assertEquals(0, fixture.port.chatCalls());
        fixture.runtime.close();
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

    private static SmartReportContext context() {
        return new SmartReportContext(
                "Aircon leaking", "The air conditioner is leaking water.", "Block A", null, "High",
                List.of("Electrical", "HVAC"));
    }

    private static String validJson() {
        return "{"
                + "\"draftTitle\":\"Leaking air conditioner\","
                + "\"draftDescription\":\"Water is pooling below the indoor unit.\","
                + "\"draftLocation\":\"Block A\","
                + "\"suggestedCategory\":\"HVAC\","
                + "\"suggestedUrgency\":\"High\","
                + "\"missingInformationQuestions\":[\"When did the leak begin?\"],"
                + "\"rationale\":\"The description supports an HVAC inspection.\""
                + "}";
    }

    private static SmartReportFailureCode failureCode(SmartReportResponse response) {
        return assertInstanceOf(SmartReportResponse.Failure.class, response).code();
    }

    private static SmartReportResponse await(CompletionStage<SmartReportResponse> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Smart Report response did not complete", failure);
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
