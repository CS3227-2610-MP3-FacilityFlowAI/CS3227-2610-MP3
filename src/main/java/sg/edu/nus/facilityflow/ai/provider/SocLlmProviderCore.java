package sg.edu.nus.facilityflow.ai.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderChatRequest;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderCompletion;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderModels;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderPort.ProviderUsage;

/** Package-private provider coordinator; no role-facing API is exposed here. */
final class SocLlmProviderCore implements AutoCloseable {
    private static final Duration NEGATIVE_CACHE_DURATION = Duration.ofSeconds(60);

    private final Object readinessLock = new Object();
    private final SocLlmProviderPort port;
    private final Clock clock;
    private final Executor providerDispatchExecutor;
    private final ScheduledExecutorService scheduler;
    private final String configuredModel;
    private final int maxOutputTokens;
    private final Duration readinessTimeout;

    private Readiness readiness = Readiness.UNVALIDATED;
    private Instant negativeUntil = Instant.MIN;
    private long nextGenerationId;
    private ReadinessGeneration currentGeneration;
    private boolean closed;

    SocLlmProviderCore(
            AiConfiguration.Available configuration,
            SocLlmProviderPort port,
            Clock clock,
            Executor providerDispatchExecutor,
            ScheduledExecutorService scheduler) {
        this(configuration, port, clock, providerDispatchExecutor, providerDispatchExecutor, scheduler);
    }

