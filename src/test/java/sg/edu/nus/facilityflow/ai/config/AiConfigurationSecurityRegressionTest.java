package sg.edu.nus.facilityflow.ai.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Fresh-verifier regressions for the configuration security fixes (AI-SEC-027). */
class AiConfigurationSecurityRegressionTest {

    private static final String VALID_API_KEY = "fresh-verifier-key";
    private static final String VALID_MODEL = "fresh-verifier-model";

    @ParameterizedTest(name = "{0} rejects ISO control U+{1}")
    @MethodSource("scalarFieldsAndIsoControls")
    void aiSec019RejectsEveryIsoControlWithStableIssueAndRedactedDiagnostics(
            ScalarField field, int codePoint) {
        String rawValue = "private-prefix" + Character.toString(codePoint) + "private-suffix";
        SocLlmProperties raw = enabledConfiguration();
        field.set(raw, rawValue);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertOnlyIssue(result, field.issue());
        assertRedacted(rawValue, raw, result);
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("scalarFieldsAndMalformedSurrogates")
    void aiSec019RejectsUnpairedSurrogatesWithStableIssueAndRedactedDiagnostics(
            ScalarField field, String description, String rawValue) {
        SocLlmProperties raw = enabledConfiguration();
        field.set(raw, rawValue);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertOnlyIssue(result, field.issue());
        assertRedacted(rawValue, raw, result);
    }

    @ParameterizedTest
    @MethodSource("scalarFields")
    void aiSec019AcceptsWellFormedSupplementaryUnicodeScalarValues(ScalarField field) {
        String validSurrogatePair = "configured-\uD83D\uDEE0-value";
        SocLlmProperties raw = enabledConfiguration();
        field.set(raw, validSurrogatePair);

        AiConfiguration.Available available = assertInstanceOf(
                AiConfiguration.Available.class, AiConfigurationValidator.validate(raw));

        assertEquals(validSurrogatePair, field.read(available));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "dummy",
        "dummy-key",
        "dummy_key",
        "test-key",
        "test_key",
        "undefined",
        "null",
        "not-set",
        "not_set",
        "tbd"
    })
    void reqAi004RejectsAdditionalPlaceholderTokensWithoutEchoingThem(String placeholder) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setApiKey(placeholder);
        raw.setModel(placeholder);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertIssues(
                result,
                AiConfiguration.Issue.API_KEY_MISSING_OR_PLACEHOLDER,
                AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER);
        assertRedacted(placeholder, raw, result);
    }

    @Test
    void aiSec019RawPropertiesTypeIsNotPublic() {
        assertFalse(Modifier.isPublic(SocLlmProperties.class.getModifiers()));
    }

    @Test
    void aiSec019ValidatedApiKeyAccessorIsNotPublic() throws NoSuchMethodException {
        Method apiKeyAccessor = AiConfiguration.Available.class.getDeclaredMethod("apiKey");

        assertFalse(Modifier.isPublic(apiKeyAccessor.getModifiers()));
    }

    @ParameterizedTest(name = "rejects provider URI case {0}")
    @MethodSource("unsafeProviderUris")
    void reqAi001RejectsUriConfusionAndEmbeddedControlsWithoutEchoingInput(
            String description, String configuredUrl) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setBaseUrl(configuredUrl);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertOnlyIssue(result, AiConfiguration.Issue.BASE_URL_INVALID);
        assertRedacted(configuredUrl, raw, result);
    }

    private static Stream<Arguments> scalarFieldsAndIsoControls() {
        return scalarFields().flatMap(field -> isoControlCodePoints()
                .mapToObj(codePoint -> Arguments.of(field, codePoint)));
    }

    private static IntStream isoControlCodePoints() {
        return IntStream.concat(IntStream.rangeClosed(0x00, 0x1F), IntStream.rangeClosed(0x7F, 0x9F));
    }

    private static Stream<Arguments> scalarFieldsAndMalformedSurrogates() {
        return scalarFields().flatMap(field -> Stream.of(
                Arguments.of(field, "lone high surrogate", "private-prefix\uD83Dprivate-suffix"),
                Arguments.of(field, "lone low surrogate", "private-prefix\uDEE0private-suffix"),
                Arguments.of(field, "reversed surrogate pair", "private-prefix\uDEE0\uD83Dprivate-suffix"),
                Arguments.of(field, "high surrogate at end", "private-prefix\uD83D"),
                Arguments.of(field, "low surrogate at start", "\uDEE0private-suffix")));
    }

    private static Stream<ScalarField> scalarFields() {
        return Stream.of(ScalarField.API_KEY, ScalarField.MODEL);
    }

    private static Stream<Arguments> unsafeProviderUris() {
        return Stream.of(
                Arguments.of("percent-encoded host label", "https://soclaas-api%2ecomp.nus.edu.sg/v1"),
                Arguments.of("percent-encoded path character", "https://soclaas-api.comp.nus.edu.sg/v%31"),
                Arguments.of("percent-encoded path separator", "https://soclaas-api.comp.nus.edu.sg/v1%2f"),
                Arguments.of("trailing-dot host", "https://soclaas-api.comp.nus.edu.sg./v1"),
                Arguments.of("parent dot segment", "https://soclaas-api.comp.nus.edu.sg/v1/../v1"),
                Arguments.of("current dot segment", "https://soclaas-api.comp.nus.edu.sg/./v1"),
                Arguments.of("trailing dot segment", "https://soclaas-api.comp.nus.edu.sg/v1/."),
                Arguments.of("backslash before path", "https://soclaas-api.comp.nus.edu.sg\\v1"),
                Arguments.of("backslash in path", "https://soclaas-api.comp.nus.edu.sg/v1\\models"),
                Arguments.of("opaque URI", "https:soclaas-api.comp.nus.edu.sg/v1"),
                Arguments.of("scheme-relative URI", "//soclaas-api.comp.nus.edu.sg/v1"),
                Arguments.of("carriage return", "https://soclaas-api.comp.nus.edu.sg/v1\rprivate"),
                Arguments.of("line feed", "https://soclaas-api.comp.nus.edu.sg/v1\nprivate"),
                Arguments.of("NUL", "https://soclaas-api.comp.nus.edu.sg/v1\u0000private"),
                Arguments.of("percent-encoded control", "https://soclaas-api.comp.nus.edu.sg/v1%00"));
    }

    private static SocLlmProperties enabledConfiguration() {
        SocLlmProperties raw = new SocLlmProperties();
        raw.setEnabled("true");
        raw.setApiKey(VALID_API_KEY);
        raw.setModel(VALID_MODEL);
        return raw;
    }

    private static void assertOnlyIssue(AiConfiguration result, AiConfiguration.Issue expected) {
        assertIssues(result, expected);
    }

    private static void assertIssues(AiConfiguration result, AiConfiguration.Issue... expected) {
        AiConfiguration.Unavailable unavailable = assertInstanceOf(AiConfiguration.Unavailable.class, result);
        assertEquals(Set.of(expected), unavailable.issues());
    }

    private static void assertRedacted(
            String rawInput, SocLlmProperties raw, AiConfiguration result) {
        String diagnostics = raw + " | " + result;
        assertFalse(diagnostics.contains(rawInput), "Diagnostics reproduced raw configuration input");
        assertFalse(diagnostics.contains("private-prefix"), "Diagnostics reproduced raw configuration input");
        assertFalse(diagnostics.contains("private-suffix"), "Diagnostics reproduced raw configuration input");
    }

    private enum ScalarField {
        API_KEY(AiConfiguration.Issue.API_KEY_MISSING_OR_PLACEHOLDER) {
            @Override
            void set(SocLlmProperties raw, String value) {
                raw.setApiKey(value);
            }

            @Override
            String read(AiConfiguration.Available available) {
                return available.apiKey();
            }
        },
        MODEL(AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER) {
            @Override
            void set(SocLlmProperties raw, String value) {
                raw.setModel(value);
            }

            @Override
            String read(AiConfiguration.Available available) {
                return available.model();
            }
        };

        private final AiConfiguration.Issue issue;

        ScalarField(AiConfiguration.Issue issue) {
            this.issue = issue;
        }

        AiConfiguration.Issue issue() {
            return issue;
        }

        abstract void set(SocLlmProperties raw, String value);

        abstract String read(AiConfiguration.Available available);
    }
}
