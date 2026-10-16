package sg.edu.nus.facilityflow.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PasswordChangeServiceTest {
    @Test
    void reqSh002RejectsChangedOrInactiveAccountAtUseCaseSeam() {
        var id = UUID.randomUUID();
        var accounts = mock(AccountRepository.class);
        var operations = mock(JdbcIdentityOperations.class);
        var encoder = mock(PasswordEncoder.class);
        CurrentAccountProvider current = () -> new CurrentAccount(id, "owner", "Owner", AccountRole.REQUESTER, 0);
        var service = new PasswordChangeService(current, accounts, operations, encoder);
        for (var account : new Account[]{
                account(id, false, AccountRole.REQUESTER, 0),
                account(id, true, AccountRole.REQUESTER, 1),
                account(id, true, AccountRole.FACILITIES_MANAGER, 0)}) {
            when(accounts.findById(id)).thenReturn(Optional.of(account));
            assertThrows(AccessDeniedException.class, () -> service.changePassword("old-password", "new-password"));
        }
        when(accounts.findById(id)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.changePassword("old-password", "new-password"));
        verifyNoInteractions(operations, encoder);
    }

    private Account account(UUID id, boolean active, AccountRole role, long sessionVersion) {
        return new Account(id, "owner", "Owner", "synthetic-hash", role, active,
                sessionVersion, Instant.EPOCH, Instant.EPOCH);
    }
}
