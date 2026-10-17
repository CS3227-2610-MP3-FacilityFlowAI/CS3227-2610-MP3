package sg.edu.nus.facilityflow.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;

/** Bounds each caller's readiness wait without cancelling or mutating shared readiness (AI-SEC-008). */
public final class AiReadinessDeadline {
    public enum Outcome { READY, UNAVAILABLE, TIMED_OUT }

    private static final AiReadinessDeadline SYSTEM = new AiReadinessDeadline(() -> SystemHolder.SCHEDULER);
    private final Supplier<ScheduledExecutorService> scheduler;
    private final Executor timeoutExecutor;

    public AiReadinessDeadline(ScheduledExecutorService scheduler) {
        this(scheduler, AiReadinessDeadline::dispatchTimeout);
    }

    public AiReadinessDeadline(ScheduledExecutorService scheduler, Executor timeoutExecutor) {
        Objects.requireNonNull(scheduler, "scheduler");
        this.scheduler = () -> scheduler;
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    private AiReadinessDeadline(Supplier<ScheduledExecutorService> scheduler) {
        this.scheduler = scheduler;
        this.timeoutExecutor = AiReadinessDeadline::dispatchTimeout;
    }

    public static AiReadinessDeadline system() {
        return SYSTEM;
    }

    public CompletionStage<Outcome> await(
            CompletionStage<AiProviderLifecycle.State> readiness, Instant deadline, Clock clock) {
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        long remainingNanos;
        try {
            remainingNanos = Duration.between(clock.instant(), deadline).toNanos();
        } catch (ArithmeticException overflow) {
            remainingNanos = clock.instant().isBefore(deadline) ? Long.MAX_VALUE : 0;
        }
        if (remainingNanos <= 0) {
            return CompletableFuture.completedStage(Outcome.TIMED_OUT);
        }
        ScheduledFuture<?> timer;
        try {
            timer = scheduler.get().schedule(() -> {
                try {
                    timeoutExecutor.execute(() -> result.complete(Outcome.TIMED_OUT));
                } catch (RuntimeException dispatchFailure) {
                    result.complete(Outcome.UNAVAILABLE);
                }
            },
                    remainingNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException schedulerFailure) {
            return CompletableFuture.completedStage(Outcome.UNAVAILABLE);
        }
        result.whenComplete((outcome, failure) -> timer.cancel(false));
        try {
            readiness.whenComplete((state, failure) -> result.complete(
                    !clock.instant().isBefore(deadline) ? Outcome.TIMED_OUT
                            : failure == null && state == AiProviderLifecycle.State.READY
                                    ? Outcome.READY : Outcome.UNAVAILABLE));
        } catch (RuntimeException readinessFailure) {
            result.complete(Outcome.UNAVAILABLE);
        }
        return result.minimalCompletionStage();
    }

    private static void dispatchTimeout(Runnable task) {
        Thread.ofVirtual().name("ai-readiness-timeout").start(task);
    }

    private static final class SystemHolder {
        private static final ScheduledExecutorService SCHEDULER = scheduler();

        private static ScheduledExecutorService scheduler() {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "ai-readiness-deadline");
                thread.setDaemon(true);
                return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
            return executor;
        }
    }
}
