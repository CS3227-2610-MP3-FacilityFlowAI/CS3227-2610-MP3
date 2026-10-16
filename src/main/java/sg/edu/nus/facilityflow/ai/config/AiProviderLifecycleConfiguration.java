package sg.edu.nus.facilityflow.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.edu.nus.facilityflow.ai.provider.SocLlmProviderRuntimeFactory;

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
            return SocLlmProviderRuntimeFactory.start(available);
        }
        if (configuration instanceof AiConfiguration.Disabled) {
            return AiProviderLifecycle.disabled();
        }
        return AiProviderLifecycle.unavailable();
    }
}
