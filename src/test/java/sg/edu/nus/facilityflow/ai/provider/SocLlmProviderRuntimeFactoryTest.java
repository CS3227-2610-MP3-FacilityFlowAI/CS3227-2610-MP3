package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.FakeProviderPort;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualExecutor;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;

/** Deterministic lifecycle tests using the package-private factory seam. */
class SocLlmProviderRuntimeFactoryTest {

    @Test
    @DisplayName("REQ-AI-003/AI-SEC-020 current SoCLaaS model metadata reaches READY")
    void configuredQwenModelWithCurrentSoclaasMetadataReachesReady() {
        String json = """
                {"object":"list","data":[{
                  "id":"qwen3.8:27b",
                  "object":"model",
                  "created":1760000000,
                  "owned_by":"soclaas",
                  "context_length":32768,
                  "context_window":{"input":24576,"output":8192},
                  "max_context_length":32768,
                  "max_model_len":32768,
                  "soclaas":{"deployment":{"region":"sg","replicas":2},"capabilities":["chat"]}
                }]}
                """;
        ProviderModels decodedModels = new ProviderEnvelopeDecoder().decodeModels(
                DecompressedBody.of(json.getBytes(StandardCharsets.UTF_8)));
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();

        AiProviderLifecycle lifecycle = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration("qwen3.8:27b"),
                port,
                clock,
                discovery,
                provider,
                scheduler);
        discovery.runNext();
        models.complete(decodedModels);

        assertEquals(AiProviderLifecycle.State.READY, await(lifecycle.readiness()));
        assertEquals(AiProviderLifecycle.State.READY, lifecycle.state());
        lifecycle.close();
    }

    @Test
    @DisplayName("REQ-AI-004/AI-SEC-020 available runtime starts readiness asynchronously")
    void availableRuntimeStartsCheckingAndReachesReadyAfterDiscoveryDispatch() {
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();

        AiProviderLifecycle lifecycle = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider, scheduler);

        assertTrueRuntimeChecking(lifecycle);
        assertEquals(1, discovery.queuedCount());
        assertEquals(0, port.modelCalls());

        discovery.runNext();
        assertEquals(1, port.modelCalls());
        assertEquals(0, provider.queuedCount());
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(AiProviderLifecycle.State.READY, await(lifecycle.readiness()));
        assertEquals(AiProviderLifecycle.State.READY, lifecycle.state());
        lifecycle.close();
    }

    @Test
    @DisplayName("REQ-AI-005/AI-SEC-020 close is idempotent and rejects late readiness")
    void closeIsIdempotentAndLateReadinessCannotBecomeReady() {
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();

        AiProviderLifecycle lifecycle = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider, scheduler);
        CompletionStage<AiProviderLifecycle.State> readiness = lifecycle.readiness();
        discovery.runNext();

        assertDoesNotThrow(lifecycle::close);
        assertDoesNotThrow(lifecycle::close);
        assertEquals(AiProviderLifecycle.State.NEGATIVE, lifecycle.state());
        assertEquals(0, scheduler.activeTaskCount());

        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(AiProviderLifecycle.State.NEGATIVE, await(readiness));
        assertEquals(AiProviderLifecycle.State.NEGATIVE, lifecycle.state());
        assertFalse(readiness.toCompletableFuture().isCancelled());
    }

    @Test
    @DisplayName("REQ-AI-005 lifecycle readiness mapping cannot be cancelled by callers")
    void readinessViewCancellationDoesNotMutateRuntimeState() {
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();

        AiProviderLifecycle lifecycle = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider, scheduler);
        CompletionStage<AiProviderLifecycle.State> view = lifecycle.readiness();
        assertTrue(view.toCompletableFuture().cancel(true));

        discovery.runNext();
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(AiProviderLifecycle.State.READY, await(lifecycle.readiness()));
        assertEquals(AiProviderLifecycle.State.READY, lifecycle.state());
        lifecycle.close();
    }

    @Test
    @DisplayName("AI-SEC-020 negative readiness cache expires into a new shared generation")
    void negativeCacheExpiryStartsFreshGenerationForConcurrentJoiners() {
        MutableClock clock = new MutableClock();
        ManualExecutor discovery = new ManualExecutor();
        ManualExecutor provider = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        CompletableFuture<ProviderModels> failedModels = port.enqueueModels();

        AiProviderLifecycle lifecycle = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider, scheduler);
        discovery.runNext();
        failedModels.completeExceptionally(new IllegalStateException("synthetic discovery failure"));
        assertEquals(AiProviderLifecycle.State.NEGATIVE, await(lifecycle.readiness()));
        assertEquals(AiProviderLifecycle.State.NEGATIVE, lifecycle.state());

        // Before expiry the negative cache is terminal and does not dispatch work.
        CompletionStage<AiProviderLifecycle.State> cached = lifecycle.readiness();
        assertEquals(AiProviderLifecycle.State.NEGATIVE, await(cached));
        assertEquals(0, discovery.queuedCount());

        clock.advance(Duration.ofSeconds(60));
        CompletableFuture<ProviderModels> recoveredModels = port.enqueueModels();
        CompletionStage<AiProviderLifecycle.State> first = lifecycle.readiness();
        CompletionStage<AiProviderLifecycle.State> second = lifecycle.readiness();
        assertEquals(AiProviderLifecycle.State.CHECKING, lifecycle.state());
        assertEquals(1, discovery.queuedCount());
        assertFalse(first.toCompletableFuture().isDone());
        assertFalse(second.toCompletableFuture().isDone());

        discovery.runNext();
        recoveredModels.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(AiProviderLifecycle.State.READY, await(first));
        assertEquals(AiProviderLifecycle.State.READY, await(second));
        lifecycle.close();
    }

    private static void assertTrueRuntimeChecking(AiProviderLifecycle lifecycle) {
        assertEquals(true, lifecycle.enabled());
        assertEquals(AiProviderLifecycle.State.CHECKING, lifecycle.state());
        assertFalse(lifecycle.readiness().toCompletableFuture().isDone());
    }

    private static AiProviderLifecycle.State await(CompletionStage<AiProviderLifecycle.State> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("provider lifecycle readiness did not complete", failure);
        }
    }
}
