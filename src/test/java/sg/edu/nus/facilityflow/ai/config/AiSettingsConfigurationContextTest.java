package sg.edu.nus.facilityflow.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiSettingsConfigurationContextTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(AiSettingsConfiguration.class);

    @Test
    void reqAi004SpringContextStartsWithAiDisabledAndNoProviderOrDatabaseConfiguration() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertInstanceOf(AiConfiguration.Disabled.class, context.getBean(AiConfiguration.class));
            assertEquals("false", context.getBean(SocLlmProperties.class).getEnabled());
        });
    }

    @Test
    void reqAi004SpringContextBindsValidStringSettingsWithoutProviderAccess() {
        contextRunner
                .withPropertyValues(
                        "facilityflow.ai.enabled=true",
                        "facilityflow.ai.base-url=https://soclaas-api.comp.nus.edu.sg:443/v1/",
                        "facilityflow.ai.api-key=context-key-marker",
                        "facilityflow.ai.model=context-model-marker",
                        "facilityflow.ai.timeout-seconds=12",
                        "facilityflow.ai.max-output-tokens=400",
                        "facilityflow.ai.max-in-flight=3",
                        "facilityflow.ai.rate-per-minute=2",
                        "facilityflow.ai.rate-per-hour=20")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AiConfiguration.Available available = assertInstanceOf(
                            AiConfiguration.Available.class, context.getBean(AiConfiguration.class));
                    assertEquals(AiConfiguration.CANONICAL_BASE_URL, available.baseUrl());
                    assertEquals("context-model-marker", available.model());
                    assertEquals(12, available.timeoutSeconds());
                    assertEquals(400, available.maxOutputTokens());
                    assertEquals(3, available.maxInFlight());
                    assertEquals(2, available.ratePerMinute());
                    assertEquals(20, available.ratePerHour());
                });
    }

    @Test
    void reqAi004MalformedBoundStringsKeepCoreContextRunningAndMarkAiUnavailable() {
        contextRunner
                .withPropertyValues(
                        "facilityflow.ai.enabled=true",
                        "facilityflow.ai.base-url=https://attacker.invalid/v1",
                        "facilityflow.ai.api-key=placeholder",
                        "facilityflow.ai.model=todo",
                        "facilityflow.ai.timeout-seconds=attacker-timeout",
                        "facilityflow.ai.max-output-tokens=801",
                        "facilityflow.ai.max-in-flight=0",
                        "facilityflow.ai.rate-per-minute=-1",
                        "facilityflow.ai.rate-per-hour=not-a-number")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AiConfiguration.Unavailable unavailable = assertInstanceOf(
                            AiConfiguration.Unavailable.class, context.getBean(AiConfiguration.class));
                    assertEquals(8, unavailable.issues().size());
                });
    }
}
