package sg.edu.nus.facilityflow.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import sg.edu.nus.facilityflow.ai.requester.*;
import sg.edu.nus.facilityflow.identity.*;
import sg.edu.nus.facilityflow.requester.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** REQ-RQ-013/017: the form endpoint adapts input and delegates to the shared role service. */
class SmartReportFormAdapterTest {
    private final RequesterRequestRepository repository = mock(RequesterRequestRepository.class);
    private final RequesterSmartReportService shared = mock(RequesterSmartReportService.class);
    private final SmartReportService form = new SmartReportService(
            () -> new CurrentAccount(UUID.randomUUID(), "requester", "Requester", AccountRole.REQUESTER, 0),
            repository, mock(PlatformTransactionManager.class), shared);

    @Test void delegatesCurrentFieldsWithoutTruncatingOrSendingIdentifiersAsText() {
        var result = new SmartReportResult("Flickering light", null, null, "Electrical", "Normal", List.of(), "Clarify the room.");
        when(shared.requestCreate(any())).thenReturn(CompletableFuture.completedFuture(new SmartReportResponse.Success(result)));
        String category = UUID.randomUUID().toString();
        var reply = form.suggest(null, null, new SmartReportInput("Rough description", "Manual title", "Manual description", "Block A", category, "NORMAL"));
        assertEquals("SUCCEEDED", reply.status());
        assertSame(result, reply.suggestion());
        var input = ArgumentCaptor.forClass(RequesterSmartReportInput.class);
        verify(shared).requestCreate(input.capture());
        assertEquals("Manual title", input.getValue().roughTitle());
        assertEquals("Rough description\n\nManual description", input.getValue().roughDescription());
        assertEquals("Block A", input.getValue().roughLocation());
        assertEquals(category, input.getValue().selectedCategory());
        assertEquals("Normal", input.getValue().selectedUrgency());
        verify(repository, never()).categories();
    }

    @Test void rejectsCombinedOversizeInputWithoutCallingSharedService() {
        assertEquals("AI_INVALID_REQUEST", form.suggest(null, null,
                new SmartReportInput("a".repeat(1500), "", "b".repeat(1000), "", "", "")).status());
        verifyNoInteractions(shared);
    }

    @Test void sameDescriptionIsNotDuplicated() {
        when(shared.requestCreate(any())).thenReturn(CompletableFuture.completedFuture(SmartReportResponse.failure(SmartReportFailureCode.AI_UNAVAILABLE)));
        String description = "a".repeat(2000);
        form.suggest(null, null, new SmartReportInput(description, "", description, "", "", ""));
        verify(shared).requestCreate(new RequesterSmartReportInput("", description, "", "", null));
    }

    @Test void staleEditFormDoesNotInvokeRuntime() {
        when(repository.openVersion(any(), eq("FF-000001"))).thenReturn(Optional.of(1L));
        assertThrows(StaleRequestException.class, () -> form.suggest("FF-000001", "0",
                new SmartReportInput("Flickering light", "", "", "", "", "")));
        verifyNoInteractions(shared);
    }

    @Test void editPassesExpectedVersionAndPreservesSharedDiscardCode() {
        when(repository.openVersion(any(), eq("FF-000001"))).thenReturn(Optional.of(3L));
        when(shared.requestEdit(eq("FF-000001"), any(), eq(3L))).thenReturn(
                CompletableFuture.completedFuture(SmartReportResponse.failure(SmartReportFailureCode.DISCARDED_ACCESS_CHANGED)));
        var reply = form.suggest("FF-000001", "3", new SmartReportInput("Flickering light", "", "", "", "", ""));
        assertEquals("DISCARDED_ACCESS_CHANGED", reply.status());
        assertNull(reply.suggestion());
        verify(shared).requestEdit(eq("FF-000001"), any(), eq(3L));
    }
}
