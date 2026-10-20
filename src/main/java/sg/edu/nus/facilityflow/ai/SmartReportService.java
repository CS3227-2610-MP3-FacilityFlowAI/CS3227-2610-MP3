package sg.edu.nus.facilityflow.ai;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResponse;
import sg.edu.nus.facilityflow.ai.requester.SmartReportResult;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import sg.edu.nus.facilityflow.requester.*;

/** REQ-RQ-012-017: form compatibility only; the shared role service owns AI execution. */
@Service
public class SmartReportService {
    public record Reply(String status, SmartReportResult suggestion, String message) { }
    private final CurrentAccountProvider accounts;
    private final RequesterRequestRepository requests;
    private final TransactionTemplate transactions;
    private final RequesterSmartReportService shared;

    public SmartReportService(CurrentAccountProvider accounts, RequesterRequestRepository requests,
            PlatformTransactionManager manager, RequesterSmartReportService shared) {
        this.accounts = accounts;
        this.requests = requests;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(5);
        this.shared = shared;
    }

    public Reply suggest(String displayId, String version, SmartReportInput input) {
        CurrentAccount actor = accounts.requireCurrentAccount();
        Long expected = transactions.execute(tx -> {
            requests.requireRequester(actor, false);
            if (displayId == null) return null;
            long parsed;
            try { parsed = Long.parseLong(version); }
            catch (RuntimeException invalid) { throw new StaleRequestException(); }
            long actual = requests.openVersion(actor.id(), displayId).orElseThrow(RequestUnavailableException::new);
            if (parsed < 0 || actual != parsed) throw new StaleRequestException();
            return parsed;
        });
        if (input == null || !input.valid()) return failure("AI_INVALID_REQUEST");
        String description = input.roughText().isEmpty() ? input.description()
                : input.description().isEmpty() || input.description().equals(input.roughText())
                        ? input.roughText() : input.roughText() + "\n\n" + input.description();
        if (description.codePointCount(0, description.length()) > 2000) return failure("AI_INVALID_REQUEST");
        String urgency = input.reportedUrgency().isEmpty() ? null
                : input.reportedUrgency().substring(0, 1) + input.reportedUrgency().substring(1).toLowerCase(Locale.ROOT);
        var data = new RequesterSmartReportInput(input.title(), description, input.location(), input.categoryId(), urgency);
        var response = (displayId == null ? shared.requestCreate(data) : shared.requestEdit(displayId, data, expected))
                .toCompletableFuture().join();
        if (response instanceof SmartReportResponse.Success success) {
            return new Reply("SUCCEEDED", success.result(),
                    "AI suggestion only. Review it before using the ordinary form. Nothing has been saved.");
        }
        return failure(((SmartReportResponse.Failure) response).code().name());
    }

    private static Reply failure(String status) {
        return new Reply(status, null,
                "AI suggestions are unavailable for this attempt (" + status
                        + "). Your manual form remains usable; keep your input and try again later.");
    }
}
