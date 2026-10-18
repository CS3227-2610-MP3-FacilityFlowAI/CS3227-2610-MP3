package sg.edu.nus.facilityflow.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ViewResolver;
import sg.edu.nus.facilityflow.identity.Account;
import sg.edu.nus.facilityflow.identity.AccountRole;
import sg.edu.nus.facilityflow.identity.CurrentAccountPrincipal;
import sg.edu.nus.facilityflow.identity.CurrentAccountProvider;
import sg.edu.nus.facilityflow.identity.CurrentAccountSnapshot;
import sg.edu.nus.facilityflow.identity.PasswordChangeException;
import sg.edu.nus.facilityflow.identity.PasswordChangeRepository;
import sg.edu.nus.facilityflow.identity.PasswordChangeService;

/** Standalone MVC behavior checks; security-chain CSRF/session coverage is owned by the root test lane. */
class SharedPagesControllerTest {
    private MockMvc mvc;
    private RecordingRepository repository;
    private CurrentAccountPrincipal principal;

    @BeforeEach void setUp() {
        repository = new RecordingRepository();
        Account account = new Account(UUID.fromString("75000000-0000-0000-0000-000000000001"),
                "synthetic-tech", "Synthetic Technician", "{noop}password", AccountRole.TECHNICIAN,
                true, 4, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        principal = CurrentAccountPrincipal.from(account);
        CurrentAccountProvider provider = new CurrentAccountProvider() {
            public sg.edu.nus.facilityflow.identity.CurrentAccount requireCurrentAccount() { return principal.asCurrentAccount(); }
            public CurrentAccountSnapshot requireCurrentAccountSnapshot() { return new CurrentAccountSnapshot(account.id(), account.role(), account.sessionVersion()); }
        };
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
        ViewResolver testViewResolver = (viewName, locale) -> (model, request, response) -> { };
        mvc = MockMvcBuilders.standaloneSetup(new SharedPagesController(new PasswordChangeService(provider, repository)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setViewResolvers(testViewResolver).build();
    }

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("REQ-SH-012/NFR-SEC-001: protected shared pages derive identity from principal and disable caching")
    void sharedPagesUseAuthenticatedIdentityAndNoStore() throws Exception {
        mvc.perform(get("/account/password").with(user(principal)))
                .andExpect(status().isOk()).andExpect(view().name("account/change-password"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("username", "synthetic-tech"))
                .andExpect(model().attribute("displayName", "Synthetic Technician"))
                .andExpect(model().attribute("role", "TECHNICIAN"))
                .andExpect(model().attribute("fieldErrors", Map.of()));
        mvc.perform(get("/about").with(user(principal)))
                .andExpect(status().isOk()).andExpect(view().name("about"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("role", "TECHNICIAN"));
        assertEquals(0, repository.calls);
    }

    @Test
    @DisplayName("REQ-SH-010/REQ-SH-004: successful password POST has fixed 303 target and does not expose fields")
    void successfulChangeRedirectsWithoutPasswordEcho() throws Exception {
        mvc.perform(post("/account/password").with(user(principal))
                        .param("currentPassword", "old-password")
                        .param("newPassword", "new-password")
                        .param("confirmPassword", "new-password"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/account/password?changed"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertEquals(1, repository.calls);
        assertEquals("old-password", repository.currentPassword);
        assertEquals("new-password", repository.newPassword);
        mvc.perform(get("/account/password").param("changed", "true").with(user(principal)))
                .andExpect(model().attribute("changed", true));
    }

    @Test
    @DisplayName("NFR-SEC-002: missing input failure returns safe field errors without credential model attributes")
    void invalidInputFailureNeverEchoesSubmittedValues() throws Exception {
        mvc.perform(post("/account/password").with(user(principal))
                        .param("currentPassword", "old-password")
                        .param("newPassword", "short-secret")
                        .param("confirmPassword", "different-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("account/change-password"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attributeExists("fieldErrors"))
                .andExpect(model().attributeDoesNotExist("currentPassword", "newPassword", "confirmPassword", "password"));
        assertEquals(0, repository.calls);
    }

    @Test
    @DisplayName("REQ-SH-002: changed access is a safe 403 and storage outages are a safe 503")
    void mapsAuthorizationAndUnavailableFailuresWithoutSecretEcho() throws Exception {
        repository.failure = new PasswordChangeException(PasswordChangeException.Code.ACCESS_CHANGED);
        mvc.perform(post("/account/password").with(user(principal)).param("currentPassword", "old-password")
                        .param("newPassword", "new-password").param("confirmPassword", "new-password"))
                .andExpect(status().isForbidden()).andExpect(model().attributeDoesNotExist("newPassword"));
        repository.failure = new PasswordChangeException(PasswordChangeException.Code.UNAVAILABLE);
        mvc.perform(post("/account/password").with(user(principal)).param("currentPassword", "old-password")
                        .param("newPassword", "new-password").param("confirmPassword", "new-password"))
                .andExpect(status().isServiceUnavailable()).andExpect(model().attributeExists("passwordError"))
                .andExpect(model().attributeDoesNotExist("newPassword"));
    }

    private static final class RecordingRepository implements PasswordChangeRepository {
        int calls;
        String currentPassword;
        String newPassword;
        PasswordChangeException failure;
        public void changeOwnPassword(CurrentAccountSnapshot actor, String currentPassword, String newPassword) {
            calls++; this.currentPassword = currentPassword; this.newPassword = newPassword;
            if (failure != null) throw failure;
        }
    }
}
