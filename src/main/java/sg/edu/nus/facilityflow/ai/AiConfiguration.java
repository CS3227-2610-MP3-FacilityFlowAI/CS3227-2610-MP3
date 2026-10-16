package sg.edu.nus.facilityflow.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class AiConfiguration {
    @Bean
    @ConditionalOnMissingBean(AiTransport.class)
    AiTransport unavailableTransport() {
        // Step 5 supplies the fake-tested contract; live SoCLaaS readiness/transport belongs to step 6.
        return new AiTransport() {
            public boolean available() { return false; }
            public String model() { return null; }
            public Response complete(String system, String user, int maximum, java.time.Duration remaining) {
                throw new IllegalStateException("AI unavailable");
            }
        };
    }
}
