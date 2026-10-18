package sg.edu.nus.facilityflow.ai.provider;

/** Opaque capability required to apply the provider credential. */
public final class ProviderCredentialCapability {
    private ProviderCredentialCapability() {}

    static ProviderCredentialCapability create() {
        return new ProviderCredentialCapability();
    }
}
