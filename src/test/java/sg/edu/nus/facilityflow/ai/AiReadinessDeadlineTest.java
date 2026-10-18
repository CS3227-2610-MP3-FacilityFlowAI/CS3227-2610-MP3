package sg.edu.nus.facilityflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle.State;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanFailureCode;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;
import sg.edu.nus.facilityflow.manager.ManagerTriageRepository;
import sg.edu.nus.facilityflow.manager.ManagerTriageResponse;
import sg.edu.nus.facilityflow.manager.ManagerTriageService;
import sg.edu.nus.facilityflow.manager.TriageCandidateRanker;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportInput;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportRepository;
import sg.edu.nus.facilityflow.requester.RequesterSmartReportService;
import sg.edu.nus.facilityflow.technician.TechnicianWorkPlanRepository;
import sg.edu.nus.facilityflow.technician.TechnicianWorkPlanService;

class AiReadinessDeadlineTest {
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("55000000-0000-0000-0000-000000000001");

    @Test
    void aiSec008CallerTimeoutDoesNotMutateOrCancelSharedReadiness() {
        Timer timer = new Timer();
        CompletableFuture<State> shared = new CompletableFuture<>();
        AiReadinessDeadline wait = new AiReadinessDeadline(timer.scheduler, Runnable::run);
        var first = wait.await(shared, NOW.plusSeconds(1), CLOCK);
        var second = wait.await(shared, NOW.plusSeconds(2), CLOCK);
        timer.tasks.get(0).run();

        assertEquals(AiReadinessDeadline.Outcome.TIMED_OUT, first.toCompletableFuture().join());
        assertFalse(shared.isDone());
        assertFalse(second.toCompletableFuture().isDone());
        shared.complete(State.READY);
        assertEquals(AiReadinessDeadline.Outcome.READY, second.toCompletableFuture().join());
        assertEquals(AiReadinessDeadline.Outcome.TIMED_OUT, first.toCompletableFuture().join());
        verify(timer.futures.get(0)).cancel(false);
        verify(timer.futures.get(1)).cancel(false);
    }

    @Test
    void aiSec008ExpiryAndSchedulingRejectionFailClosedWithoutWaiting() {
        Timer timer = new Timer();
        CompletableFuture<State> shared = new CompletableFuture<>();
        AiReadinessDeadline wait = new AiReadinessDeadline(timer.scheduler, Runnable::run);
        assertEquals(AiReadinessDeadline.Outcome.TIMED_OUT,
                wait.await(shared, NOW, CLOCK).toCompletableFuture().join());
        assertTrue(timer.tasks.isEmpty());
        when(timer.scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new RejectedExecutionException("private-canary"));
        assertEquals(AiReadinessDeadline.Outcome.UNAVAILABLE,
                wait.await(shared, NOW.plusSeconds(1), CLOCK).toCompletableFuture().join());
        assertFalse(shared.isDone());
    }

    @Test
    void reqAi006FailedReadinessIsUnavailableAndCancelsCallerTimer() {
        Timer timer = new Timer();
        CompletableFuture<State> shared = new CompletableFuture<>();
        var result = new AiReadinessDeadline(timer.scheduler, Runnable::run)
                .await(shared, NOW.plusSeconds(1), CLOCK);
        shared.completeExceptionally(new IllegalStateException("private-canary"));
        assertEquals(AiReadinessDeadline.Outcome.UNAVAILABLE, result.toCompletableFuture().join());
        verify(timer.futures.getFirst()).cancel(false);
    }

