package sg.edu.nus.facilityflow.ai.provider;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import sg.edu.nus.facilityflow.ai.config.AiConfiguration;

/** Server-assembled output deny material; never serialized into the prompt. */
final class DisclosureDenyMaterial {
    private static final String UNAUTHORIZED_CONTEXT_CANARY =
            "facilityflow-unauthorized-context-canary";

    private final AiConfiguration.Available configuration;
    private final Set<String> normalizedValues;

    private DisclosureDenyMaterial(AiConfiguration.Available configuration, String... values) {
        this.configuration = configuration;
        this.normalizedValues = new HashSet<>();
        Arrays.stream(values).map(DisclosureDenyMaterial::normalize)
                .filter(value -> !value.isEmpty()).forEach(normalizedValues::add);
        normalizedValues.add(normalize("<request-data>"));
        normalizedValues.add(normalize("</request-data>"));
        normalizedValues.add(normalize(UNAUTHORIZED_CONTEXT_CANARY));
    }

    static DisclosureDenyMaterial forRequest(
            AiConfiguration.Available configuration, String template, String... requestValues) {
        String[] values = new String[requestValues.length + 1];
        values[0] = template;
        System.arraycopy(requestValues, 0, values, 1, requestValues.length);
        return new DisclosureDenyMaterial(configuration, values);
    }

    boolean matches(String candidate) {
        if (candidate == null || configuration.containsProviderSecret(candidate)) {
            return true;
        }
        String normalizedCandidate = normalize(candidate);
        return normalizedValues.stream().anyMatch(normalizedCandidate::contains);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .trim().toLowerCase(Locale.ROOT);
    }
}
