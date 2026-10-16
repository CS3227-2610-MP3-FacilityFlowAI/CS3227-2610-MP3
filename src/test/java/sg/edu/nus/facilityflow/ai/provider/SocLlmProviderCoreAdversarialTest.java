package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.CallHandle;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.CompletionResult;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.Failure;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.FailureCode;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.PreparedChat;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.Readiness;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;

class SocLlmProviderCoreAdversarialTest {

    @Test
    void aiSec020ReadinessTimeoutWinsWhileSynchronousListModelsIsBlockedAndCancelsLateStage() {
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        BlockingProviderPort port = new BlockingProviderPort();
        ExecutorService dispatch = Executors.newSingleThreadExecutor();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        try {
            CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
            port.awaitModelsEntered();

            scheduler.advanceBy(Duration.ofSeconds(30));

            assertEquals(Readiness.NEGATIVE, join(readiness));
            assertEquals(Readiness.NEGATIVE, core.readiness());
            port.releaseModels();
            port.modelsStage.awaitCancellation();
            assertTrue(port.modelsStage.isCancelled());
            assertEquals(Readiness.NEGATIVE, core.readiness());
        } finally {
            port.releaseAll();
            dispatch.shutdownNow();
        }
    }

    @Test
    void aiSec020CloseWinsWhileSynchronousListModelsIsBlockedAndCancelsLateStage() {
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        BlockingProviderPort port = new BlockingProviderPort();
        ExecutorService dispatch = Executors.newSingleThreadExecutor();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        try {
            CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
            port.awaitModelsEntered();

            core.close();

            assertEquals(Readiness.NEGATIVE, join(readiness));
            assertEquals(Readiness.NEGATIVE, core.readiness());
            port.releaseModels();
            port.modelsStage.awaitCancellation();
            assertTrue(port.modelsStage.isCancelled());
        } finally {
            port.releaseAll();
            dispatch.shutdownNow();
        }
    }

    @Test
    void aiSec020DeadlineWinsWhileSynchronousCompleteIsBlockedAndCancelsLateStage() {
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        BlockingProviderPort port = new BlockingProviderPort();
        port.modelsBlocked = false;
        ExecutorService dispatch = Executors.newSingleThreadExecutor();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        try {
            assertEquals(Readiness.READY, join(core.beginStartupReadinessCheck()));
            CallHandle handle = core.complete(
                    new PreparedChat("system", "user"), clock.instant().plusSeconds(10));
            port.awaitChatEntered();

            scheduler.advanceBy(Duration.ofSeconds(10));

            assertFailure(join(handle.stage()), FailureCode.DEADLINE_EXCEEDED);
            port.releaseChat();
            port.chatStage.awaitCancellation();
            assertTrue(port.chatStage.isCancelled());
            assertEquals(Readiness.READY, core.readiness());
        } finally {
            port.releaseAll();
            dispatch.shutdownNow();
        }
    }

    @Test
    void aiSec006CallerCancellationWinsWhileSynchronousCompleteIsBlockedAndCancelsLateStage() {
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        BlockingProviderPort port = new BlockingProviderPort();
        port.modelsBlocked = false;
        ExecutorService dispatch = Executors.newSingleThreadExecutor();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        try {
            assertEquals(Readiness.READY, join(core.beginStartupReadinessCheck()));
            CallHandle handle = core.complete(
                    new PreparedChat("system", "user"), clock.instant().plusSeconds(10));
            port.awaitChatEntered();

            assertTrue(handle.cancel());
            assertTrue(handle.stage().toCompletableFuture().isCancelled());
            port.releaseChat();
            port.chatStage.awaitCancellation();
            assertTrue(port.chatStage.isCancelled());
            assertEquals(Readiness.READY, core.readiness());
        } finally {
            port.releaseAll();
            dispatch.shutdownNow();
        }
    }

    @Test
    void aiSec006ReadinessViewRejectsEveryCompletableFutureMutationRoute() {
        ProviderTestControls.ManualExecutor dispatch = new ProviderTestControls.ManualExecutor();
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        ProviderTestControls.FakeProviderPort port = new ProviderTestControls.FakeProviderPort();
        CompletableFuture<ProviderModels> providerStage = port.enqueueModels();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        CompletionStage<Readiness> view = core.beginStartupReadinessCheck();

        assertAllMutationsRejected(view.toCompletableFuture(), Readiness.NEGATIVE);
        dispatch.runNext();
        providerStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(Readiness.READY, join(view));
        assertEquals(Readiness.READY, core.readiness());
    }

    @Test
    void aiSec006CallResultRejectsEveryCompletableFutureMutationRouteAndCannotReplay() {
        ProviderTestControls.ManualExecutor dispatch = new ProviderTestControls.ManualExecutor();
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        ProviderTestControls.FakeProviderPort port = new ProviderTestControls.FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
        dispatch.runNext();
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.READY, join(readiness));

        CompletableFuture<ProviderCompletion> providerStage = port.enqueueChat();
        CallHandle handle = core.complete(
                new PreparedChat("system", "user"), clock.instant().plusSeconds(10));
        CompletableFuture<CompletionResult> resultView = handle.stage().toCompletableFuture();
        Failure forged = new Failure(FailureCode.INVALID_OUTPUT);