    SocLlmProviderCore(
            AiConfiguration.Available configuration,
            SocLlmProviderPort port,
            Clock clock,
            Executor discoveryExecutor,
            Executor providerDispatchExecutor,
            ScheduledExecutorService scheduler) {
        Objects.requireNonNull(discoveryExecutor, "discoveryExecutor");
        AiConfiguration.Available available = Objects.requireNonNull(configuration, "configuration");
        this.port = Objects.requireNonNull(port, "port");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.providerDispatchExecutor = Objects.requireNonNull(providerDispatchExecutor, "providerDispatchExecutor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.configuredModel = requireConfiguredModel(available.model());
        this.maxOutputTokens = requirePositive(available.maxOutputTokens());
        this.readinessTimeout = Duration.ofSeconds(requirePositive(available.timeoutSeconds()));
    }

    CompletionStage<Readiness> beginStartupReadinessCheck() {
        ReadinessGeneration generation;
        CompletionStage<Readiness> view;
        Readiness immediate = null;
        synchronized (readinessLock) {
            if (closed) {
                immediate = Readiness.NEGATIVE;
                generation = null;
                view = null;
            } else if (readiness == Readiness.READY) {
                immediate = Readiness.READY;
                generation = null;
                view = null;
            } else if (readiness == Readiness.CHECKING) {
                return currentGeneration.view();
            } else {
                Instant now = clock.instant();
                if (readiness == Readiness.NEGATIVE && now.isBefore(negativeUntil)) {
                    immediate = Readiness.NEGATIVE;
                    generation = null;
                    view = null;
                } else {
                    generation = new ReadinessGeneration(++nextGenerationId, safePlus(now, readinessTimeout));
                    currentGeneration = generation;
                    readiness = Readiness.CHECKING;
                    view = generation.view();
                }
            }
        }
        if (immediate != null) {
            return completedReadiness(immediate);
        }
        installReadinessTimerAndDispatch(generation);
        return view;
    }

    Readiness readiness() {
        synchronized (readinessLock) {
            return readiness;
        }
    }

    CallHandle complete(PreparedChat chat, Instant absoluteDeadline) {
        Objects.requireNonNull(chat, "chat");
        Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
        CallControl call = new CallControl(absoluteDeadline);
        if (!call.armDeadline()) {
            return call.handle;
        }

        CompletionStage<Readiness> readinessView;
        ReadinessGeneration reserved = null;
        Readiness immediate = null;
        boolean unavailable;
        synchronized (readinessLock) {
            unavailable = closed || call.isTerminal();
            if (unavailable) {
                readinessView = null;
            } else if (readiness == Readiness.CHECKING) {
                readinessView = currentGeneration.view();
            } else if (readiness == Readiness.READY) {
                immediate = Readiness.READY;
                readinessView = null;
            } else if (readiness == Readiness.NEGATIVE && clock.instant().isBefore(negativeUntil)) {
                immediate = Readiness.NEGATIVE;
                readinessView = null;
            } else {
                Instant now = clock.instant();
                reserved = new ReadinessGeneration(++nextGenerationId, safePlus(now, readinessTimeout));
                currentGeneration = reserved;
                readiness = Readiness.CHECKING;
                readinessView = reserved.view();
            }
        }
        if (reserved != null) {
            installReadinessTimerAndDispatch(reserved);
        }
        if (unavailable) {
            call.fail(FailureCode.MODEL_UNAVAILABLE);
            return call.handle;
        }
        if (immediate != null) {
            readinessView = completedReadiness(immediate);
        }
        CompletionStage<Readiness> finalReadinessView = readinessView;
        finalReadinessView.whenComplete((state, failure) -> {
            if (failure != null || state != Readiness.READY) {
                call.fail(FailureCode.MODEL_UNAVAILABLE);
            } else {
                dispatchChat(call, chat);
            }
        });
        return call.handle;
    }

    @Override
    public void close() {
        ReadinessGeneration generation;
        ScheduledFuture<?> timer;
        CompletableFuture<ProviderModels> providerStage;
        synchronized (readinessLock) {
            if (closed) {
                return;
            }
            closed = true;
            generation = currentGeneration;
            currentGeneration = null;
            timer = generation == null ? null : generation.timeoutTask;
            providerStage = generation == null ? null : generation.providerStage;
            if (generation != null && readiness == Readiness.CHECKING) {
                readiness = Readiness.NEGATIVE;
                negativeUntil = safePlus(clock.instant(), NEGATIVE_CACHE_DURATION);
            }
        }
        cancelTimer(timer);
        cancelProvider(providerStage);
        if (generation != null) {
            generation.shared.publish(Readiness.NEGATIVE);
        }
    }

    private void installReadinessTimerAndDispatch(ReadinessGeneration generation) {
        ScheduledFuture<?> scheduled;
        try {
            scheduled = scheduler.schedule(
                    () -> onReadinessTimeout(generation),
                    delayNanos(clock.instant(), generation.absoluteExpiry),
                    TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            transitionNegative(generation);
            return;
        }
        boolean install;
        synchronized (readinessLock) {
            install = ownsCheckingLocked(generation);
            if (install) {
                generation.timeoutTask = scheduled;
            }
        }
        if (!install) {
            cancelTimer(scheduled);
            return;
        }
        try {
            providerDispatchExecutor.execute(() -> runDiscovery(generation));
        } catch (RuntimeException ignored) {
            cancelTimer(scheduled);
            transitionNegative(generation);
        }
    }

    private void runDiscovery(ReadinessGeneration generation) {
        synchronized (readinessLock) {
            if (!ownsCheckingLocked(generation) || !clock.instant().isBefore(generation.absoluteExpiry)) {
                return;
            }
            generation.invocationReserved = true;
        }

        CompletableFuture<ProviderModels> providerStage;
        try {
            providerStage = port.listModels();
        } catch (RuntimeException ignored) {
            transitionNegative(generation);
            return;
        }
        boolean install;
        synchronized (readinessLock) {
            install = ownsCheckingLocked(generation) && generation.invocationReserved;
            if (install) {
                generation.providerStage = providerStage;
                generation.invocationReserved = false;
            }
        }
        if (!install) {
            cancelProvider(providerStage);
            return;
        }
        if (providerStage == null) {
            transitionNegative(generation);
            return;
        }
        providerStage.whenComplete((models, failure) -> onDiscoveryCompleted(generation, models, failure));
    }

    private void onDiscoveryCompleted(ReadinessGeneration generation, ProviderModels models, Throwable failure) {
        boolean late;
        ReadinessTransition transition;
        synchronized (readinessLock) {
            late = !ownsCheckingLocked(generation);
            if (late) {
                transition = null;
            } else if (failure != null || models == null
                    || !clock.instant().isBefore(generation.absoluteExpiry)
                    || !models.modelIds().contains(configuredModel)) {
                transition = reserveNegativeTransitionLocked(generation);
            } else {
                transition = reserveReadyTransitionLocked(generation);
            }
        }
        if (late) {
            cancelProvider(generation.providerStage);
        }
        publishReadinessTransition(transition);
    }

    private void onReadinessTimeout(ReadinessGeneration generation) {
        transitionNegative(generation);
    }

    private void transitionNegative(ReadinessGeneration generation) {
        ReadinessTransition transition;
        synchronized (readinessLock) {
            transition = reserveNegativeTransitionLocked(generation);
        }
        publishReadinessTransition(transition);
    }

    private ReadinessTransition reserveReadyTransitionLocked(ReadinessGeneration generation) {
        if (!ownsCheckingLocked(generation)) {
            return null;
        }
        currentGeneration = null;
        readiness = Readiness.READY;
        return new ReadinessTransition(generation, Readiness.READY, generation.timeoutTask);
    }

    private ReadinessTransition reserveNegativeTransitionLocked(ReadinessGeneration generation) {
        if (!ownsCheckingLocked(generation)) {
            return null;
        }
        currentGeneration = null;
        readiness = Readiness.NEGATIVE;
        negativeUntil = safePlus(clock.instant(), NEGATIVE_CACHE_DURATION);
        return new ReadinessTransition(generation, Readiness.NEGATIVE, generation.timeoutTask);
    }

    private void publishReadinessTransition(ReadinessTransition transition) {
        if (transition != null) {
            cancelTimer(transition.timer);
            transition.generation.shared.publish(transition.state);
        }
    }

    private boolean ownsCheckingLocked(ReadinessGeneration generation) {
        return !closed && readiness == Readiness.CHECKING && currentGeneration == generation;
    }

    private void dispatchChat(CallControl call, PreparedChat chat) {
        boolean reserve;
        synchronized (call.callLock) {
            reserve = !call.terminal && clock.instant().isBefore(call.absoluteDeadline);
            if (reserve) {
                call.invocationReserved = true;
            }
        }
        if (!reserve) {
            call.fail(FailureCode.DEADLINE_EXCEEDED);
            return;
        }
        try {
            providerDispatchExecutor.execute(() -> runChatInvocation(call, chat));
        } catch (RuntimeException ignored) {
            synchronized (call.callLock) {
                call.invocationReserved = false;
            }
            call.fail(FailureCode.PROVIDER_UNAVAILABLE);
        }
    }

    private void runChatInvocation(CallControl call, PreparedChat chat) {
        ScheduledFuture<?> expiredTimer = null;
        boolean expired = false;
        synchronized (call.callLock) {
            if (call.terminal || !call.invocationReserved) {
                return;
            }
            if (!clock.instant().isBefore(call.absoluteDeadline)) {
                call.terminal = true;
                call.invocationReserved = false;
                expiredTimer = call.deadlineTask;
                expired = true;
            }
        }
        if (expired) {
            cancelTimer(expiredTimer);
            call.result.publish(new Failure(FailureCode.DEADLINE_EXCEEDED));
            return;
        }
        CompletableFuture<ProviderCompletion> providerStage;
        try {
            providerStage = port.complete(new ProviderChatRequest(
                    chat.systemMessage, chat.userDataMessage, configuredModel,
                    maxOutputTokens, call.absoluteDeadline));
        } catch (RuntimeException ignored) {
            synchronized (call.callLock) {
                call.invocationReserved = false;
            }
            call.fail(FailureCode.PROVIDER_UNAVAILABLE);
            return;
        }
        boolean install;
        synchronized (call.callLock) {
            install = !call.terminal && call.invocationReserved;
            if (install) {
                call.providerStage = providerStage;
                call.invocationReserved = false;
            }
        }
        if (!install) {
            cancelProvider(providerStage);
            return;
        }
        if (providerStage == null) {
            call.fail(FailureCode.PROVIDER_UNAVAILABLE);
            return;
        }
        providerStage.whenComplete(call::providerCompleted);
    }

    private static CompletionStage<Readiness> completedReadiness(Readiness state) {
        ReadOnlyFuture<Readiness> result = new ReadOnlyFuture<>();
        result.publish(state);
        return result;
    }

    private static void cancelProvider(CompletableFuture<?> stage) {
        if (stage != null) {
            try {
                stage.cancel(true);
            } catch (RuntimeException ignored) {
                // Best effort only.
            }
        }
    }

    private static String requireConfiguredModel(String value) {
        Objects.requireNonNull(value, "configuredModel");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Configured model must be non-empty.");
        }
        return value;
    }

    private static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Configured numeric value must be positive.");
        }
        return value;
    }

    private static Instant safePlus(Instant start, Duration duration) {
        try {
            return start.plus(duration);
        } catch (RuntimeException ignored) {
            return Instant.MAX;
        }
    }

    private static long delayNanos(Instant now, Instant deadline) {
        Duration remaining = Duration.between(now, deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            return 0;
        }
        try {
            return remaining.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static void cancelTimer(ScheduledFuture<?> task) {
        if (task != null) {
            try {
                task.cancel(false);
            } catch (RuntimeException ignored) {
                // Best effort only.
            }
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    enum Readiness { UNVALIDATED, CHECKING, NEGATIVE, READY }

    enum FailureCode {
        MODEL_UNAVAILABLE,
        PROVIDER_UNAVAILABLE,
        PROVIDER_BAD_REQUEST,
        PROVIDER_AUTHENTICATION,
        PROVIDER_FORBIDDEN,
        PROVIDER_RATE_LIMITED,
        PROVIDER_SERVICE_UNAVAILABLE,
        PROVIDER_TIMEOUT,
        PROVIDER_CANCELLED,
        INVALID_OUTPUT,
        DEADLINE_EXCEEDED
    }

    sealed interface CompletionResult permits Completion, Failure {}

    static final class Completion implements CompletionResult {
        private final String content;
        private final Optional<Usage> usage;

        private Completion(String content, Optional<Usage> usage) {
            this.content = content;
            this.usage = usage;
        }

        public String content() { return content; }

        public Optional<Usage> usage() { return usage; }

        @Override
        public String toString() {
            return "Completion[content=[REDACTED], usage="
                    + (usage.isPresent() ? "[PRESENT]" : "[ABSENT]") + "]";
        }
    }

    record Failure(FailureCode code) implements CompletionResult {
        Failure { Objects.requireNonNull(code, "code"); }
    }

    record Usage(OptionalLong promptTokens, OptionalLong completionTokens, OptionalLong totalTokens) {
        Usage {
            Objects.requireNonNull(promptTokens, "promptTokens");
            Objects.requireNonNull(completionTokens, "completionTokens");
            Objects.requireNonNull(totalTokens, "totalTokens");
        }
    }

    static final class PreparedChat {
        private final String systemMessage;
        private final String userDataMessage;

        PreparedChat(String systemMessage, String userDataMessage) {
            this.systemMessage = requireMessage(systemMessage);
            this.userDataMessage = requireMessage(userDataMessage);
        }

        String systemMessage() { return systemMessage; }

        String userDataMessage() { return userDataMessage; }

        @Override
        public String toString() {
            return "PreparedChat[systemMessage=[REDACTED], userDataMessage=[REDACTED]]";
        }

        private static String requireMessage(String value) {
            Objects.requireNonNull(value, "message");
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Prepared chat messages must be non-empty.");
            }
            return value;
        }
    }

    static final class CallHandle {
        private final ReadOnlyFuture<CompletionResult> result;
        private final BooleanSupplier cancelAction;

        private CallHandle(ReadOnlyFuture<CompletionResult> result, BooleanSupplier cancelAction) {
            this.result = result;
            this.cancelAction = cancelAction;
        }

        CompletionStage<CompletionResult> stage() { return result; }

        boolean cancel() { return cancelAction.getAsBoolean(); }
    }

    private static final class ReadinessGeneration {
        private final long id;
        private final Instant absoluteExpiry;
        private final ReadOnlyFuture<Readiness> shared = new ReadOnlyFuture<>();
        private ScheduledFuture<?> timeoutTask;
        private CompletableFuture<ProviderModels> providerStage;
        private boolean invocationReserved;

        private ReadinessGeneration(long id, Instant absoluteExpiry) {
            this.id = id;
            this.absoluteExpiry = absoluteExpiry;
        }

        private CompletionStage<Readiness> view() { return shared; }

        @Override
        public String toString() { return "ReadinessGeneration[id=" + id + "]"; }
    }

    private record ReadinessTransition(ReadinessGeneration generation, Readiness state, ScheduledFuture<?> timer) {}

    /**
     * A future usable as a CompletionStage but with every caller-side mutation
     * operation disabled. The coordinator alone can publish or withdraw it.
     */
    private static final class ReadOnlyFuture<T> extends CompletableFuture<T> {
        private boolean publish(T value) {
            return super.complete(value);
        }

        private boolean withdraw() {
            return super.cancel(false);
        }

        @Override
        public boolean complete(T value) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public boolean completeExceptionally(Throwable exception) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public void obtrudeValue(T value) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public void obtrudeException(Throwable exception) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public CompletableFuture<T> completeAsync(
                Supplier<? extends T> supplier, Executor executor) {
            throw new UnsupportedOperationException("Provider stages are read-only.");
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return this;
        }
    }

    private final class CallControl {
        private final Object callLock = new Object();
        private final Instant absoluteDeadline;
        private final ReadOnlyFuture<CompletionResult> result = new ReadOnlyFuture<>();
        private final CallHandle handle = new CallHandle(result, this::cancelByCaller);
        private ScheduledFuture<?> deadlineTask;
        private CompletableFuture<ProviderCompletion> providerStage;
        private boolean invocationReserved;
        private boolean terminal;

        private CallControl(Instant absoluteDeadline) { this.absoluteDeadline = absoluteDeadline; }

        private boolean armDeadline() {
            Instant now = clock.instant();
            if (!now.isBefore(absoluteDeadline)) {
                fail(FailureCode.DEADLINE_EXCEEDED);
                return false;
            }
            ScheduledFuture<?> scheduled;
            try {
                scheduled = scheduler.schedule(this::deadlineExpired,
                        delayNanos(now, absoluteDeadline), TimeUnit.NANOSECONDS);
            } catch (RuntimeException ignored) {
                fail(FailureCode.DEADLINE_EXCEEDED);
                return false;
            }
            boolean install;
            synchronized (callLock) {
                install = !terminal;
                if (install) {
                    deadlineTask = scheduled;
                }
            }
            if (!install) {
                cancelTimer(scheduled);
            }
            return install;
        }

        private boolean isTerminal() {
            synchronized (callLock) { return terminal; }
        }

        private void providerCompleted(ProviderCompletion completion, Throwable failure) {
            FailureCode failureCode = null;
            CompletionResult success = null;
            ScheduledFuture<?> timer;
            synchronized (callLock) {
                if (terminal) {
                    return;
                }
                terminal = true;
                timer = deadlineTask;
                if (failure != null || completion == null) {
                    Throwable safeType = failure == null ? null : unwrap(failure);
                    failureCode = safeType instanceof ProviderFailure providerFailure
                            ? mapProviderFailure(providerFailure.code())
                            : FailureCode.PROVIDER_UNAVAILABLE;
                } else {
                    success = toCompletion(completion);
                }
            }
            cancelTimer(timer);
            result.publish(failureCode == null ? success : new Failure(failureCode));
        }

        private void deadlineExpired() {
            CompletableFuture<ProviderCompletion> stageToCancel;
            ScheduledFuture<?> timer;
            synchronized (callLock) {
                if (terminal) {
                    return;
                }
                terminal = true;
                stageToCancel = providerStage;
                timer = deadlineTask;
            }
            cancelTimer(timer);
            cancelProvider(stageToCancel);
            result.publish(new Failure(FailureCode.DEADLINE_EXCEEDED));
        }

        private boolean cancelByCaller() {
            CompletableFuture<ProviderCompletion> stageToCancel;
            ScheduledFuture<?> timer;
            synchronized (callLock) {
                if (terminal) {
                    return false;
                }
                terminal = true;
                stageToCancel = providerStage;
                timer = deadlineTask;
            }
            cancelTimer(timer);
            cancelProvider(stageToCancel);
            result.withdraw();
            return true;
        }

        private void fail(FailureCode code) {
            ScheduledFuture<?> timer;
            synchronized (callLock) {
                if (terminal) {
                    return;
                }
                terminal = true;
                timer = deadlineTask;
            }
            cancelTimer(timer);
            result.publish(new Failure(code));
        }

        private Completion toCompletion(ProviderCompletion providerCompletion) {
            Optional<Usage> usage = providerCompletion.usage().map(CallControl::toUsage);
            return new Completion(providerCompletion.content(), usage);
        }

        private static FailureCode mapProviderFailure(ProviderFailure.Code code) {
            return switch (code) {
                case HTTP_BAD_REQUEST -> FailureCode.PROVIDER_BAD_REQUEST;
                case HTTP_UNAUTHORIZED -> FailureCode.PROVIDER_AUTHENTICATION;
                case HTTP_FORBIDDEN -> FailureCode.PROVIDER_FORBIDDEN;
                case HTTP_RATE_LIMITED -> FailureCode.PROVIDER_RATE_LIMITED;
                case HTTP_SERVICE_UNAVAILABLE -> FailureCode.PROVIDER_SERVICE_UNAVAILABLE;
                case TIMEOUT -> FailureCode.PROVIDER_TIMEOUT;
                case CANCELLED -> FailureCode.PROVIDER_CANCELLED;
                case BODY_TOO_LARGE, BODY_UNREADABLE, INVALID_UTF8, MALFORMED_JSON,
                        INVALID_ENVELOPE -> FailureCode.INVALID_OUTPUT;
                case HTTP_UNEXPECTED, REDIRECT_REJECTED, UNSUPPORTED_CONTENT_ENCODING,
                        NETWORK_FAILURE, REQUEST_BUILD_FAILED -> FailureCode.PROVIDER_UNAVAILABLE;
            };
        }

        private static Usage toUsage(ProviderUsage usage) {
            return new Usage(optionalLong(usage.promptTokens()), optionalLong(usage.completionTokens()),
                    optionalLong(usage.totalTokens()));
        }

        private static OptionalLong optionalLong(Long value) {
            return value == null ? OptionalLong.empty() : OptionalLong.of(value);
        }
    }
}
