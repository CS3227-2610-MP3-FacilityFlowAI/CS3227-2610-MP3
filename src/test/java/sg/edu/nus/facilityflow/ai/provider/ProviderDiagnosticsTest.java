package sg.edu.nus.facilityflow.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import sg.edu.nus.facilityflow.ai.config.AiProviderLifecycle;
import sg.edu.nus.facilityflow.ai.config.AiProviderRuntime;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.FakeProviderPort;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualExecutor;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.ManualScheduler;
import sg.edu.nus.facilityflow.ai.provider.ProviderTestControls.MutableClock;
import sg.edu.nus.facilityflow.ai.requester.SmartReportContext;
import sg.edu.nus.facilityflow.ai.requester.SmartReportFailureCode;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;

class ProviderDiagnosticsTest {
    private static final String CANARY = "private-content-key-prompt-output-canary";
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private AiProviderRuntime runtime;
    private FakeProviderPort port;
    private ManualExecutor discovery;
    private ManualExecutor provider;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ProviderDiagnostics.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MutableClock clock = new MutableClock();
        discovery = new ManualExecutor();
        provider = new ManualExecutor();
        port = new FakeProviderPort();
        runtime = SocLlmProviderRuntimeFactory.startForTests(
                ProviderTestControls.availableConfiguration(), port, clock, discovery, provider,
                new ManualScheduler(clock));
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void aiSec020MissingModelLogsDiscoveryReasonWithoutModelNames() {
        var models = port.enqueueModels();
        discovery.runNext();
        models.complete(ProviderTestControls.models(CANARY));

        assertEquals(AiProviderLifecycle.State.NEGATIVE, runtime.state());
        assertLog("READINESS", "MODEL_NOT_FOUND");
        assertSanitized();
    }

    @Test
    void aiSec017DiscoveryEnvelopeFailureLogsOriginalSafeCode() {
        var models = port.enqueueModels();
        discovery.runNext();
        models.completeExceptionally(new CompletionException(
                new ProviderFailure(ProviderFailure.Code.INVALID_ENVELOPE)));

        assertEquals(AiProviderLifecycle.State.NEGATIVE, runtime.state());
        assertLog("READINESS", "INVALID_ENVELOPE");
        assertSanitized();
    }

    @Test
    void aiSec017ProviderAuthenticationFailureLogsCodeAndReturnsConfigurationCategory() {
        ready();
        var chat = port.enqueueChat();
        var response = runtime.request(context());
        provider.runNext();
        chat.completeExceptionally(new ProviderFailure(ProviderFailure.Code.HTTP_UNAUTHORIZED));

        var failure = assertInstanceOf(SmartReportResponse.Failure.class,
                response.toCompletableFuture().join());
        assertEquals(SmartReportFailureCode.AI_CONFIGURATION_ERROR, failure.code());
        assertTrue(failure.message().contains("authentication"));
        assertLog("COMPLETION", "HTTP_UNAUTHORIZED");
        assertSanitized();
    }

    @Test
    void aiSec017MalformedOutputLogsValidationStageWithoutResponseContent() {
        ready();
        var chat = port.enqueueChat();
        var response = runtime.request(context());
        provider.runNext();
        chat.complete(ProviderTestControls.completion("{invalid-json:" + CANARY));

        var failure = assertInstanceOf(SmartReportResponse.Failure.class,
                response.toCompletableFuture().join());
        assertEquals(SmartReportFailureCode.AI_INVALID_OUTPUT, failure.code());
        assertTrue(failure.message().contains("invalid format"));
        assertLog("SMART_REPORT_OUTPUT", "INVALID_OUTPUT");
        assertSanitized();
    }

    @Test
    void aiSec017UnexpectedProviderExceptionDoesNotLeakMessageOrStack() {
        ready();
        var chat = port.enqueueChat();
        var response = runtime.request(context());
        provider.runNext();
        chat.completeExceptionally(new IllegalStateException(CANARY));

        assertEquals(SmartReportFailureCode.AI_UNAVAILABLE,
                assertInstanceOf(SmartReportResponse.Failure.class,
                        response.toCompletableFuture().join()).code());
        assertLog("COMPLETION", "INTERNAL_FAILURE");
        assertSanitized();
    }

    private void ready() {
        CompletableFuture<SocLlmProviderPort.ProviderModels> models = port.enqueueModels();
        discovery.runNext();
        models.complete(ProviderTestControls.models(ProviderTestControls.MODEL));
        assertEquals(AiProviderLifecycle.State.READY, runtime.state());
    }

    @Test
    void aiSec017TransportReasonsNeverLogExceptionMessagesOrStacks() {
        var connection = new java.net.ConnectException(CANARY);
        connection.initCause(new java.nio.channels.UnresolvedAddressException());
        var failures = List.of(
                new javax.net.ssl.SSLHandshakeException(CANARY),
                new java.net.UnknownHostException(CANARY),
                connection,
                new java.net.ConnectException(CANARY),
                new java.io.IOException(CANARY),
                new IllegalStateException(CANARY));
        var reasons = List.of("TLS_FAILURE", "DNS_FAILURE", "DNS_FAILURE",
                "CONNECTION_FAILURE", "HTTP_IO_FAILURE", "INTERNAL_FAILURE");
        for (int index = 0; index < failures.size(); index++) {
            appender.list.clear();
            ProviderDiagnostics.reportTransportFailure(new CompletionException(failures.get(index)));
            assertLog("TRANSPORT", reasons.get(index));
            assertSanitized();
        }
    }

    private static SmartReportContext context() {
        return new SmartReportContext("Leak report", CANARY, "Block A", null, null, List.of("HVAC"));
    }

    private void assertLog(String stage, String reason) {
        assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("stage=" + stage)
                        && message.contains("reason=" + reason)));
    }

    private void assertSanitized() {
        assertFalse(appender.list.isEmpty());
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(CANARY));
            assertFalse(event.getFormattedMessage().contains("test-only-key"));
            assertEquals(null, event.getThrowableProxy());
        }
    }
}
