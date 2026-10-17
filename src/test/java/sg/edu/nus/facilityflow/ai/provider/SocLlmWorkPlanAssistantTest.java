package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.config.AiProviderRuntime;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.technician.RequesterUpdate;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResult;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.FakeProviderPort;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualExecutor;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;

/** Requirement tests for the bounded Technician Work Plan assistant seam. */
class SocLlmWorkPlanAssistantTest {
    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICIAN_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final String DISPLAY_ID = "REQ-PRIVATE-42";

    @Test
    @DisplayName("REQ-TECH-013/AI-SEC-004 bounded context excludes identifiers and orders latest updates")
    void contextProjectionExcludesIdentifiersAndOrdersLatestTenOldestFirst() {
        Instant base = Instant.parse("2026-10-16T00:00:00Z");
        List<RequesterUpdate> updates = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(index -> new RequesterUpdate(
                        UUID.fromString(String.format("30000000-0000-0000-0000-%012d", index)),
                        base.plusSeconds(index),
                        "update-" + index))
                .toList();
        WorkPlanContext context = contextWith(updates);

        assertEquals(10, context.selectedRequesterUpdates().size());
        assertEquals("update-2", context.selectedRequesterUpdates().get(0).note());
        assertEquals("update-11", context.selectedRequesterUpdates().get(9).note());
        assertEquals(REQUEST_ID, context.requestId());
        assertEquals(DISPLAY_ID, context.displayId());
        assertThrows(IllegalArgumentException.class, () -> new WorkPlanContext(
                REQUEST_ID, DISPLAY_ID, TECHNICIAN_ID, "title", "description", "location",
                "Electrical", "High", "Critical", "COMPLETED", 1, List.of()));
    }

    @Test
    @DisplayName("REQ-TECH-014/015 valid response is typed advisory data and sends only allowlisted context")
    void validResponseIsTypedAndPromptDoesNotContainProtectedIdentifiers() {
        Fixture fixture = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> providerCompletion = fixture.port.enqueueChat();
        CompletionStage<WorkPlanResponse> response = fixture.runtime.request(contextWith(List.of(
                new RequesterUpdate(
                        UUID.fromString("30000000-0000-0000-0000-000000000003"),
                        Instant.parse("2026-10-16T00:00:01Z"),
                        "The pump makes a rattling sound."))));

        fixture.provider.runNext();
        assertEquals(1, fixture.port.chatCalls());
        SocLlmProviderPort.ProviderChatRequest request = fixture.port.requests().get(0);
        String userData = request.userDataMessage();
        assertTrue(userData.contains("The pump makes a rattling sound."));
        assertTrue(userData.contains("<request-data>"));
        assertFalse(userData.contains(REQUEST_ID.toString()));
        assertFalse(userData.contains(DISPLAY_ID));
        assertFalse(userData.contains(TECHNICIAN_ID.toString()));
        assertFalse(userData.contains("workLogs"));
        assertFalse(userData.contains("playbook"));

        providerCompletion.complete(ProviderTestControls.completion(validPlanJson()));
        WorkPlanResponse result = await(response);
        WorkPlanResponse.Success success = assertInstanceOf(WorkPlanResponse.Success.class, result);
        WorkPlanResult plan = success.plan();
        assertEquals(List.of("Inspect the supply connection."), plan.diagnosticSteps());
        assertEquals(List.of("Wear appropriate PPE."), plan.safetyPrecautions());
        assertEquals(List.of("Stop and escalate if energized."), plan.stopOrEscalateWhen());
        fixture.runtime.close();
    }

    @Test
    @DisplayName("AI-SEC-010/012 malformed or unsafe Work Plan output is rejected as AI_INVALID_OUTPUT")
    void malformedOrUnsafeOutputIsRejectedWithoutPartialPlan() {
        Fixture malformed = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> malformedStage = malformed.port.enqueueChat();
        CompletionStage<WorkPlanResponse> malformedResponse = malformed.runtime.request(contextWith(List.of()));
        malformed.provider.runNext();
        malformedStage.complete(ProviderTestControls.completion("{\"unexpected\":true}"));
        assertEquals(WorkPlanFailureCode.AI_INVALID_OUTPUT, failureCode(await(malformedResponse)));
        malformed.runtime.close();

        Fixture unsafe = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> unsafeStage = unsafe.port.enqueueChat();
        CompletionStage<WorkPlanResponse> unsafeResponse = unsafe.runtime.request(contextWith(List.of()));
        unsafe.provider.runNext();
        unsafeStage.complete(ProviderTestControls.completion(unsafePlanJson()));
        assertEquals(WorkPlanFailureCode.AI_INVALID_OUTPUT, failureCode(await(unsafeResponse)));
        unsafe.runtime.close();

        Fixture deceptive = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> deceptiveStage = deceptive.port.enqueueChat();
        CompletionStage<WorkPlanResponse> deceptiveResponse = deceptive.runtime.request(contextWith(List.of()));
        deceptive.provider.runNext();
        deceptiveStage.complete(ProviderTestControls.completion(deceptiveNegationPlanJson()));
        assertEquals(WorkPlanFailureCode.AI_INVALID_OUTPUT, failureCode(await(deceptiveResponse)));
        deceptive.runtime.close();

        Fixture genericStop = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> genericStopStage = genericStop.port.enqueueChat();
        CompletionStage<WorkPlanResponse> genericStopResponse = genericStop.runtime.request(contextWith(List.of()));
        genericStop.provider.runNext();
        genericStopStage.complete(ProviderTestControls.completion(genericStopPlanJson()));
        assertEquals(WorkPlanFailureCode.AI_INVALID_OUTPUT, failureCode(await(genericStopResponse)));
        genericStop.runtime.close();

        Fixture disclosure = readyFixture();
        CompletableFuture<SocLlmProviderPort.ProviderCompletion> disclosureStage = disclosure.port.enqueueChat();
        CompletionStage<WorkPlanResponse> disclosureResponse = disclosure.runtime.request(contextWith(List.of()));
        disclosure.provider.runNext();
        disclosureStage.complete(ProviderTestControls.completion(validPlanJson()
                .replace("The symptoms suggest a connection inspection.", DISPLAY_ID)));
        WorkPlanResponse rejected = await(disclosureResponse);
        assertEquals(WorkPlanFailureCode.AI_INVALID_OUTPUT, failureCode(rejected));
        assertFalse(rejected.toString().contains(DISPLAY_ID));
        disclosure.runtime.close();
    }