    @ParameterizedTest
    @EnumSource(AccountRole.class)
    void aiSec008AllRoleWaitsTimeoutReleasePermitAndIgnoreLateReadiness(AccountRole role) {
        Timer timer = new Timer();
        CompletableFuture<State> shared = new CompletableFuture<>();
        AiProviderLifecycle lifecycle = mock(AiProviderLifecycle.class);
        when(lifecycle.enabled()).thenReturn(true);
        when(lifecycle.readiness()).thenReturn(shared);
        IdentityAuthorization authorization = mock(IdentityAuthorization.class);
        when(authorization.requireRoleSnapshot(role)).thenReturn(new CurrentAccountSnapshot(ACTOR, role, 7));
        AiInvocationCoordinator coordinator = new AiInvocationCoordinator(1, 5, 30, CLOCK);
        AtomicInteger providerCalls = new AtomicInteger();
        AiInvocationTelemetry telemetry = mock(AiInvocationTelemetry.class);
        var invocation = new AiInvocationTelemetry.Invocation(UUID.randomUUID(), NOW);
        when(telemetry.start(any())).thenReturn(invocation);
        AiReadinessDeadline wait = new AiReadinessDeadline(timer.scheduler, Runnable::run);

        CompletionStage<?> response = switch (role) {
            case REQUESTER -> {
                RequesterSmartReportRepository repository = mock(RequesterSmartReportRepository.class);
                var eligible = new RequesterSmartReportRepository.Eligibility(ACTOR, null, null, null);
                when(repository.probeCreate(any())).thenReturn(Optional.of(eligible));
                when(repository.loadContext(any(), any()))
                        .thenReturn(mock(RequesterSmartReportRepository.ContextSnapshot.class));
                yield new RequesterSmartReportService(authorization, new AiAccessGuard(authorization),
                        coordinator, lifecycle, repository, context -> {
                            providerCalls.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE));
                        }, telemetry, new AiConfiguration.Disabled(), CLOCK, wait)
                        .requestCreate(new RequesterSmartReportInput("Leaking unit", null, null, null, null));
            }
            case TECHNICIAN -> new TechnicianWorkPlanService(
                    authorization, new AiAccessGuard(authorization), coordinator, lifecycle,
                    mock(TechnicianWorkPlanRepository.class), context -> {
                        providerCalls.incrementAndGet();
                        return CompletableFuture.completedFuture(
                                WorkPlanResponse.failure(WorkPlanFailureCode.AI_UNAVAILABLE));
                    }, telemetry, new AiConfiguration.Disabled(), CLOCK, wait).request("FF-000051");
            case FACILITIES_MANAGER -> {
                ManagerTriageRepository repository = mock(ManagerTriageRepository.class);
                when(repository.probe(eq("FF-000051"), any())).thenReturn(Optional.of(
                        new ManagerTriageRepository.Eligibility(UUID.randomUUID(), "FF-000051", 1)));
                when(repository.loadContext(any()))
                        .thenReturn(mock(ManagerTriageRepository.ContextSnapshot.class));
                yield new ManagerTriageService(authorization, new AiAccessGuard(authorization),
                        coordinator, lifecycle, repository, context -> {
                            providerCalls.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    TriageResponse.failure(TriageFailureCode.AI_UNAVAILABLE));
                        }, new TriageCandidateRanker(), telemetry, new AiConfiguration.Disabled(), CLOCK, wait)
                        .request("FF-000051");
            }
        };
        assertFalse(response.toCompletableFuture().isDone());
        assertTrue(coordinator.tryAcquire(ACTOR).isEmpty());
        verify(timer.scheduler).schedule(any(Runnable.class), eq(TimeUnit.SECONDS.toNanos(30)),
                eq(TimeUnit.NANOSECONDS));
        timer.tasks.getFirst().run();

        Object value = response.toCompletableFuture().join();
        String code = switch (value) {
            case SmartReportResponse.Failure failed -> failed.code().name();
            case WorkPlanResponse.Failure failed -> failed.code().name();
            case ManagerTriageResponse.Failure failed -> failed.code().name();
            default -> throw new AssertionError("Expected timeout failure");
        };
        assertEquals("AI_TIMEOUT", code);
        var nextPermit = coordinator.tryAcquire(ACTOR);
        assertTrue(nextPermit.isPresent(), "Readiness expiry must release the admitted slot");
        nextPermit.orElseThrow().release();
        shared.complete(State.READY);
        assertEquals(0, providerCalls.get());
        verify(telemetry).finish(eq(invocation), org.mockito.ArgumentMatchers.argThat(
                terminal -> terminal.status() == AiInvocationTelemetry.Status.AI_TIMEOUT));
    }

    @Test
    void aiSec008BlockedTimeoutContinuationCannotDelayAnotherCallersDeadline() throws Exception {
        Timer timer = new Timer();
        AiReadinessDeadline wait = new AiReadinessDeadline(timer.scheduler);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var first = wait.await(new CompletableFuture<>(), NOW.plusSeconds(1), CLOCK)
                .thenRun(() -> {
                    entered.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("Blocked timeout continuation was not released");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                });
        var second = wait.await(new CompletableFuture<>(), NOW.plusSeconds(2), CLOCK);
        try {
            timer.tasks.get(0).run();
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertFalse(first.toCompletableFuture().isDone());
            timer.tasks.get(1).run();
            assertEquals(AiReadinessDeadline.Outcome.TIMED_OUT,
                    second.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            first.toCompletableFuture().get(1, TimeUnit.SECONDS);
        }
    }

    private static final class Timer {
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final List<Runnable> tasks = new ArrayList<>();
        private final List<ScheduledFuture<?>> futures = new ArrayList<>();

        private Timer() {
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.NANOSECONDS)))
                    .thenAnswer(call -> {
                        tasks.add(call.getArgument(0));
                        ScheduledFuture<?> future = mock(ScheduledFuture.class);
                        futures.add(future);
                        return future;
                    });
        }
    }
}
