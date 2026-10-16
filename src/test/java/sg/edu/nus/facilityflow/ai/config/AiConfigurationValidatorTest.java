package sg.edu.nus.facilityflow.ai.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiConfigurationValidatorTest {

    private static final String NON_SECRET_KEY_MARKER = "configured-key-marker";
    private static final String MODEL_MARKER = "configured-model-marker";

    @Test
    void reqAi004DefaultsToDisabledWithDocumentedSafeLimits() {
        SocLlmProperties raw = new SocLlmProperties();

        AiConfiguration result = AiConfigurationValidator.validate(raw);

        assertInstanceOf(AiConfiguration.Disabled.class, result);
        assertEquals("false", raw.getEnabled());
        assertEquals(AiConfiguration.CANONICAL_BASE_URL.toString(), raw.getBaseUrl());
        assertEquals("", raw.getApiKey());
        assertEquals("", raw.getModel());
        assertEquals("30", raw.getTimeoutSeconds());
        assertEquals("800", raw.getMaxOutputTokens());
        assertEquals("5", raw.getMaxInFlight());
        assertEquals("5", raw.getRatePerMinute());
        assertEquals("30", raw.getRatePerHour());
    }

    @Test
    void reqAi004AcceptsEnabledConfigurationAtMinimumLimits() {
        SocLlmProperties raw = enabledConfiguration();
        setAllNumericSettings(raw, "1", "1", "1", "1", "1");

        AiConfiguration.Available available = assertInstanceOf(
                AiConfiguration.Available.class, AiConfigurationValidator.validate(raw));

        assertEquals(AiConfiguration.CANONICAL_BASE_URL, available.baseUrl());
        assertEquals(NON_SECRET_KEY_MARKER, available.apiKey());
        assertEquals(MODEL_MARKER, available.model());
        assertEquals(1, available.timeoutSeconds());
        assertEquals(1, available.maxOutputTokens());
        assertEquals(1, available.maxInFlight());
        assertEquals(1, available.ratePerMinute());
        assertEquals(1, available.ratePerHour());
    }

    @Test
    void reqAi003AndReqAi004AcceptConfiguredModelAndDefaultMaximumLimits() {
        SocLlmProperties raw = enabledConfiguration();
        raw.setApiKey("  " + NON_SECRET_KEY_MARKER + "  ");
        raw.setModel("  " + MODEL_MARKER + "  ");

        AiConfiguration.Available available = assertInstanceOf(
                AiConfiguration.Available.class, AiConfigurationValidator.validate(raw));

        assertEquals(NON_SECRET_KEY_MARKER, available.apiKey());
        assertEquals(MODEL_MARKER, available.model());
        assertEquals(30, available.timeoutSeconds());
        assertEquals(800, available.maxOutputTokens());
        assertEquals(5, available.maxInFlight());
        assertEquals(5, available.ratePerMinute());
        assertEquals(30, available.ratePerHour());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", " true ", "False", " FALSE "})
    void reqAi004ParsesBooleanCaseInsensitivelyAfterOuterTrim(String configured) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setEnabled(configured);

        AiConfiguration result = AiConfigurationValidator.validate(raw);

        if (configured.trim().equalsIgnoreCase("true")) {
            assertInstanceOf(AiConfiguration.Available.class, result);
        } else {
            assertInstanceOf(AiConfiguration.Disabled.class, result);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "yes", "1", "enabled", "true false", "attacker\ntrue"})
    void reqAi004MalformedBooleanFailsClosedWithoutThrowing(String configured) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setEnabled(configured);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertIssues(result, AiConfiguration.Issue.ENABLED_INVALID);
    }

    @ParameterizedTest
    @MethodSource("numericBoundaryViolations")
    void aiSec008RejectsZeroNegativeAndValuesAboveMandatoryMaxima(
            String setting, String value, AiConfiguration.Issue expectedIssue) {
        SocLlmProperties raw = enabledConfiguration();
        setNumericSetting(raw, setting, value);

        AiConfiguration result = AiConfigurationValidator.validate(raw);

        assertIssues(result, expectedIssue);
    }

    @ParameterizedTest
    @MethodSource("malformedNumericSettings")
    void reqAi004MalformedNumericsFailClosedWithoutThrowing(
            String setting, String value, AiConfiguration.Issue expectedIssue) {
        SocLlmProperties raw = enabledConfiguration();
        setNumericSetting(raw, setting, value);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertIssues(result, expectedIssue);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void reqAi004BlankCredentialAndModelAreBothRejected(String value) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setApiKey(value);
        raw.setModel(value);

        AiConfiguration result = AiConfigurationValidator.validate(raw);

        assertIssues(
                result,
                AiConfiguration.Issue.API_KEY_MISSING_OR_PLACEHOLDER,
                AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "placeholder",
        " CHANGE-ME ",
        "changeme",
        "replace-me",
        "replaceme",
        "todo",
        "example",
        "your_api_key",
        "your-model",
        "your model",
        "api_key_here",
        "model-here",
        "<insert-value>",
        "${SOCLAAS_SECRET}"
    })
    void reqAi004RepresentativePlaceholderCredentialAndModelAreBothRejected(String value) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setApiKey(value);
        raw.setModel(value);

        AiConfiguration result = AiConfigurationValidator.validate(raw);

        assertIssues(
                result,
                AiConfiguration.Issue.API_KEY_MISSING_OR_PLACEHOLDER,
                AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://soclaas-api.comp.nus.edu.sg/v1",
        "https://soclaas-api.comp.nus.edu.sg/v1/",
        "https://soclaas-api.comp.nus.edu.sg:443/v1",
        "https://soclaas-api.comp.nus.edu.sg:443/v1/"
    })
    void reqAi001AcceptsOnlyPermittedOfficialUrlFormsAndCanonicalizesThem(String configuredUrl) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setBaseUrl(configuredUrl);

        AiConfiguration.Available available = assertInstanceOf(
                AiConfiguration.Available.class, AiConfigurationValidator.validate(raw));

        assertEquals(URI.create("https://soclaas-api.comp.nus.edu.sg/v1"), available.baseUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://soclaas-api.comp.nus.edu.sg/v1",
        "https://example.invalid/v1",
        "https://soclaas-api.comp.nus.edu.sg/",
        "https://soclaas-api.comp.nus.edu.sg/v1/models",
        "https://user:password@soclaas-api.comp.nus.edu.sg/v1",
        "https://soclaas-api.comp.nus.edu.sg:444/v1",
        "https://soclaas-api.comp.nus.edu.sg/v1?redirect=attacker",
        "https://soclaas-api.comp.nus.edu.sg/v1#attacker",
        "https://[malformed/v1"
    })
    void reqAi001RejectsUnsafeOrMalformedProviderUrls(String configuredUrl) {
        SocLlmProperties raw = enabledConfiguration();
        raw.setBaseUrl(configuredUrl);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));

        assertIssues(result, AiConfiguration.Issue.BASE_URL_INVALID);
    }

    @Test
    void reqAi004AggregatesEveryIndependentConfigurationIssue() {
        SocLlmProperties raw = enabledConfiguration();
        raw.setBaseUrl("https://attacker.invalid/v1");
        raw.setApiKey("placeholder");
        raw.setModel("todo");
        setAllNumericSettings(raw, "31", "801", "6", "6", "31");

        AiConfiguration.Unavailable unavailable = assertInstanceOf(
                AiConfiguration.Unavailable.class, AiConfigurationValidator.validate(raw));

        assertEquals(EnumSet.of(
                        AiConfiguration.Issue.BASE_URL_INVALID,
                        AiConfiguration.Issue.API_KEY_MISSING_OR_PLACEHOLDER,
                        AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER,
                        AiConfiguration.Issue.TIMEOUT_SECONDS_INVALID,
                        AiConfiguration.Issue.MAX_OUTPUT_TOKENS_INVALID,
                        AiConfiguration.Issue.MAX_IN_FLIGHT_INVALID,
                        AiConfiguration.Issue.RATE_PER_MINUTE_INVALID,
                        AiConfiguration.Issue.RATE_PER_HOUR_INVALID),
                unavailable.issues());
    }

    @Test
    void reqAi004UnavailableIssuesAreImmutableAndDefensivelyCopied() {
        EnumSet<AiConfiguration.Issue> source = EnumSet.of(AiConfiguration.Issue.BASE_URL_INVALID);
        AiConfiguration.Unavailable unavailable = new AiConfiguration.Unavailable(source);

        source.add(AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER);

        assertEquals(Set.of(AiConfiguration.Issue.BASE_URL_INVALID), unavailable.issues());
        assertThrows(
                UnsupportedOperationException.class,
                () -> unavailable.issues().add(AiConfiguration.Issue.MODEL_MISSING_OR_PLACEHOLDER));
    }

    @Test
    void nfrSec002DiagnosticsAndValidationExceptionsDoNotExposeRawConfiguration() {
        String secret = "sensitive-key-attacker-marker";
        String model = "sensitive-model-attacker-marker";
        String unsafeUrl = "https://user:unsafe-password@attacker.invalid/v1?payload=raw-url-marker";
        String attackerInput = "raw-attacker-numeric-marker";
        SocLlmProperties raw = enabledConfiguration();
        raw.setBaseUrl(unsafeUrl);
        raw.setApiKey(secret);
        raw.setModel(model);
        raw.setTimeoutSeconds(attackerInput);

        AiConfiguration result = assertDoesNotThrow(() -> AiConfigurationValidator.validate(raw));
        String combinedDiagnostics = raw + " | " + result;

        assertContainsNone(combinedDiagnostics, secret, model, unsafeUrl, attackerInput, "unsafe-password", "raw-url-marker");

        IllegalArgumentException emptyIssues = assertThrows(
                IllegalArgumentException.class,
                () -> new AiConfiguration.Unavailable(Set.of()));
        assertContainsNone(emptyIssues.getMessage(), secret, model, unsafeUrl, attackerInput);
    }

    @Test
    void nfrSec002AvailableDiagnosticsRedactCredentialAndModel() {
        SocLlmProperties raw = enabledConfiguration();

        AiConfiguration.Available available = assertInstanceOf(
                AiConfiguration.Available.class, AiConfigurationValidator.validate(raw));
        String diagnostics = available.toString();

        assertContainsNone(diagnostics, NON_SECRET_KEY_MARKER, MODEL_MARKER);
        assertTrue(diagnostics.contains("apiKey=[REDACTED]"));
        assertTrue(diagnostics.contains("model=[PRESENT]"));
    }

    private static Stream<Arguments> numericBoundaryViolations() {
        return Stream.of(
                numericCases("timeoutSeconds", 30, AiConfiguration.Issue.TIMEOUT_SECONDS_INVALID),
                numericCases("maxOutputTokens", 800, AiConfiguration.Issue.MAX_OUTPUT_TOKENS_INVALID),
                numericCases("maxInFlight", 5, AiConfiguration.Issue.MAX_IN_FLIGHT_INVALID),
                numericCases("ratePerMinute", 5, AiConfiguration.Issue.RATE_PER_MINUTE_INVALID),
                numericCases("ratePerHour", 30, AiConfiguration.Issue.RATE_PER_HOUR_INVALID))
                .flatMap(stream -> stream);
    }

    private static Stream<Arguments> numericCases(
            String setting, int maximum, AiConfiguration.Issue expectedIssue) {
        return Stream.of(
                Arguments.of(setting, "0", expectedIssue),
                Arguments.of(setting, "-1", expectedIssue),
                Arguments.of(setting, Integer.toString(maximum + 1), expectedIssue));
    }

    private static Stream<Arguments> malformedNumericSettings() {
        return Stream.of(
                Arguments.of("timeoutSeconds", "not-a-number", AiConfiguration.Issue.TIMEOUT_SECONDS_INVALID),
                Arguments.of("maxOutputTokens", "1.5", AiConfiguration.Issue.MAX_OUTPUT_TOKENS_INVALID),
                Arguments.of("maxInFlight", "1 0", AiConfiguration.Issue.MAX_IN_FLIGHT_INVALID),
                Arguments.of("ratePerMinute", "999999999999", AiConfiguration.Issue.RATE_PER_MINUTE_INVALID),
                Arguments.of("ratePerHour", "", AiConfiguration.Issue.RATE_PER_HOUR_INVALID));
    }

    private static SocLlmProperties enabledConfiguration() {
        SocLlmProperties raw = new SocLlmProperties();
        raw.setEnabled("true");
        raw.setApiKey(NON_SECRET_KEY_MARKER);
        raw.setModel(MODEL_MARKER);
        return raw;
    }

    private static void setAllNumericSettings(
            SocLlmProperties raw,
            String timeoutSeconds,
            String maxOutputTokens,
            String maxInFlight,
            String ratePerMinute,
            String ratePerHour) {
        raw.setTimeoutSeconds(timeoutSeconds);
        raw.setMaxOutputTokens(maxOutputTokens);
        raw.setMaxInFlight(maxInFlight);
        raw.setRatePerMinute(ratePerMinute);
        raw.setRatePerHour(ratePerHour);
    }

    private static void setNumericSetting(SocLlmProperties raw, String setting, String value) {
        switch (setting) {
            case "timeoutSeconds" -> raw.setTimeoutSeconds(value);
            case "maxOutputTokens" -> raw.setMaxOutputTokens(value);
            case "maxInFlight" -> raw.setMaxInFlight(value);
            case "ratePerMinute" -> raw.setRatePerMinute(value);
            case "ratePerHour" -> raw.setRatePerHour(value);
            default -> throw new IllegalArgumentException("Unknown test setting: " + setting);
        }
    }

    private static void assertIssues(AiConfiguration result, AiConfiguration.Issue... expected) {
        AiConfiguration.Unavailable unavailable = assertInstanceOf(AiConfiguration.Unavailable.class, result);
        assertEquals(Set.copyOf(Arrays.asList(expected)), unavailable.issues());
    }

    private static void assertContainsNone(String text, String... forbiddenValues) {
        for (String forbidden : forbiddenValues) {
            assertFalse(text.contains(forbidden), "Diagnostics exposed forbidden raw configuration");
        }
    }
}
