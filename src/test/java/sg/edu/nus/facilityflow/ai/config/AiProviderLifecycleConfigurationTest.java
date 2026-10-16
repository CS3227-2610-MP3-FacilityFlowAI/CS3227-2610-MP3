package sg.edu.nus.facilityflow.ai.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Requirement tests for startup-safe Iteration 2C provider lifecycle wiring. */
class AiProviderLifecycleConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiSettingsConfiguration.class, AiProviderLifecycleConfiguration.class);

    @Test
    @DisplayName("REQ-AI-004/AI-SEC-020 disabled configuration creates only inert lifecycle")
    void disabledConfigurationCreatesNoProviderRuntimeOrWork() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());

            AiProviderLifecycle lifecycle = context.getBean(AiProviderLifecycle.class);
            assertFalse(lifecycle.enabled());
            assertEquals(AiProviderLifecycle.State.DISABLED, lifecycle.state());
            assertEquals(AiProviderLifecycle.State.DISABLED, await(lifecycle.readiness()));
            assertInstanceOf(AiProviderLifecycle.Inert.class, lifecycle);

            assertNoProviderBeans(context);
            assertNoExecutorOrSchedulerBeans(context);
            assertDoesNotThrow(lifecycle::close);
            assertDoesNotThrow(lifecycle::close);
        });
    }

    @Test
    @DisplayName("REQ-AI-004/AI-SEC-020 unavailable configuration creates no provider runtime or work")
    void unavailableConfigurationCreatesNoProviderRuntimeOrWork() {
        contextRunner
                .withPropertyValues(
                        "facilityflow.ai.enabled=true",
                        "facilityflow.ai.base-url=https://attacker.invalid/v1",
                        "facilityflow.ai.api-key=placeholder",
                        "facilityflow.ai.model=todo")
                .run(context -> {
                    assertNull(context.getStartupFailure());

                    AiConfiguration.Unavailable configuration = assertInstanceOf(
                            AiConfiguration.Unavailable.class, context.getBean(AiConfiguration.class));
                    assertTrue(configuration.issues().contains(AiConfiguration.Issue.BASE_URL_INVALID));

                    AiProviderLifecycle lifecycle = context.getBean(AiProviderLifecycle.class);
                    assertFalse(lifecycle.enabled());
                    assertEquals(AiProviderLifecycle.State.UNAVAILABLE, lifecycle.state());
                    assertEquals(AiProviderLifecycle.State.UNAVAILABLE, await(lifecycle.readiness()));
                    assertInstanceOf(AiProviderLifecycle.Inert.class, lifecycle);

                    assertNoProviderBeans(context);
                    assertNoExecutorOrSchedulerBeans(context);
                    assertDoesNotThrow(lifecycle::close);
                    assertDoesNotThrow(lifecycle::close);
                });
    }

    @Test
    @DisplayName("REQ-AI-004 no-work lifecycle readiness is terminal and cannot be replayed")
    void noWorkLifecycleReadinessIsCompletedAndStable() {
        AiProviderLifecycle disabled = AiProviderLifecycle.disabled();
        AiProviderLifecycle unavailable = AiProviderLifecycle.unavailable();

        assertEquals(AiProviderLifecycle.State.DISABLED, await(disabled.readiness()));
        assertEquals(AiProviderLifecycle.State.DISABLED, await(disabled.readiness()));
        assertEquals(AiProviderLifecycle.State.UNAVAILABLE, await(unavailable.readiness()));
        assertEquals(AiProviderLifecycle.State.UNAVAILABLE, await(unavailable.readiness()));
    }

    private static AiProviderLifecycle.State await(CompletionStage<AiProviderLifecycle.State> stage) {
        try {
            return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("inert lifecycle readiness did not complete", failure);
        }
    }

    private static void assertNoProviderBeans(org.springframework.context.ApplicationContext context) {
        assertEquals(0, context.getBeansOfType(AiProviderLifecycle.class).values().stream()
                .filter(lifecycle -> lifecycle.enabled())
                .count(), "disabled/unavailable settings must not construct a provider runtime");
        for (String beanName : context.getBeanDefinitionNames()) {
            assertFalse(beanName.toLowerCase().contains("socllmprovidercore"),
                    "provider core must not be a Spring bean for no-work configuration");
            assertFalse(beanName.toLowerCase().contains("socllmprovideradapter"),
                    "provider adapter must not be a Spring bean for no-work configuration");
        }
    }

    private static void assertNoExecutorOrSchedulerBeans(org.springframework.context.ApplicationContext context) {
        for (String beanName : context.getBeanDefinitionNames()) {
            String lower = beanName.toLowerCase();
            assertFalse(lower.contains("executor") || lower.contains("scheduler"),
                    "no executor/timer bean may be created when AI is not available");
        }
    }
}
