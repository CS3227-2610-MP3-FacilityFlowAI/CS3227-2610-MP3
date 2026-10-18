package sg.edu.nus.facilityflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import sg.edu.nus.facilityflow.ai.manager.TriageFailureCode;
import sg.edu.nus.facilityflow.ai.manager.TriageResult;
import sg.edu.nus.facilityflow.identity.Account;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.manager.ManagerTriageResponse;
import sg.edu.nus.facilityflow.manager.ManagerTriageService;
import sg.edu.nus.facilityflow.manager.TechnicianCandidate;
import sg.edu.nus.facilityflow.manager.TriageRankingResult;

class ManagerTriagePageControllerTest {
    private static final String DISPLAY_ID = "FF-000051";

    @Test
    void getExplainsAdvisoryReviewWithoutInvokingTheAssistant() {
        var response = new MockHttpServletResponse();
        var page = new ManagerTriagePageController(null).page(principal(), DISPLAY_ID, response);

        assertThat(page.getViewName()).isEqualTo("manager/triage");
        assertThat(page.getModel()).containsEntry("displayId", DISPLAY_ID);
        assertThat(page.getModel()).doesNotContainKey("suggestion");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void successfulSuggestionIsMappedToTheHumanReviewSurface() {
        ManagerTriageService service = mock(ManagerTriageService.class);
        TriageResult suggestion = new TriageResult(
                "HVAC", "High", List.of("Can the unit be isolated?"),
                List.of("Electrical"), List.of("Working at height"),
                "The report indicates an electrical HVAC fault.");
        TriageRankingResult ranking = new TriageRankingResult(List.of(
                new TechnicianCandidate(UUID.randomUUID(), "tech-one", true,
                        Set.of("Electrical", "Working at height"), 1)), "Ranked deterministically.");
        when(service.request(DISPLAY_ID)).thenReturn(CompletableFuture.completedFuture(
                new ManagerTriageResponse.Success(suggestion, ranking)));

        ModelAndView page = new ManagerTriagePageController(service)
                .suggest(principal(), DISPLAY_ID, new MockHttpServletResponse())
                .toCompletableFuture().join();

        assertThat(page.getModel()).containsEntry("suggestion", suggestion)
                .containsEntry("ranking", ranking)
                .doesNotContainKey("assistantError");
    }

    @Test
    void safeFailureKeepsManualTriageAvailable() {
        ManagerTriageService service = mock(ManagerTriageService.class);
        when(service.request(DISPLAY_ID)).thenReturn(CompletableFuture.completedFuture(
                new ManagerTriageResponse.Failure(TriageFailureCode.AI_TIMEOUT)));

        ModelAndView page = new ManagerTriagePageController(service)
                .suggest(principal(), DISPLAY_ID, new MockHttpServletResponse())
                .toCompletableFuture().join();

        assertThat(page.getModel()).doesNotContainKey("suggestion");
        assertThat(page.getModel().get("assistantError").toString()).containsIgnoringCase("time");
    }

    private static CurrentAccountPrincipal principal() {
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        return CurrentAccountPrincipal.from(new Account(
                UUID.randomUUID(), "manager-test", "Manager Test", "synthetic",
                AccountRole.FACILITIES_MANAGER, true, 0, now, now));
    }
}
