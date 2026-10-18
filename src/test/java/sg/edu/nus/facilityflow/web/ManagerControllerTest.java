package sg.edu.nus.facilityflow.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import sg.edu.nus.facilityflow.identity.Account;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.manager.ManagerAuditFilters;
import sg.edu.nus.facilityflow.manager.ManagerRequestDraft;
import sg.edu.nus.facilityflow.manager.ManagerRequestFilters;
import sg.edu.nus.facilityflow.manager.ManagerValidationException;
import sg.edu.nus.facilityflow.manager.ManagerViews;

class ManagerControllerTest {
    @Test
    @DisplayName("REQ-MGR-003 / REQ-SH-010: validation failure retains the complete on-behalf draft")
    void retainsDraftAfterValidationFailure() {
        var views = new FailingViews(new ManagerValidationException(Map.of("requesterId", "Choose an active Requester.")));
        var controller = new ManagerController(views);
        var draft = draft();
        var model = new ConcurrentModel();

        assertEquals("manager/create", controller.create(principal(), draft, model, new RedirectAttributesModelMap()));
        assertSame(draft, model.getAttribute("draft"));
        assertEquals("Choose an active Requester.", ((Map<?, ?>) model.getAttribute("errors")).get("requesterId"));
    }

    @Test
    @DisplayName("REQ-MGR-003 / REQ-SH-010/014: storage failure retains the complete draft with safe retry guidance")
    void retainsDraftAfterStorageFailure() {
        var views = new FailingViews(new DataRetrievalFailureException("synthetic database detail"));
        var controller = new ManagerController(views);
        var draft = draft();
        var model = new ConcurrentModel();

        assertEquals("manager/create", controller.create(principal(), draft, model, new RedirectAttributesModelMap()));
        assertSame(draft, model.getAttribute("draft"));
        var errors = (Map<?, ?>) model.getAttribute("errors");
        assertTrue(errors.get("save").toString().contains("retained"));
        assertTrue(errors.values().stream().noneMatch(value -> value.toString().contains("synthetic database detail")));
    }

    @Test
    @DisplayName("REQ-MGR-003 / REQ-SH-010: option-load failure keeps the Manager form available with safe guidance")
    void keepsFormAvailableWhenOptionsCannotLoad() {
        var views = new FailingViews(new UnsupportedOperationException(),
                new DataRetrievalFailureException("synthetic option detail"));
        var controller = new ManagerController(views);
        var model = new ConcurrentModel();

        assertEquals("manager/create", controller.newRequest(principal(), model));
        assertTrue(((Map<?, ?>) model.getAttribute("errors")).containsKey("save"));
        assertEquals(List.of(), ((ManagerViews.CreationOptions) model.getAttribute("options")).requesters());
    }

    private static ManagerRequestDraft draft() {
        return new ManagerRequestDraft(UUID.randomUUID().toString(), "Leaking pipe",
                "Water is leaking below the kitchen sink.", "Block A", UUID.randomUUID().toString(), "HIGH");
    }

    private static CurrentAccountPrincipal principal() {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        return CurrentAccountPrincipal.from(new Account(UUID.randomUUID(), "manager-test", "Manager Test",
                "synthetic", AccountRole.FACILITIES_MANAGER, true, 0, now, now));
    }

    private static final class FailingViews implements ManagerViews {
        private final RuntimeException failure;
        private final RuntimeException optionsFailure;

        private FailingViews(RuntimeException failure) {
            this(failure, null);
        }

        private FailingViews(RuntimeException failure, RuntimeException optionsFailure) {
            this.failure = failure;
            this.optionsFailure = optionsFailure;
        }

        @Override public Overview overview() { throw new UnsupportedOperationException(); }
        @Override public RequestPage requests(ManagerRequestFilters filters) { throw new UnsupportedOperationException(); }
        @Override public AuditPage audit(ManagerAuditFilters filters) { throw new UnsupportedOperationException(); }
        @Override public CreationOptions creationOptions() {
            if (optionsFailure != null) throw optionsFailure;
            return new CreationOptions(List.of(), List.of());
        }
        @Override public String createOnBehalf(ManagerRequestDraft draft) { throw failure; }
    }
}
