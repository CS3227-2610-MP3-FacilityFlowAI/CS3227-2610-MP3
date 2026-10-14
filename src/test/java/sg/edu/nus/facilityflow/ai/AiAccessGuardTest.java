package sg.edu.nus.facilityflow.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccount;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

class AiAccessGuardTest {

    private static final UUID CURRENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void aiSec001UnauthorizedRequesterBuildsNoContextAndMakesNoModelCall() {
        AtomicBoolean contextBuilt = new AtomicBoolean();
        AtomicBoolean modelCalled = new AtomicBoolean();
        AiAccessGuard guard = guardFor(AccountRole.REQUESTER);

        assertThrows(AccessDeniedException.class, () -> guard.forRequester(
                OTHER_ID,
                () -> {
                    contextBuilt.set(true);
                    return "protected context";
                },
                context -> {
                    modelCalled.set(true);
                    return "suggestion";
                }));

        assertFalse(contextBuilt.get());
        assertFalse(modelCalled.get());
    }

    @Test
    void aiSec001AuthorizedRequesterCallsModelOnlyAfterContextConstruction() {
        AtomicBoolean contextBuilt = new AtomicBoolean();
        AtomicBoolean modelSawBuiltContext = new AtomicBoolean();
        AiAccessGuard guard = guardFor(AccountRole.REQUESTER);

        String result = guard.forRequester(
                CURRENT_ID,
                () -> {
                    contextBuilt.set(true);
                    return "allowed context";
                },
                context -> {
                    modelSawBuiltContext.set(contextBuilt.get());
                    return context.toUpperCase();
                });

        assertEquals("ALLOWED CONTEXT", result);
        assertTrue(modelSawBuiltContext.get());
    }

    private static AiAccessGuard guardFor(AccountRole role) {
        CurrentAccount account = new CurrentAccount(CURRENT_ID, "current", "Current User", role);
        return new AiAccessGuard(new IdentityAuthorization(() -> account));
    }
}
