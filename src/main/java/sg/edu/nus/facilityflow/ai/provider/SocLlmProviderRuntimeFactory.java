package sg.edu.nus.facilityflow.ai.provider;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;
import sg.edu.nus.facilityflow.ai.config.AiProviderRuntime;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle.State;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanContext;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanResponse;
import sg.edu.nus.facilityflow.ai.manager.TriageAssistant;
import sg.edu.nus.facilityflow.ai.manager.TriageContext;
import sg.edu.nus.facilityflow.ai.manager.TriageResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportAssistant;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCore.Readiness;

/**
 * Narrow application-wiring bridge for the package-private provider module.
 * Role and web packages must depend on {@link AiProviderLifecycle}, not this
 * factory or any provider implementation type.
 */
public final class SocLlmProviderRuntimeFactory {

    private SocLlmProviderRuntimeFactory() {}

    /**
     * Creates and starts an available provider runtime without waiting for
     * network readiness. The first provider call is dispatched asynchronously.
     */
    public static AiProviderRuntime start(AiConfiguration.Available configuration) {
        AiConfiguration.Available available = Objects.requireNonNull(configuration, "configuration");
        ExecutorService discovery = Executors.newSingleThreadExecutor(named("facilityflow-ai-discovery"));
        ExecutorService provider = Executors.newSingleThreadExecutor(named("facilityflow-ai-provider"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                named("facilityflow-ai-scheduler"));
        try {
            SocLlmHttpsProviderAdapter adapter = new SocLlmHttpsProviderAdapter(available);
            Clock clock = Clock.systemUTC();
            SocLlmProviderCore core = new SocLlmProviderCore(
                    available,
                    adapter,
                    clock,
                    discovery,
                    provider,
                    scheduler);
            CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
            WorkPlanAssistant assistant = new SocLlmWorkPlanAssistant(available, core, clock);
            SmartReportAssistant smartReport = new SocLlmSmartReportAssistant(available, core, clock);
            return new Runtime(core, readiness, assistant, smartReport,
                    TriageAssistant.unavailable(), discovery, provider, scheduler, true);
        } catch (RuntimeException failure) {
            shutdown(discovery);
            shutdown(provider);
            shutdown(scheduler);
            throw failure;
        }
    }

    /**
     * Package-private deterministic seam for provider-package tests. It never
     * creates threads or performs network work outside the supplied seams.
     */
    static AiProviderRuntime startForTests(
            AiConfiguration.Available configuration,
            SocLlmProviderPort port,
            Clock clock,
            Executor discovery,
            Executor provider,
            ScheduledExecutorService scheduler) {
        AiConfiguration.Available available = Objects.requireNonNull(configuration, "configuration");
        SocLlmProviderCore core = new SocLlmProviderCore(
                available,
                Objects.requireNonNull(port, "port"),
                Objects.requireNonNull(clock, "clock"),
                Objects.requireNonNull(discovery, "discovery"),
                Objects.requireNonNull(provider, "provider"),
                Objects.requireNonNull(scheduler, "scheduler"));
        CompletionStage<Readiness> readiness = core.beginStartupReadinessCheck();
        WorkPlanAssistant assistant = new SocLlmWorkPlanAssistant(available, core, clock);
        SmartReportAssistant smartReport = new SocLlmSmartReportAssistant(available, core, clock);
        return new Runtime(core, readiness, assistant, smartReport,
                TriageAssistant.unavailable(), discovery, provider, scheduler, false);
    }

    private static ThreadFactory named(String prefix) {
        return Thread.ofPlatform().daemon(true).name(prefix + "-", 0).factory();
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdownNow();
    }

    private static void shutdown(ScheduledExecutorService executor) {
        executor.shutdownNow();
    }

    private static final class Runtime implements AiProviderRuntime {
        private final SocLlmProviderCore core;
        private final CompletionStage<Readiness> readiness;
        private final WorkPlanAssistant assistant;
        private final SmartReportAssistant smartReportAssistant;
        private final TriageAssistant triageAssistant;
        private final Executor discovery;
        private final Executor provider;
        private final ScheduledExecutorService scheduler;
        private final boolean ownsResources;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Runtime(
                SocLlmProviderCore core,
                CompletionStage<Readiness> readiness,
                WorkPlanAssistant assistant,
                SmartReportAssistant smartReportAssistant,
                TriageAssistant triageAssistant,
                Executor discovery,
                Executor provider,
                ScheduledExecutorService scheduler,
                boolean ownsResources) {
            this.core = core;
            this.readiness = readiness;
            this.assistant = assistant;
            this.smartReportAssistant = smartReportAssistant;
            this.triageAssistant = triageAssistant;
            this.discovery = discovery;
            this.provider = provider;
            this.scheduler = scheduler;
            this.ownsResources = ownsResources;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public State state() {
            return map(core.readiness());
        }

        @Override
        public CompletionStage<State> readiness() {
            CompletionStage<State> mapped = readiness.thenApply(SocLlmProviderRuntimeFactory::map);
            return mapped.toCompletableFuture().minimalCompletionStage();
        }

        @Override
        public CompletionStage<WorkPlanResponse> request(WorkPlanContext context) {
            return assistant.request(context);
        }

        @Override
        public CompletionStage<SmartReportResponse> request(SmartReportContext context) {
            return smartReportAssistant.request(context);
        }

        @Override
        public CompletionStage<TriageResponse> request(TriageContext context) {
            return triageAssistant.request(context);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            core.close();
            if (ownsResources) {
                shutdown(discovery);
                shutdown(provider);
                shutdown(scheduler);
            }
        }

    }

    private static void shutdown(Executor executor) {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    private static State map(Readiness readiness) {
        return switch (Objects.requireNonNull(readiness, "readiness")) {
            case UNVALIDATED -> State.CHECKING;
            case CHECKING -> State.CHECKING;
            case NEGATIVE -> State.NEGATIVE;
            case READY -> State.READY;
        };
    }
}
