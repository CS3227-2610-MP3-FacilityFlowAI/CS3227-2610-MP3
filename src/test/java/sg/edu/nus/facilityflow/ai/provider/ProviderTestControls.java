package sg.edu.nus.facilityflow.ai.provider;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;

final class ProviderTestControls {
    static final Instant START = Instant.parse("2026-10-16T00:00:00Z");
    static final String MODEL = "approved-model";

    private ProviderTestControls() {}

    static AiConfiguration.Available availableConfiguration() {
        return availableConfiguration(MODEL);
    }

    static AiConfiguration.Available availableConfiguration(String model) {
        try {
            Constructor<AiConfiguration.Available> constructor = AiConfiguration.Available.class
                    .getDeclaredConstructor(
                            URI.class,
                            String.class,
                            String.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    AiConfiguration.CANONICAL_BASE_URL,
                    "test-only-key",
                    model,
                    30,
                    321,
                    5,
                    5,
                    30);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to create validated test configuration", failure);
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock() {
            this(START);
        }

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        void set(Instant value) {
            instant = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    static final class ManualExecutor implements Executor {
        private final Queue<Runnable> queued = new ArrayDeque<>();
        private boolean reject;
        private int submissions;

        @Override
        public void execute(Runnable command) {
            submissions++;
            if (reject) {
                throw new RejectedExecutionException("test rejection");
            }
            queued.add(command);
        }

        void rejectNewTasks() {
            reject = true;
        }

        void runNext() {
            Runnable task = queued.poll();
            if (task == null) {
                throw new AssertionError("No queued executor task");
            }
            task.run();
        }

        void runAll() {
            while (!queued.isEmpty()) {
                runNext();
            }
        }

        int queuedCount() {
            return queued.size();
        }

        int submissions() {
            return submissions;
        }
    }

    static final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final MutableClock clock;
        private final AtomicLong sequence = new AtomicLong();
        private final List<ManualScheduledFuture<?>> tasks = new ArrayList<>();
        private boolean reject;
        private boolean shutdown;
        private int submissions;

        ManualScheduler(MutableClock clock) {
            this.clock = clock;
        }

        void rejectNewTasks() {
            reject = true;
        }

        void acceptNewTasks() {
            reject = false;
        }

        int submissions() {
            return submissions;
        }

        int activeTaskCount() {
            return (int) tasks.stream().filter(task -> !task.isDone()).count();
        }

        void advanceBy(Duration duration) {
            clock.advance(duration);
            runDueTasks();
        }

        void runDueTasks() {
            while (true) {
                ManualScheduledFuture<?> next = tasks.stream()
                        .filter(task -> !task.isDone() && !task.due.isAfter(clock.instant()))
                        .min(Comparator.comparing((ManualScheduledFuture<?> task) -> task.due)
                                .thenComparingLong(task -> task.sequence))
                        .orElse(null);
                if (next == null) {
                    return;
                }
                next.runTask();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return schedule(() -> {
                command.run();
                return null;
            }, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            submissions++;
            if (reject || shutdown) {
                throw new RejectedExecutionException("test scheduler rejection");
            }
            Duration duration = Duration.ofNanos(unit.toNanos(delay));
            ManualScheduledFuture<V> task = new ManualScheduledFuture<>(
                    clock, callable, clock.instant().plus(duration), sequence.incrementAndGet());
            tasks.add(task);
            return task;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            schedule(command, 0, TimeUnit.NANOSECONDS);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            tasks.forEach(task -> task.cancel(false));
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && activeTaskCount() == 0;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
    }

    private static final class ManualScheduledFuture<V> implements ScheduledFuture<V> {
        private final MutableClock clock;
        private final Callable<V> callable;
        private final Instant due;
        private final long sequence;
        private boolean cancelled;
        private boolean done;
        private V value;
        private Exception failure;

        private ManualScheduledFuture(MutableClock clock, Callable<V> callable, Instant due, long sequence) {
            this.clock = clock;
            this.callable = callable;
            this.due = due;
            this.sequence = sequence;
        }

        void runTask() {
            if (done) {
                return;
            }
            try {
                value = callable.call();
            } catch (Exception caught) {
                failure = caught;
            } finally {
                done = true;
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long nanos;
            try {
                nanos = Duration.between(clock.instant(), due).toNanos();
            } catch (ArithmeticException ignored) {
                nanos = due.isAfter(clock.instant()) ? Long.MAX_VALUE : Long.MIN_VALUE;
            }
            return unit.convert(nanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            done = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public V get() {
            if (!done) {
                throw new IllegalStateException("Test task has not run");
            }
            if (failure != null) {
                throw new AssertionError(failure);
            }
            return value;
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            return get();
        }
    }

    static final class FakeProviderPort implements SocLlmProviderPort {
        private final ProviderEnvelopeDecoder decoder = new ProviderEnvelopeDecoder();
        private final Queue<CompletableFuture<ProviderModels>> modelStages = new ArrayDeque<>();
        private final Queue<CompletableFuture<ProviderCompletion>> chatStages = new ArrayDeque<>();
        private final List<ProviderChatRequest> requests = new ArrayList<>();
        private boolean throwOnModels;
        private boolean nullModels;
        private boolean throwOnChat;
        private boolean nullChat;
        private int modelCalls;
        private int chatCalls;

        CompletableFuture<ProviderModels> enqueueModels() {
            CompletableFuture<ProviderModels> stage = new CompletableFuture<>();
            modelStages.add(stage);
            return stage;
        }

        CompletableFuture<ProviderCompletion> enqueueChat() {
            CompletableFuture<ProviderCompletion> stage = new CompletableFuture<>();
            chatStages.add(stage);
            return stage;
        }

        void throwOnModels() {
            throwOnModels = true;
        }

        void returnNullModels() {
            nullModels = true;
        }

        void throwOnChat() {
            throwOnChat = true;
        }

        void returnNullChat() {
            nullChat = true;
        }

        void completeModelsFromRaw(CompletableFuture<ProviderModels> stage, byte[] rawBody) {
            try {
                stage.complete(decoder.decodeModels(DecompressedBody.of(rawBody)));
            } catch (RuntimeException failure) {
                stage.completeExceptionally(failure);
            }
        }

        void completeChatFromRaw(CompletableFuture<ProviderCompletion> stage, byte[] rawBody) {
            try {
                stage.complete(decoder.decodeCompletion(DecompressedBody.of(rawBody)));
            } catch (RuntimeException failure) {
                stage.completeExceptionally(failure);
            }
        }

        @Override
        public CompletableFuture<ProviderModels> listModels() {
            modelCalls++;
            if (throwOnModels) {
                throw new IllegalStateException("provider canary must not escape");
            }
            if (nullModels) {
                return null;
            }
            CompletableFuture<ProviderModels> stage = modelStages.poll();
            return stage == null ? new CompletableFuture<>() : stage;
        }

        @Override
        public CompletableFuture<ProviderCompletion> complete(ProviderChatRequest request) {
            chatCalls++;
            requests.add(request);
            if (throwOnChat) {
                throw new IllegalStateException("provider canary must not escape");
            }
            if (nullChat) {
                return null;
            }
            CompletableFuture<ProviderCompletion> stage = chatStages.poll();
            return stage == null ? new CompletableFuture<>() : stage;
        }

        int modelCalls() {
            return modelCalls;
        }

        int chatCalls() {
            return chatCalls;
        }

        List<ProviderChatRequest> requests() {
            return List.copyOf(requests);
        }
    }

    static ProviderModels models(String... ids) {
        return new ProviderModels(Set.of(ids));
    }

    static ProviderCompletion completion(String content) {
        return new ProviderCompletion(content, Optional.empty());
    }
}
