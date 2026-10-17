package sg.edu.nus.facilityflow.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the explicit startup-safe AI configuration result (REQ-AI-004). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SocLlmProperties.class)
public class AiSettingsConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiSettingsConfiguration.class);

    @Bean
    AiConfiguration aiConfiguration(SocLlmProperties properties) {
        AiConfiguration configuration = AiConfigurationValidator.validate(properties);
        if (configuration instanceof AiConfiguration.Unavailable unavailable) {
            LOGGER.warn("SoCLaaS gateway configuration unavailable issues={}", unavailable.issues());
        } else if (configuration instanceof AiConfiguration.Disabled) {
            LOGGER.info("SoCLaaS gateway disabled (AI_ENABLED=false)");
        }
        return configuration;
    }
}
