package sg.edu.nus.facilityflow.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderRuntimeFactory;
import sg.edu.nus.facilityflow.ai.technician.WorkPlanAssistant;

/**
 * Wires the optional provider runtime after startup-safe configuration
 * validation. No provider core or executor is created for disabled/unavailable
 * settings, and available readiness is started asynchronously.
 */
@Configuration(proxyBeanMethods = false)
public class AiProviderLifecycleConfiguration {

    @Bean(destroyMethod = "close")
    AiProviderLifecycle aiProviderLifecycle(AiConfiguration configuration) {
        if (configuration instanceof AiConfiguration.Available available) {
            return new RuntimeLifecycleView(SocLlmProviderRuntimeFactory.start(available));
        }
        if (configuration instanceof AiConfiguration.Disabled) {
            return AiProviderLifecycle.disabled();
        }
        return AiProviderLifecycle.unavailable();
    }

    @Bean
    WorkPlanAssistant workPlanAssistant(
            @Qualifier("aiProviderLifecycle") AiProviderLifecycle lifecycle) {
        if (lifecycle instanceof RuntimeLifecycleView runtime) {
            return WorkPlanAssistant.delegating(runtime.delegate());
        }
        return WorkPlanAssistant.unavailable();
    }

    /** Lifecycle-only adapter keeps the provider runtime out of Spring's lifecycle type lookup. */
    private static final class RuntimeLifecycleView implements AiProviderLifecycle {
        private final AiProviderRuntime runtime;

        private RuntimeLifecycleView(AiProviderRuntime runtime) {
            this.runtime = runtime;
        }

        private WorkPlanAssistant delegate() {
            return runtime;
        }

        @Override
        public boolean enabled() {
            return runtime.enabled();
        }

        @Override
        public State state() {
            return runtime.state();
        }

        @Override
        public java.util.concurrent.CompletionStage<State> readiness() {
            return runtime.readiness();
        }

        @Override
        public void close() {
            runtime.close();
        }
    }
}
