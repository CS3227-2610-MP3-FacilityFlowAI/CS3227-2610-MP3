package sg.edu.nus.facilityflow.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AiConfiguration {
    @Bean
    @ConditionalOnMissingBean(AiTransport.class)
    AiTransport soClaasTransport(@Value("${SOCLAAS_BASE_URL:https://soclaas-api.comp.nus.edu.sg/v1}") String baseUrl,
            @Value("${SOCLAAS_API_KEY:}") String key, @Value("${SOCLAAS_MODEL:}") String model) {
        return new SoClaasTransport(baseUrl, key, model);
    }
}