    @Test
    @DisplayName("REQ-AI-005/AI-SEC-016 unavailable provider returns stable fallback and no partial plan")
    void unavailableProviderReturnsSafeFallback() {
        Fixture fixture = readyFixture();
        fixture.port.throwOnChat();
        CompletionStage<WorkPlanResponse> response = fixture.runtime.request(contextWith(List.of()));

        fixture.provider.runNext();

        assertEquals(WorkPlanFailureCode.AI_UNAVAILABLE, failureCode(await(response)));
        fixture.runtime.close();
    }

    @Test
    @DisplayName("REQ-AI-005 application seam exposes no provider implementation types")
    void publicAssistantSeamDoesNotExposeProviderInternals() {
        for (var method : sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant.class.getMethods()) {
            assertFalse(method.getGenericReturnType().getTypeName().contains("ai.provider"));
            for (var parameter : method.getGenericParameterTypes()) {
                assertFalse(parameter.getTypeName().contains("ai.provider"));
            }
        }
    }

    private static Fixture readyFixture() {
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();
        AiProviderRuntime runtime = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider, scheduler);
        discovery.runNext();
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        awaitReadiness(runtime.readiness());
        return new Fixture(runtime, port, provider);
    }

    private static WorkPlanContext contextWith(List<RequesterUpdate> updates) {
        return new WorkPlanContext(
                REQUEST_ID,
                DISPLAY_ID,
                TECHNICIAN_ID,
                "Cooling pump vibration",
                "The cooling pump vibrates during operation.",
                "North plant room",
                "Mechanical",
                "High",
                "Critical",
                "ASSIGNED",
                7,
                updates);
    }

    private static String validPlanJson() {
        return "{" +
                "\"diagnosticSteps\":[\"Inspect the supply connection.\"]," +
                "\"safetyPrecautions\":[\"Wear appropriate PPE.\"]," +
                "\"suggestedTools\":[]," +
                "\"requesterQuestions\":[]," +
                "\"evidenceChecklist\":[\"Capture a photo of the connection.\"]," +
                "\"stopOrEscalateWhen\":[\"Stop and escalate if energized.\"]," +
                "\"rationale\":\"The symptoms suggest a connection inspection.\"" +
                "}";
    }

    private static String unsafePlanJson() {
        return "{" +
                "\"diagnosticSteps\":[\"Work on live equipment without PPE.\",\"Proceed without required protective equipment.\",\"Disable a safety interlock.\",\"Disable an interlock.\",\"Bypass an interlock.\"]," +
                "\"safetyPrecautions\":[\"Wear appropriate PPE.\"]," +
                "\"suggestedTools\":[]," +
                "\"requesterQuestions\":[]," +
                "\"evidenceChecklist\":[\"Capture a photo.\"]," +
                "\"stopOrEscalateWhen\":[\"Stop and escalate when unsafe.\"]," +
                "\"rationale\":\"Do not bypass safety controls.\"" +
                "}";
    }

    private static String deceptiveNegationPlanJson() {
        return "{" +
                "\"diagnosticSteps\":[\"Do not ignore this warning: work on live equipment without PPE.\"]," +
                "\"safetyPrecautions\":[\"Wear appropriate PPE.\"]," +
                "\"suggestedTools\":[]," +
                "\"requesterQuestions\":[]," +
                "\"evidenceChecklist\":[\"Capture a photo.\"]," +
                "\"stopOrEscalateWhen\":[\"Stop and escalate if energized.\"]," +
                "\"rationale\":\"Inspect safely.\"" +
                "}";
    }

    private static String genericStopPlanJson() {
        return "{" +
                "\"diagnosticSteps\":[\"Inspect the supply connection.\"]," +
                "\"safetyPrecautions\":[\"Wear appropriate PPE.\"]," +
                "\"suggestedTools\":[]," +
                "\"requesterQuestions\":[]," +
                "\"evidenceChecklist\":[\"Capture a photo.\"]," +
                "\"stopOrEscalateWhen\":[\"Continue as planned.\"]," +
                "\"rationale\":\"Inspect safely.\"" +
                "}";
    }

    private static WorkPlanFailureCode failureCode(WorkPlanResponse response) {
        WorkPlanResponse.Failure failure = assertInstanceOf(WorkPlanResponse.Failure.class, response);
        return failure.code();
    }

    private static WorkPlanResponse await(CompletionStage<WorkPlanResponse> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Work Plan response did not complete", failure);
        }
    }

    private static AiProviderLifecycle.State awaitReadiness(CompletionStage<AiProviderLifecycle.State> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("provider readiness did not complete", failure);
        }
    }

    private static <T> T assertInstanceOf(Class<T> type, Object value) {
        T result = type.cast(value);
        assertNotNull(result);
        return result;
    }

    private record Fixture(AiProviderRuntime runtime, FakeProviderPort port, ManualExecutor provider) {}
}