        assertAllMutationsRejected(resultView, forged);
        dispatch.runNext();
        providerStage.complete(new ProviderCompletion("real", Optional.empty()));

        assertEquals("real", assertInstanceOf(
                SocLlmProviderCore.Completion.class, join(handle.stage())).content());
        assertFalse(handle.cancel());
        assertAllMutationsRejected(resultView, forged);
    }

    @Test
    void aiSec020QueuedChatDispatchRechecksAbsoluteDeadlineWithoutSchedulerCallback() {
        ProviderTestControls.ManualExecutor dispatch = new ProviderTestControls.ManualExecutor();
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler(clock);
        ProviderTestControls.FakeProviderPort port = new ProviderTestControls.FakeProviderPort();
        CompletableFuture<ProviderModels> models = port.enqueueModels();
        SocLlmProviderCore core = core(port, clock, dispatch, scheduler);
        CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
        dispatch.runNext();
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.READY, join(readiness));

        Instant absoluteDeadline = clock.instant().plusSeconds(10);
        CallHandle handle = core.complete(new PreparedChat("system", "user"), absoluteDeadline);
        assertEquals(1, dispatch.queuedCount());
        assertEquals(0, port.chatCalls());

        clock.advance(Duration.ofSeconds(10));
        dispatch.runNext();

        assertFailure(join(handle.stage()), FailureCode.DEADLINE_EXCEEDED);
        assertEquals(0, port.chatCalls());
        assertEquals(0, scheduler.activeTaskCount());
        assertEquals(Readiness.READY, core.readiness());
    }

    private static <T> void assertAllMutationsRejected(CompletableFuture<T> view, T forgedValue) {
        assertThrows(UnsupportedOperationException.class, () -> view.complete(forgedValue));
        assertThrows(UnsupportedOperationException.class,
                () -> view.completeExceptionally(new IllegalStateException("forged")));
        assertThrows(UnsupportedOperationException.class, () -> view.cancel(true));
        assertThrows(UnsupportedOperationException.class, () -> view.obtrudeValue(forgedValue));
        assertThrows(UnsupportedOperationException.class,
                () -> view.obtrudeException(new IllegalStateException("forged")));
        assertThrows(UnsupportedOperationException.class,
                () -> view.completeOnTimeout(forgedValue, 1, TimeUnit.DAYS));
        assertThrows(UnsupportedOperationException.class, () -> view.orTimeout(1, TimeUnit.DAYS));
        assertThrows(UnsupportedOperationException.class, () -> view.completeAsync(() -> forgedValue));
        assertThrows(UnsupportedOperationException.class,
                () -> view.completeAsync(() -> forgedValue, Runnable::run));
    }

    private static SocLlmProviderCore core(
            SocLlmProviderPort port,
            MutableClock clock,
            java.util.concurrent.Executor dispatch,
            ManualScheduler scheduler) {
        return new SocLlmProviderCore(
                ProviderTestControls.availableConfiguration(), port, clock, dispatch, scheduler);
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void assertFailure(CompletionResult result, FailureCode expected) {
        assertEquals(expected, assertInstanceOf(Failure.class, result).code());
    }

    private static final class BlockingProviderPort implements SocLlmProviderPort {
        private final CountDownLatch modelsEntered = new CountDownLatch(1);
        private final CountDownLatch modelsRelease = new CountDownLatch(1);
        private final CountDownLatch chatEntered = new CountDownLatch(1);
        private final CountDownLatch chatRelease = new CountDownLatch(1);
        private final CancellationTrackingFuture<ProviderModels> modelsStage = new CancellationTrackingFuture<>();
        private final CancellationTrackingFuture<ProviderCompletion> chatStage = new CancellationTrackingFuture<>();
        private volatile boolean modelsBlocked = true;

        @Override
        public CompletableFuture<ProviderModels> listModels() {
            modelsEntered.countDown();
            if (modelsBlocked) {
                await(modelsRelease, "listModels release");
            }
            if (!modelsBlocked) {
                modelsStage.complete(new ProviderModels(Set.of(ProviderTestControls.MODEL)));
            }
            return modelsStage;
        }

        @Override
        public CompletableFuture<ProviderCompletion> complete(ProviderChatRequest request) {
            chatEntered.countDown();
            await(chatRelease, "complete release");
            return chatStage;
        }

        void awaitModelsEntered() {
            await(modelsEntered, "listModels entry");
        }

        void awaitChatEntered() {
            await(chatEntered, "complete entry");
        }

        void releaseModels() {
            modelsRelease.countDown();
        }

        void releaseChat() {
            chatRelease.countDown();
        }

        void releaseAll() {
            releaseModels();
            releaseChat();
        }
    }

    private static final class CancellationTrackingFuture<T> extends CompletableFuture<T> {
        private final CountDownLatch cancellation = new CountDownLatch(1);

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            try {
                return super.cancel(mayInterruptIfRunning);
            } finally {
                cancellation.countDown();
            }
        }

        void awaitCancellation() {
            await(cancellation, "late provider-stage cancellation");
        }
    }

    private static void await(CountDownLatch latch, String event) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for " + event);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + event, interrupted);
        }
    }
}
