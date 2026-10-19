package sg.edu.nus.facilityflow.ai;

import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.requester.*;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import static sg.edu.nus.facilityflow.ai.AiGateway.Status.*;

/** REQ-RQ-012–017 / REQ-AI-007/008: advisory form assistance with a durable invocation lifecycle. */
@Service
public class SmartReportService {
    public record Reply(AiGateway.Status status, AiGateway.Suggestion suggestion, String message) { }
    private final CurrentAccountProvider accounts;
    private final RequesterRequestRepository requests;
    private final TransactionTemplate transactions;
    private final AiOperations operations;
    private final AiGateway gateway;
    private final AiInvocationTelemetry telemetry;

    public SmartReportService(CurrentAccountProvider accounts, RequesterRequestRepository requests,
            PlatformTransactionManager manager, AiOperations operations, AiGateway gateway,
            AiInvocationTelemetry telemetry) {
        this.accounts = accounts;
        this.requests = requests;
        transactions = new TransactionTemplate(manager);
        transactions.setTimeout(5);
        this.operations = operations;
        this.gateway = gateway;
        this.telemetry = telemetry;
    }

    public Reply suggest(String displayId, String version, SmartReportInput input) {
        var actor = accounts.requireCurrentAccount();
        authorize(actor, displayId, version);
        if (input == null || !input.valid()) return rejected(AI_INVALID_REQUEST);
        if (!SmartReportSchema.withinInputBudget(input)) return rejected(AI_LIMITED);
        if (!gateway.available()) return rejected(AI_UNAVAILABLE);
        AiOperations.Permit permit;
        try { permit = operations.acquire(actor.id(), gateway.timeoutSeconds()); }
        catch (RuntimeException unavailable) { return rejected(AI_UNAVAILABLE); }
        if (permit == null) return rejected(AI_LIMITED);
        long started = System.nanoTime();
        AiGateway.Result result = AiGateway.Result.failure(AI_UNAVAILABLE);
        AiInvocationTelemetry.Invocation invocation = null;
        AiInvocationTelemetry.Status disposition = null;
        try {
            invocation = telemetry.start(new AiInvocationTelemetry.StartRequest(actor.id(),
                    sg.edu.nus.facilityflow.operations.RequestCorrelationFilter.currentId(),
                    AiInvocationTelemetry.Feature.SMART_REPORT, gateway.model(), SmartReportSchema.VERSION));
            try {
                var context = transactions.execute(tx -> {
                    check(actor, displayId, version);
                    var categories = requests.categories();
                    String category = input.categoryId().isEmpty() ? null : categories.stream()
                            .filter(value -> value.id().toString().equalsIgnoreCase(input.categoryId()))
                            .map(RequesterRequests.Category::name).findFirst().orElse(null);
                    if (!input.categoryId().isEmpty() && category == null) return null;
                    return new AiGateway.Context(input.roughText(), input.title(), input.description(), input.location(), category,
                            input.reportedUrgency().isEmpty() ? null : titleCase(input.reportedUrgency()),
                            categories.stream().map(RequesterRequests.Category::name).toList());
                });
                result = context == null ? AiGateway.Result.failure(AI_INVALID_REQUEST)
                        : gateway.suggest(new AiGateway.Call("SMART_REPORT", actor.id(), invocation.correlationId(), context));
                // AI-SEC-028: revoke the entire result if authorization/state changed in flight.
                authorize(actor, displayId, version);
                if (result.status() == SUCCEEDED) {
                    var suggestion = result.suggestion();
                    boolean current = transactions.execute(tx -> {
                        check(actor, displayId, version);
                        return suggestion.suggestedCategory() == null || requests.categories().stream()
                                .anyMatch(value -> value.name().equals(suggestion.suggestedCategory()));
                    });
                    if (!current) {
                        disposition = AiInvocationTelemetry.Status.DISCARDED_ACCESS_CHANGED;
                        result = discarded(result, AI_INVALID_OUTPUT);
                    } else if (leaksAccount(suggestion, actor)) result = discarded(result, AI_INVALID_OUTPUT);
                }
            } catch (AccessDeniedException | RequestUnavailableException | StaleRequestException exception) {
                disposition = AiInvocationTelemetry.Status.DISCARDED_ACCESS_CHANGED;
                result = discarded(result, AI_UNAVAILABLE);
            } catch (DataAccessException exception) {
                result = discarded(result, AI_UNAVAILABLE);
            }
        } catch (RuntimeException unavailable) {
            if (invocation == null) AiFailureDiagnostics.persistenceFailed(null);
            result = discarded(result, AI_UNAVAILABLE);
        } finally {
            try { operations.release(permit); }
            catch (RuntimeException releaseFailure) { result = discarded(result, AI_UNAVAILABLE); disposition = null; }
        }
        if (invocation != null) {
            try {
                telemetry.finish(invocation, new AiInvocationTelemetry.Terminal(
                        disposition == null ? AiInvocationTelemetry.Status.valueOf(result.status().name()) : disposition,
                        (System.nanoTime() - started) / 1_000_000, result.retryCount(), result.inputTokens(),
                        result.outputTokens(), result.totalTokens()));
            } catch (RuntimeException persistenceFailure) {
                AiFailureDiagnostics.persistenceFailed(invocation);
                result = discarded(result, AI_UNAVAILABLE);
            }
        }
        return reply(result);
    }

    private static Reply rejected(AiGateway.Status status) {
        AiFailureDiagnostics.report(AiFailureDiagnostics.Feature.SMART_REPORT, status);
        return reply(AiGateway.Result.failure(status));
    }

    private static Reply reply(AiGateway.Result result) {
        return new Reply(result.status(), result.suggestion(), result.status() == SUCCEEDED
                ? "AI suggestion only. Review it before using the ordinary form. Nothing has been saved."
                : "AI suggestions are unavailable for this attempt (" + result.status() + "). Your manual form remains usable; keep your input and try again later.");
    }

    private void authorize(CurrentAccount actor, String displayId, String version) {
        transactions.executeWithoutResult(tx -> check(actor, displayId, version));
    }

    private void check(CurrentAccount actor, String displayId, String version) {
        requests.requireRequester(actor, false);
        if (displayId == null) return;
        long expected;
        try { expected = Long.parseLong(version); }
        catch (NumberFormatException exception) { throw new StaleRequestException(); }
        var actual = requests.openVersion(actor.id(), displayId).orElseThrow(RequestUnavailableException::new);
        if (expected < 0 || actual != expected) throw new StaleRequestException();
    }

    private static String titleCase(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean leaksAccount(AiGateway.Suggestion suggestion, CurrentAccount actor) {
        String text = suggestion.toString().toLowerCase(java.util.Locale.ROOT);
        return text.contains(actor.id().toString())
                || (!actor.username().isBlank() && text.contains(actor.username().toLowerCase(java.util.Locale.ROOT)));
    }

    private static AiGateway.Result discarded(AiGateway.Result result, AiGateway.Status status) {
        return new AiGateway.Result(status, null, result.retryCount(), result.inputTokens(), result.outputTokens(), result.totalTokens());
    }
}
