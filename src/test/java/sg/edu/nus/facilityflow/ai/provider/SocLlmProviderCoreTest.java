package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.ai.AiInvocationTelemetry;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.FakeProviderPort;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualExecutor;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.Completion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.CompletionResult;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.CallHandle;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.Failure;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.FailureCode;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.PreparedChat;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.Readiness;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderUsage;

class SocLlmProviderCoreTest {

    @Test
    void reqAi005SeparateDiscoveryAndProviderExecutorsOwnTheirRespectiveInvocations() {
        MutableClock clock = new MutableClock();
        ManualExecutor discoveryExecutor = new ManualExecutor();
        ManualExecutor providerExecutor = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        FakeProviderPort port = new FakeProviderPort();
        SocLlmProviderCore core = new SocLlmProviderCore(
                ProviderTestControls.availableConfiguration(),
                port,
                clock,
                discoveryExecutor,
                providerExecutor,
                scheduler);

        CompletableFuture<ProviderModels> modelStage = port.enqueueModels();
        CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();

        assertEquals(1, discoveryExecutor.queuedCount());
        assertEquals(0, providerExecutor.queuedCount());
        assertEquals(0, port.modelCalls());

        discoveryExecutor.runNext();
        assertEquals(1, port.modelCalls());
        assertEquals(0, providerExecutor.queuedCount());
        modelStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.READY, join(readiness));

        CompletableFuture<ProviderCompletion> chatStage = port.enqueueChat();
        CallHandle call = core.complete(
                new PreparedChat("system", "user"), clock.instant().plusSeconds(10));

        assertEquals(1, providerExecutor.queuedCount());
        assertEquals(0, port.chatCalls());
        providerExecutor.runNext();
        assertEquals(1, port.chatCalls());
        chatStage.complete(ProviderTestControls.completion("advisory"));

