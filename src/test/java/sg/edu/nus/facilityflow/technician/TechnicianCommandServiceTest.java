package sg.edu.nus.facilityflow.technician;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

class TechnicianCommandServiceTest {
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
    @DisplayName("REQ-SH-002/TECH-A01: start command forwards the authenticated server snapshot and version")
    void startUsesServerAuthenticatedSnapshot() {
        service.startWork("FF-000123", 4);

        verify(authorization).requireRoleSnapshot(AccountRole.TECHNICIAN);
        verify(repository).startWork(ACTOR, "FF-000123", 4);
    }

    @Test
    @DisplayName("REQ-SH-002/TECH-A03/SH-006: log command strips only outer whitespace and preserves inner text")
    void workLogNormalizesNoteAndForwardsAuthenticatedSnapshot() {
        service.appendWorkLog("FF-000123", 5, " \tReplaced  valve\nchecked pressure. \n", 45);

        verify(repository).appendWorkLog(ACTOR, "FF-000123", 5, "Replaced  valve\nchecked pressure.", 45);
    }

    @Test
    @DisplayName("REQ-SH-002: malformed display identifiers are unavailable and never reach persistence")
    void malformedDisplayIdsAreUnavailable() {
        for (String invalid : new String[]{null, "FF-000000", "FF-000001 ", "ff-000001", "FF-1000000", "private-id"}) {
            var failure = assertThrows(TechnicianCommandException.class, () -> service.startWork(invalid, 0));
            assertEquals(TechnicianCommandException.Code.NOT_AVAILABLE, failure.code());
        }

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("REQ-TECH-004/005: negative form versions are rejected before persistence")
    void negativeVersionsAreInvalidInput() {
        var startFailure = assertThrows(TechnicianCommandException.class, () -> service.startWork("FF-000123", -1));
        var logFailure = assertThrows(TechnicianCommandException.class,
                () -> service.appendWorkLog("FF-000123", -1, "work performed", 1));

        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, startFailure.code());
        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, logFailure.code());
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("REQ-TECH-006: Work Log note length uses Unicode code points at 1 and 1000")
    void acceptsUnicodeCodePointBoundariesAndRejectsOutsideThem() {
        String supplementary = "😀";
        String atMaximum = supplementary.repeat(1000);
        service.appendWorkLog("FF-000123", 0, supplementary, 1);
        service.appendWorkLog("FF-000123", 0, atMaximum, 1440);

        var empty = assertThrows(TechnicianCommandException.class,
                () -> service.appendWorkLog("FF-000123", 0, " \n\t ", 10));
        var tooLong = assertThrows(TechnicianCommandException.class,
                () -> service.appendWorkLog("FF-000123", 0, supplementary.repeat(1001), 10));

        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, empty.code());
        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, tooLong.code());
        verify(repository).appendWorkLog(ACTOR, "FF-000123", 0, supplementary, 1);
        verify(repository).appendWorkLog(ACTOR, "FF-000123", 0, atMaximum, 1440);
        verify(repository, never()).appendWorkLog(ACTOR, "FF-000123", 0, "", 10);
    }

    @Test
    @DisplayName("REQ-TECH-006: Work Log minutes include 1 and 1440 and reject values outside the range")
    void validatesWholeMinuteBoundaries() {
        service.appendWorkLog("FF-000123", 0, "one minute", 1);
        service.appendWorkLog("FF-000123", 0, "one day", 1440);

        var below = assertThrows(TechnicianCommandException.class,
                () -> service.appendWorkLog("FF-000123", 0, "too few", 0));
        var above = assertThrows(TechnicianCommandException.class,
                () -> service.appendWorkLog("FF-000123", 0, "too many", 1441));

        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, below.code());
        assertEquals(TechnicianCommandException.Code.INVALID_INPUT, above.code());
        verify(repository).appendWorkLog(ACTOR, "FF-000123", 0, "one minute", 1);
        verify(repository).appendWorkLog(ACTOR, "FF-000123", 0, "one day", 1440);
    }

    @Test
    @DisplayName("REQ-SH-002: identity authorization failures propagate without invoking the command repository")
    void identityDenialIsPreserved() {
        AccessDeniedException denial = new AccessDeniedException("forbidden");
        when(authorization.requireRoleSnapshot(AccountRole.TECHNICIAN)).thenThrow(denial);

        assertEquals(denial, assertThrows(AccessDeniedException.class,
                () -> service.appendWorkLog("FF-000123", 0, "valid note", 1)));
        verifyNoInteractions(repository);
    }
}
