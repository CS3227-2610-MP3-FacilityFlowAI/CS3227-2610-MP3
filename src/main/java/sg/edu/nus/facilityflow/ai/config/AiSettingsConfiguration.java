package sg.edu.nus.facilityflow.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the explicit startup-safe AI configuration result (REQ-AI-004). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SocLlmProperties.class)
public class AiSettingsConfiguration {

    @Bean
    AiConfiguration aiConfiguration(SocLlmProperties properties) {
        return AiConfigurationValidator.validate(properties);
    }
}