        Completion completion = assertInstanceOf(Completion.class, join(call.stage()));
        assertEquals("advisory", completion.content());
        core.close();
    }

    @Test
    void aiSec020StartupCheckIsNonBlockingSingleFlightAndRequiresExactModel() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> providerStage = rig.port.enqueueModels();

        CompletionStage<Readiness> first = rig.core.beginStartupReadinessCheck();
        CompletionStage<Readiness> second = rig.core.beginStartupReadinessCheck();

        assertEquals(Readiness.CHECKING, rig.core.readiness());
        assertFalse(first.toCompletableFuture().isDone());
        assertFalse(second.toCompletableFuture().isDone());
        assertEquals(1, rig.executor.queuedCount());
        assertEquals(0, rig.port.modelCalls());

        rig.executor.runNext();
        assertEquals(1, rig.port.modelCalls());
        providerStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL + "-other"));

        assertEquals(Readiness.NEGATIVE, join(first));
        assertEquals(Readiness.NEGATIVE, join(second));
        assertEquals(Readiness.NEGATIVE, rig.core.readiness());
    }

    @Test
    void aiSec006ReadinessViewCannotCancelTheSharedGeneration() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> providerStage = rig.port.enqueueModels();
        CompletionStage<Readiness> protectedView = rig.core.beginStartupReadinessCheck();
        CompletionStage<Readiness> survivingView = rig.core.beginStartupReadinessCheck();

        assertThrows(UnsupportedOperationException.class, () -> protectedView.toCompletableFuture().cancel(true));
        rig.executor.runNext();
        providerStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(Readiness.READY, join(protectedView));
        assertEquals(Readiness.READY, join(survivingView));
        assertEquals(Readiness.READY, rig.core.readiness());
    }

    @Test
    void aiSec020NegativeCacheSuppressesDiscoveryUntilTheExact60SecondBoundary() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> firstStage = rig.port.enqueueModels();
        CompletionStage<Readiness> first = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();
        firstStage.completeExceptionally(new IllegalStateException("failure"));
        assertEquals(Readiness.NEGATIVE, join(first));

        rig.scheduler.advanceBy(Duration.ofSeconds(59));
        assertEquals(Readiness.NEGATIVE, join(rig.core.beginStartupReadinessCheck()));
        assertEquals(1, rig.port.modelCalls());
        assertEquals(0, rig.executor.queuedCount());

        CompletableFuture<ProviderModels> secondStage = rig.port.enqueueModels();
        rig.scheduler.advanceBy(Duration.ofSeconds(1));
        CompletionStage<Readiness> retry = rig.core.beginStartupReadinessCheck();
        assertEquals(Readiness.CHECKING, rig.core.readiness());
        assertFalse(retry.toCompletableFuture().isDone());
        rig.executor.runNext();
        secondStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(Readiness.READY, join(retry));
        assertEquals(2, rig.port.modelCalls());
    }

    @Test
    void aiSec020ReadyIsTerminalForTheProcessLifetime() {
        TestRig rig = readyRig();
        rig.scheduler.advanceBy(Duration.ofDays(365));

        assertEquals(Readiness.READY, join(rig.core.beginStartupReadinessCheck()));
        assertEquals(Readiness.READY, rig.core.readiness());
        assertEquals(1, rig.port.modelCalls());
        assertEquals(0, rig.executor.queuedCount());
    }

    @Test
    void aiSec020SharedSchedulerRejectionPerformsZeroExecutorAndPortWork() {
        TestRig rig = new TestRig();
        rig.scheduler.rejectNewTasks();

        CompletionStage<Readiness> result = rig.core.beginStartupReadinessCheck();

        assertEquals(Readiness.NEGATIVE, join(result));
        assertEquals(0, rig.executor.submissions());
        assertEquals(0, rig.port.modelCalls());
        assertEquals(0, rig.scheduler.activeTaskCount());
    }

    @Test
    void aiSec020DiscoveryExecutorRejectionCancelsTheInstalledTimer() {
        TestRig rig = new TestRig();
        rig.executor.rejectNewTasks();

        CompletionStage<Readiness> result = rig.core.beginStartupReadinessCheck();

        assertEquals(Readiness.NEGATIVE, join(result));
        assertEquals(1, rig.executor.submissions());
        assertEquals(0, rig.port.modelCalls());
        assertEquals(0, rig.scheduler.activeTaskCount());
    }

    @Test
    void aiSec020SynchronousDiscoveryThrowBecomesNegativeWithoutLeaking() {
        TestRig rig = new TestRig();
        rig.port.throwOnModels();
        CompletionStage<Readiness> result = rig.core.beginStartupReadinessCheck();

        rig.executor.runNext();

        assertEquals(Readiness.NEGATIVE, join(result));
        assertEquals(0, rig.scheduler.activeTaskCount());
    }

    @Test
    void aiSec020NullDiscoveryStageAndNullResponseBecomeNegative() {
        TestRig nullStageRig = new TestRig();
        nullStageRig.port.returnNullModels();
        CompletionStage<Readiness> nullStage = nullStageRig.core.beginStartupReadinessCheck();
        nullStageRig.executor.runNext();

        TestRig nullResponseRig = new TestRig();
        CompletableFuture<ProviderModels> providerStage = nullResponseRig.port.enqueueModels();
        CompletionStage<Readiness> nullResponse = nullResponseRig.core.beginStartupReadinessCheck();
        nullResponseRig.executor.runNext();
        providerStage.complete(null);

        assertEquals(Readiness.NEGATIVE, join(nullStage));
        assertEquals(Readiness.NEGATIVE, join(nullResponse));
    }

    @Test
    void aiSec020MalformedRawDiscoveryResponseBecomesNegativeThroughProductionDecoder() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> providerStage = rig.port.enqueueModels();
        CompletionStage<Readiness> result = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();

        rig.port.completeModelsFromRaw(providerStage, "{\"data\":[null]}".getBytes(StandardCharsets.UTF_8));

        assertEquals(Readiness.NEGATIVE, join(result));
        assertEquals(Readiness.NEGATIVE, rig.core.readiness());
    }

    @Test
    void aiSec020TimeoutIgnoresLateSuccessAndStartsNegativeCacheAtTimeout() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> providerStage = rig.port.enqueueModels();
        CompletionStage<Readiness> result = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();

        rig.scheduler.advanceBy(Duration.ofSeconds(30));
        providerStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(Readiness.NEGATIVE, join(result));
        assertEquals(Readiness.NEGATIVE, rig.core.readiness());
        rig.scheduler.advanceBy(Duration.ofSeconds(59));
        assertEquals(Readiness.NEGATIVE, join(rig.core.beginStartupReadinessCheck()));
        assertEquals(1, rig.port.modelCalls());
    }

    @Test
    void aiSec020QueuedDiscoveryDoesNoPortWorkAfterItsTimeout() {
        TestRig rig = new TestRig();
        CompletionStage<Readiness> result = rig.core.beginStartupReadinessCheck();

        rig.scheduler.advanceBy(Duration.ofSeconds(30));
        rig.executor.runNext();

        assertEquals(Readiness.NEGATIVE, join(result));
        assertEquals(0, rig.port.modelCalls());
    }

    @Test
    void aiSec020OldGenerationCannotAffectRetryGeneration() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> oldStage = rig.port.enqueueModels();
        CompletionStage<Readiness> oldView = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();
        rig.scheduler.advanceBy(Duration.ofSeconds(30));
        assertEquals(Readiness.NEGATIVE, join(oldView));

        rig.scheduler.advanceBy(Duration.ofSeconds(60));
        CompletableFuture<ProviderModels> newStage = rig.port.enqueueModels();
        CompletionStage<Readiness> newView = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();

        oldStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.CHECKING, rig.core.readiness());
        assertFalse(newView.toCompletableFuture().isDone());

        newStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.READY, join(newView));
    }

    @Test
    void aiSec020CloseFencesCurrentAndFutureReadinessWork() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> stage = rig.port.enqueueModels();
        CompletionStage<Readiness> checking = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();

        rig.core.close();
        stage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertEquals(Readiness.NEGATIVE, join(checking));
        assertEquals(Readiness.NEGATIVE, join(rig.core.beginStartupReadinessCheck()));
        assertEquals(Readiness.NEGATIVE, rig.core.readiness());
        assertEquals(0, rig.scheduler.activeTaskCount());
        rig.core.close();
    }

    @Test
    void aiSec020CloseTerminatesQueuedChatBeforeProviderDispatch() {
        TestRig rig = readyRig();
        rig.port.enqueueChat();
        CallHandle call = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));

        assertEquals(1, rig.executor.queuedCount());
        rig.core.close();

        assertFailure(join(call.stage()), FailureCode.MODEL_UNAVAILABLE);
        rig.executor.runNext();
        assertEquals(0, rig.port.chatCalls());
    }

    @Test
    void aiSec020CloseCancelsInstalledChatStageAndIgnoresLateCompletion() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle call = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        rig.core.close();

        assertFailure(join(call.stage()), FailureCode.MODEL_UNAVAILABLE);
        assertTrue(providerStage.isCancelled());
        assertFalse(providerStage.complete(ProviderTestControls.completion("late")));
    }

    @Test
    void aiSec020CloseTerminatesCallWhileProviderInvocationIsBlocked() throws Exception {
        MutableClock clock = new MutableClock();
        ManualExecutor discoveryExecutor = new ManualExecutor();
        ManualExecutor providerExecutor = new ManualExecutor();
        ManualScheduler scheduler = new ManualScheduler(clock);
        BlockingProviderPort port = new BlockingProviderPort();
        SocLlmProviderCore core = new SocLlmProviderCore(
                ProviderTestControls.availableConfiguration(), port, clock,
                discoveryExecutor, providerExecutor, scheduler);
        CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
        discoveryExecutor.runNext();
        assertEquals(Readiness.READY, join(readiness));

        CallHandle call = core.complete(
                new PreparedChat("system", "user"), clock.instant().plusSeconds(10));
        Thread invocation = Thread.ofPlatform().start(providerExecutor::runNext);
        assertTrue(port.entered.await(1, TimeUnit.SECONDS));

        core.close();
        assertFailure(join(call.stage()), FailureCode.MODEL_UNAVAILABLE);

        port.release.countDown();
        invocation.join(1_000);
        assertFalse(invocation.isAlive());
        assertTrue(port.chatStage.isCancelled());
    }

    @Test
    void reqAi003CoreControlsModelTokenLimitMessagesAndPreservesAbsoluteDeadline() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        Instant deadline = rig.clock.instant().plusSeconds(20);
        PreparedChat chat = new PreparedChat("fixed-system", "structured-user-data");

        CallHandle result = rig.core.complete(chat, deadline);
        rig.executor.runNext();

        assertEquals(1, rig.port.chatCalls());
        ProviderChatRequest request = rig.port.requests().getFirst();
        assertEquals("fixed-system", request.systemMessage());
        assertEquals("structured-user-data", request.userDataMessage());
        assertEquals(ProviderTestControls.MODEL, request.model());
        assertEquals(321, request.maxOutputTokens());
        assertEquals(deadline, request.absoluteDeadline());
        assertFalse(request.toString().contains("fixed-system"));
        assertFalse(request.toString().contains("structured-user-data"));
        assertFalse(request.toString().contains(ProviderTestControls.MODEL));

        providerStage.complete(new ProviderCompletion("answer", Optional.of(new ProviderUsage(1L, 2L, 3L))));
        Completion completion = assertInstanceOf(Completion.class, join(result.stage()));
        assertEquals("answer", completion.content());
        assertEquals(1L, completion.usage().orElseThrow().promptTokens().orElseThrow());
        assertFalse(completion.toString().contains("answer"));
        assertFalse(chat.toString().contains("fixed-system"));
    }

    @Test
    void aiSec009Retries503OnceWithinTheOriginalDeadline() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> first = rig.port.enqueueChat();
        CompletableFuture<ProviderCompletion> second = rig.port.enqueueChat();
        AiInvocationTelemetry.Invocation invocation = new AiInvocationTelemetry.Invocation(
                java.util.UUID.randomUUID(), rig.clock.instant());
        CallHandle call;
        try (AiInvocationTelemetry.Scope ignored = AiInvocationTelemetry.bind(invocation)) {
            call = rig.core.complete(new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        }

        rig.executor.runNext();
        first.completeExceptionally(new ProviderFailure(ProviderFailure.Code.HTTP_SERVICE_UNAVAILABLE));
        assertEquals(1, rig.port.chatCalls());
        rig.scheduler.advanceBy(Duration.ofMillis(249));
        assertEquals(1, rig.port.chatCalls());
        rig.scheduler.advanceBy(Duration.ofMillis(1));
        assertEquals(1, rig.executor.queuedCount());
        rig.executor.runNext();
        assertEquals(2, rig.port.chatCalls());
        second.complete(ProviderTestControls.completion("answer"));

        Completion completion = assertInstanceOf(Completion.class, join(call.stage()));
        assertEquals("answer", completion.content());
        assertEquals(1, completion.retryCount());
        assertEquals(1, invocation.retryCount());
    }

    @Test
    void aiSec009Retries429OnlyWithApprovedRetryAfterAndFiveSecondsRemaining() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> first = rig.port.enqueueChat();
        CallHandle call = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(4));
        rig.executor.runNext();
        first.completeExceptionally(new ProviderFailure(
                ProviderFailure.Code.HTTP_RATE_LIMITED, Duration.ofSeconds(1)));

        assertFailure(join(call.stage()), FailureCode.PROVIDER_RATE_LIMITED);
        assertEquals(1, rig.port.chatCalls());
    }

    @Test
    void aiSec009RejectsUnapproved429RetryAfterWithoutASecondProviderCall() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> first = rig.port.enqueueChat();
        CallHandle call = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();
        first.completeExceptionally(new ProviderFailure(
                ProviderFailure.Code.HTTP_RATE_LIMITED, Duration.ofSeconds(3)));

        assertFailure(join(call.stage()), FailureCode.PROVIDER_RATE_LIMITED);
        assertEquals(1, rig.port.chatCalls());
    }

    @Test
    void aiSec009CancellationDuringRetryPreventsTheSecondProviderCall() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> first = rig.port.enqueueChat();
        CallHandle call = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();
        first.completeExceptionally(new ProviderFailure(ProviderFailure.Code.NETWORK_FAILURE));
        assertTrue(call.cancel());
        assertThrows(CancellationException.class, () -> join(call.stage()));
        rig.scheduler.advanceBy(Duration.ofMillis(250));
        assertEquals(1, rig.port.chatCalls());
    }

    @Test
    void aiSec020ExpiredDeadlinePerformsZeroChatCalls() {
        TestRig rig = readyRig();

        CompletionResult result = join(rig.core.complete(
                        new PreparedChat("system", "user"), rig.clock.instant())
                .stage());

        assertFailure(result, FailureCode.DEADLINE_EXCEEDED);
        assertEquals(0, rig.port.chatCalls());
    }

    @Test
    void aiSec020PerCallSchedulerRejectionPerformsZeroChatCalls() {
        TestRig rig = readyRig();
        rig.scheduler.rejectNewTasks();

        CompletionResult result = join(rig.core.complete(
                        new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10))
                .stage());

        assertFailure(result, FailureCode.DEADLINE_EXCEEDED);
        assertEquals(0, rig.port.chatCalls());
    }

    @Test
    void aiSec020NoChatIsDispatchedBeforeReadinessAndCallerCancellationDetaches() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> readinessStage = rig.port.enqueueModels();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(100));

        assertEquals(0, rig.port.chatCalls());
        assertTrue(result.cancel());
        assertThrows(CancellationException.class, () -> join(result.stage()));

        rig.executor.runNext();
        readinessStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.READY, rig.core.readiness());
        assertEquals(0, rig.port.chatCalls());
    }

    @Test
    void aiSec020DeadlineWhileWaitingForReadinessReturnsTimeoutAndNeverDispatchesChat() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> readinessStage = rig.port.enqueueModels();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        rig.scheduler.advanceBy(Duration.ofSeconds(10));
        readinessStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));

        assertFailure(join(result.stage()), FailureCode.DEADLINE_EXCEEDED);
        assertEquals(0, rig.port.chatCalls());
        assertEquals(Readiness.READY, rig.core.readiness());
    }

    @Test
    void aiSec006PostDispatchCancellationBestEffortCancelsUniqueStageAndIgnoresLateCompletion() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        assertTrue(result.cancel());
        assertTrue(providerStage.isCancelled());
        assertFalse(providerStage.complete(ProviderTestControls.completion("late-secret")));
        assertThrows(CancellationException.class, () -> join(result.stage()));
        assertEquals(Readiness.READY, rig.core.readiness());
    }

    @Test
    void aiSec020ProviderResponseWinsAndLaterCancellationHasNoEffect() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        providerStage.complete(ProviderTestControls.completion("winner"));

        assertEquals("winner", assertInstanceOf(Completion.class, join(result.stage())).content());
        assertFalse(result.cancel());
        assertFalse(providerStage.isCancelled());
    }

    @Test
    void aiSec020DeadlineAfterDispatchCancelsProviderAndIgnoresLateCompletion() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        rig.scheduler.advanceBy(Duration.ofSeconds(10));

        assertFailure(join(result.stage()), FailureCode.DEADLINE_EXCEEDED);
        assertTrue(providerStage.isCancelled());
        assertFalse(providerStage.complete(ProviderTestControls.completion("late")));
        assertEquals(Readiness.READY, rig.core.readiness());
    }

    @Test
    void aiSec016ProviderFailureMapsToInvalidOutputAndDoesNotDemoteReady() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        providerStage.completeExceptionally(new CompletionException(
                new ProviderFailure(ProviderFailure.Code.INVALID_ENVELOPE)));

        assertFailure(join(result.stage()), FailureCode.INVALID_OUTPUT);
        assertEquals(Readiness.READY, rig.core.readiness());
    }

    @Test
    void aiSec016TransportFailureNullResponseAndSyncThrowStaySafeAndDoNotDemoteReady() {
        TestRig failedStageRig = readyRig();
        CompletableFuture<ProviderCompletion> failedStage = failedStageRig.port.enqueueChat();
        CallHandle failedResult = failedStageRig.core.complete(
                new PreparedChat("system", "user"), failedStageRig.clock.instant().plusSeconds(10));
        failedStageRig.executor.runNext();
        failedStage.completeExceptionally(new IllegalStateException("secret-provider-error"));
        assertFailure(join(failedResult.stage()), FailureCode.PROVIDER_UNAVAILABLE);
        assertEquals(Readiness.READY, failedStageRig.core.readiness());

        TestRig nullResponseRig = readyRig();
        CompletableFuture<ProviderCompletion> nullResponseStage = nullResponseRig.port.enqueueChat();
        CallHandle nullResponse = nullResponseRig.core.complete(
                new PreparedChat("system", "user"), nullResponseRig.clock.instant().plusSeconds(10));
        nullResponseRig.executor.runNext();
        nullResponseStage.complete(null);
        assertFailure(join(nullResponse.stage()), FailureCode.PROVIDER_UNAVAILABLE);
        assertEquals(Readiness.READY, nullResponseRig.core.readiness());

        TestRig nullStageRig = readyRig();
        nullStageRig.port.returnNullChat();
        CallHandle nullStageHandle = nullStageRig.core.complete(
                new PreparedChat("system", "user"), nullStageRig.clock.instant().plusSeconds(10));
        nullStageRig.executor.runNext();
        CompletionResult nullStage = join(nullStageHandle.stage());
        assertFailure(nullStage, FailureCode.PROVIDER_UNAVAILABLE);
        assertEquals(Readiness.READY, nullStageRig.core.readiness());

        TestRig throwingRig = readyRig();
        throwingRig.port.throwOnChat();
        CallHandle thrownHandle = throwingRig.core.complete(
                new PreparedChat("system", "user"), throwingRig.clock.instant().plusSeconds(10));
        throwingRig.executor.runNext();
        CompletionResult thrown = join(thrownHandle.stage());
        assertFailure(thrown, FailureCode.PROVIDER_UNAVAILABLE);
        assertEquals(Readiness.READY, throwingRig.core.readiness());
    }

    @Test
    void reqAi006DeterministicFakeRoutesRawCompletionThroughProductionDecoder() {
        TestRig rig = readyRig();
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle result = rig.core.complete(
                new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        rig.executor.runNext();

        rig.port.completeChatFromRaw(providerStage, ("{\"choices\":[{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"decoded\"},\"finish_reason\":\"stop\"}]}")
                .getBytes(StandardCharsets.UTF_8));

        assertEquals("decoded", assertInstanceOf(Completion.class, join(result.stage())).content());
    }

    @Test
    void aiSec016PublicValueDiagnosticsAreRedacted() {
        PreparedChat chat = new PreparedChat("system-canary", "user-canary");
        ProviderChatRequest request = new TestRig().requestForDiagnostics(chat);
        ProviderCompletion completion = new ProviderCompletion("answer-canary", Optional.empty());

        String diagnostics = chat + " | " + request + " | " + completion;

        assertFalse(diagnostics.contains("system-canary"));
        assertFalse(diagnostics.contains("user-canary"));
        assertFalse(diagnostics.contains("answer-canary"));
        assertFalse(diagnostics.contains(ProviderTestControls.MODEL));
        assertTrue(diagnostics.contains("[REDACTED]"));
    }

    private static TestRig readyRig() {
        TestRig rig = new TestRig();
        CompletableFuture<ProviderModels> stage = rig.port.enqueueModels();
        CompletionStage<Readiness> readiness = rig.core.beginStartupReadinessCheck();
        rig.executor.runNext();
        stage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(Readiness.READY, join(readiness));
        return rig;
    }

    @Test
    void reqAi007RetryCountSurvivesEveryPostRetryTerminalFailure() {
        for (String terminal : java.util.List.of(
                "timeout", "executor", "synchronous", "shutdown", "queued-expiry")) {
            TestRig rig = readyRig();
            CompletableFuture<ProviderCompletion> first = rig.port.enqueueChat();
            CallHandle call = rig.core.complete(
                    new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
            rig.executor.runNext();
            first.completeExceptionally(new ProviderFailure(ProviderFailure.Code.NETWORK_FAILURE));
            FailureCode expected = FailureCode.PROVIDER_UNAVAILABLE;
            switch (terminal) {
                case "timeout" -> {
                    rig.port.enqueueChat();
                    rig.scheduler.advanceBy(Duration.ofMillis(250));
                    rig.executor.runNext();
                    rig.scheduler.advanceBy(Duration.ofSeconds(10));
                    expected = FailureCode.DEADLINE_EXCEEDED;
                }
                case "executor" -> {
                    rig.executor.rejectNewTasks();
                    rig.scheduler.advanceBy(Duration.ofMillis(250));
                }
                case "synchronous" -> {
                    rig.scheduler.advanceBy(Duration.ofMillis(250));
                    rig.port.throwOnChat();
                    rig.executor.runNext();
                }
                case "shutdown" -> {
                    rig.core.close();
                    expected = FailureCode.MODEL_UNAVAILABLE;
                }
                case "queued-expiry" -> {
                    rig.scheduler.advanceBy(Duration.ofMillis(250));
                    rig.clock.advance(Duration.ofSeconds(10));
                    rig.executor.runNext();
                    expected = FailureCode.DEADLINE_EXCEEDED;
                }
                default -> throw new AssertionError(terminal);
            }
            Failure failure = assertInstanceOf(Failure.class, join(call.stage()));
            assertEquals(expected, failure.code(), terminal);
            assertEquals(1, failure.retryCount(), terminal);
            rig.core.close();
        }
    }

    @Test
    void reqAi007UsageRecordingFailureCannotSwallowValidatedCompletion() {
        TestRig rig = readyRig();
        AiInvocationTelemetry.Invocation invocation = org.mockito.Mockito.mock(
                AiInvocationTelemetry.Invocation.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("telemetry-private-canary"))
                .when(invocation).recordUsage(1L, 2L, 3L);
        CompletableFuture<ProviderCompletion> providerStage = rig.port.enqueueChat();
        CallHandle call;
        try (AiInvocationTelemetry.Scope ignored = AiInvocationTelemetry.bind(invocation)) {
            call = rig.core.complete(new PreparedChat("system", "user"), rig.clock.instant().plusSeconds(10));
        }
        rig.executor.runNext();
        providerStage.complete(new ProviderCompletion("answer", Optional.of(new ProviderUsage(1L, 2L, 3L))));

        Completion completion = assertInstanceOf(Completion.class, join(call.stage()));
        assertEquals("answer", completion.content());
        assertEquals(3L, completion.usage().orElseThrow().totalTokens().orElseThrow());
        rig.scheduler.advanceBy(Duration.ofSeconds(11));
        assertInstanceOf(Completion.class, join(call.stage()));
        rig.core.close();
    }

    private static void assertFailure(CompletionResult result, FailureCode expected) {
        assertEquals(expected, assertInstanceOf(Failure.class, result).code());
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class TestRig {
        private final MutableClock clock = new MutableClock();
        private final ManualExecutor executor = new ManualExecutor();
        private final ManualScheduler scheduler = new ManualScheduler(clock);
        private final FakeProviderPort port = new FakeProviderPort();
        private final SocLlmProviderCore core = new SocLlmProviderCore(
                ProviderTestControls.availableConfiguration(), port, clock, executor, scheduler);

        private ProviderChatRequest requestForDiagnostics(PreparedChat chat) {
            CompletableFuture<ProviderModels> modelStage = port.enqueueModels();
            core.beginStartupReadinessCheck();
            executor.runNext();
            modelStage.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
            port.enqueueChat();
            core.complete(chat, clock.instant().plusSeconds(10));
            executor.runNext();
            return port.requests().getFirst();
        }
    }

    private static final class BlockingProviderPort implements SocLlmProviderPort {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CompletableFuture<ProviderCompletion> chatStage = new CompletableFuture<>();

        @Override
        public CompletableFuture<ProviderModels> listModels() {
            return CompletableFuture.completedFuture(ProviderTestControls.models(ProviderTestControls.MODEL));
        }

        @Override
        public CompletableFuture<ProviderCompletion> complete(ProviderChatRequest request) {
            entered.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("test provider invocation was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test provider invocation was interrupted", interrupted);
            }
            return chatStage;
        }
    }
}
