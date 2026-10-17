package sg.edu.nus.facilityflow.ai.config;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Startup-safe lifecycle view for the optional SoCLaaS provider runtime.
 *
 * <p>The interface deliberately exposes readiness only. Provider ports, HTTP
 * adapters, credentials and mutable coordination state remain behind the
 * provider seam.
 */
public interface AiProviderLifecycle extends AutoCloseable {

    enum State {
        DISABLED,
        UNAVAILABLE,
        CHECKING,
        NEGATIVE,
        READY
    }

    /** Returns whether a validated provider runtime was constructed. */
    boolean enabled();

    /** Returns the latest lifecycle state without blocking. */
    State state();

    /**
     * Returns the non-blocking readiness result for the startup check. The
     * returned stage is read-only from the provider coordinator's perspective.
     */
    CompletionStage<State> readiness();

    @Override
    void close();

    /** Creates the inert lifecycle used for deliberately disabled AI. */
    static AiProviderLifecycle disabled() {
        return new Inert(State.DISABLED);
    }

    /** Creates the inert lifecycle used for invalid/unavailable AI settings. */
    static AiProviderLifecycle unavailable() {
        return new Inert(State.UNAVAILABLE);
    }

    /** Small immutable no-work implementation for startup-safe configuration. */
    class Inert implements AiProviderLifecycle {
        private final State state;
        private final CompletionStage<State> readiness;

        protected Inert(State state) {
            this.state = Objects.requireNonNull(state, "state");
            this.readiness = CompletableFuture.completedFuture(state).minimalCompletionStage();
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public State state() {
            return state;
        }

        @Override
        public CompletionStage<State> readiness() {
            return readiness;
        }

        @Override
        public void close() {
            // No provider core, executor, timer or transport exists to close.
        }
    }
}
