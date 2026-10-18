package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.IdentityAuthorization;

class TechnicianCompletionServiceTest {
    private static final CurrentAccountSnapshot ACTOR = new CurrentAccountSnapshot(
            TechnicianCommandFixtures.TECHNICIAN, AccountRole.TECHNICIAN, 7);

    private IdentityAuthorization authorization;
    private TechnicianCommandRepository repository;
    private TechnicianCommandService service;

    @BeforeEach
    void setUp() {
        authorization = mock(IdentityAuthorization.class);
        repository = mock(TechnicianCommandRepository.class);
        service = new TechnicianCommandService(authorization, repository);
        when(authorization.requireRoleSnapshot(AccountRole.TECHNICIAN)).thenReturn(ACTOR);
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-A05: completion forwards the authenticated actor and outer-stripped summary")
    void completionUsesServerActorAndPreservesInternalWhitespace() {
        service.completeWork("FF-000123", 8, " \tRepaired pump.\nVerified flow.  ");

        verify(authorization).requireRoleSnapshot(AccountRole.TECHNICIAN);
        verify(repository).completeWork(ACTOR, "FF-000123", 8, "Repaired pump.\nVerified flow.");
    }

    @Test
    @DisplayName("REQ-TECH-007: completion accepts 10 and 2,000 Unicode code points")
    void completionAcceptsInclusiveUnicodeCodePointLimits() {
        String min = "😀".repeat(10);
        String max = "😀".repeat(2000);

        service.completeWork("FF-000123", 0, min);
        service.completeWork("FF-000123", 1, max);

        verify(repository).completeWork(ACTOR, "FF-000123", 0, min);
        verify(repository).completeWork(ACTOR, "FF-000123", 1, max);
    }

    @Test
    @DisplayName("REQ-TECH-007: blank, null, 9 and 2,001 code point summaries fail before persistence")
    void invalidResolutionSummariesAreRejected() {
        for (String invalid : new String[]{null, " \n\t ", "x".repeat(9), "x".repeat(2001)}) {
            var failure = assertThrows(TechnicianCommandException.class,
                    () -> service.completeWork("FF-000123", 0, invalid));
            assertEquals(TechnicianCommandException.Code.INVALID_INPUT, failure.code());
        }
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-A06: invalid identity, version, or display ID prevents completion writes")
    void invalidRequestAndAuthorizationDoNotReachRepository() {
        var negative = assertThrows(TechnicianCommandException.class,
                () -> service.completeWork("FF-000123", -1, "Valid resolution summary"));
        var malformed = assertThrows(TechnicianCommandException.class,
                () -> service.completeWork("private-id", 0, "Valid resolution summary"));
        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, negative.code());
        assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE, malformed.code());
        verifyNoInteractions(repository);

        AccessDeniedException denial = new AccessDeniedException("forbidden");
        when(authorization.requireRoleSnapshot(AccountRole.TECHNICIAN)).thenThrow(denial);
        assertEquals(denial, assertThrows(AccessDeniedException.class,
                () -> service.completeWork("FF-000123", 0, "Valid resolution summary")));
        verifyNoInteractions(repository);
    }
}
