package sg.edu.nus.facilityflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import sg.edu.nus.facilityflow.identity.AccountRole;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIntegrationTest {

    private static final String VALID_PASSWORD = "Welcome123";
    private static final UUID REQUESTER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICIAN_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID MANAGER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID INACTIVE_ID = UUID.fromString("30000000-0000-0000-0000-000000000004");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String validPasswordHash;

    @BeforeAll
    void hashFixturePasswordOnce() {
        validPasswordHash = passwordEncoder.encode(VALID_PASSWORD);
    }

    @BeforeEach
    void seedAccounts() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM user_account");
        insertAccount(REQUESTER_ID, "requester1", AccountRole.REQUESTER, true);
        insertAccount(TECHNICIAN_ID, "technician1", AccountRole.TECHNICIAN, true);
        insertAccount(MANAGER_ID, "manager1", AccountRole.FACILITIES_MANAGER, true);
        insertAccount(INACTIVE_ID, "inactive1", AccountRole.REQUESTER, false);
    }

    @Test
    void reqSh001ValidUsersReachOnlyTheirRoleDashboard() throws Exception {
        Cookie requester = login("REQUESTER1", VALID_PASSWORD, "/requester");
        mockMvc.perform(get("/requester").cookie(requester))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Requester workspace")));
        mockMvc.perform(get("/technician").cookie(requester)).andExpect(status().isForbidden());
        mockMvc.perform(get("/manager").cookie(requester)).andExpect(status().isForbidden());

        Cookie technician = login("technician1", VALID_PASSWORD, "/technician");
        mockMvc.perform(get("/technician").cookie(technician)).andExpect(status().isOk());

        Cookie manager = login("manager1", VALID_PASSWORD, "/manager");
        mockMvc.perform(get("/manager").cookie(manager)).andExpect(status().isOk());
    }

    @Test
    void reqSh003InvalidUnknownAndInactiveAccountsHaveIdenticalExternalFailure() throws Exception {
        String wrongPassword = failedLoginRedirect("requester1", "not-the-password");
        String unknown = failedLoginRedirect("does-not-exist", VALID_PASSWORD);
        String inactive = failedLoginRedirect("inactive1", VALID_PASSWORD);

        assertThat(wrongPassword).isEqualTo("/login?error");
        assertThat(unknown).isEqualTo(wrongPassword);
        assertThat(inactive).isEqualTo(wrongPassword);
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Invalid username or password")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("inactive1"))));
    }

    @Test
    void reqSh002UnauthenticatedAccessRedirectsAndCrossRoleAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/requester"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        mockMvc.perform(get("/manager").cookie(requester)).andExpect(status().isForbidden());
    }

    @Test
    void nfrSec001StateChangingRequestsRequireCsrf() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");

        mockMvc.perform(post("/logout").cookie(requester)).andExpect(status().isForbidden());
        mockMvc.perform(get("/requester").cookie(requester)).andExpect(status().isOk());
    }

    @Test
    void reqSh004LogoutInvalidatesTheServerSession() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");

        mockMvc.perform(post("/logout").cookie(requester).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
        mockMvc.perform(get("/requester").cookie(requester))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void reqSh004DeactivationAndSessionVersionChangesRejectExistingSessions() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        jdbc.update("UPDATE user_account SET active = FALSE WHERE id = ?", REQUESTER_ID);

        mockMvc.perform(get("/requester").cookie(requester))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        jdbc.update("UPDATE user_account SET active = TRUE WHERE id = ?", REQUESTER_ID);
        Cookie newSession = login("requester1", VALID_PASSWORD, "/requester");
        jdbc.update("UPDATE user_account SET session_version = session_version + 1 WHERE id = ?", REQUESTER_ID);
        mockMvc.perform(get("/requester").cookie(newSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void reqSh004PersistedRoleChangeTakesEffectWithoutNewLogin() throws Exception {
        Cookie requester = login("requester1", VALID_PASSWORD, "/requester");
        jdbc.update(
                "UPDATE user_account SET role = 'FACILITIES_MANAGER', updated_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC),
                REQUESTER_ID);

        mockMvc.perform(get("/requester").cookie(requester)).andExpect(status().isForbidden());
        mockMvc.perform(get("/manager").cookie(requester)).andExpect(status().isOk());
    }

    @Test
    void reqSh005SessionCookieIsBrowserScopedSecureHttpOnlyAndSameSiteLax() throws Exception {
        MvcResult result = performSuccessfulLogin("requester1", VALID_PASSWORD, "/requester");
        String setCookie = result.getResponse().getHeader("Set-Cookie");

        assertThat(setCookie).contains("SESSION=");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).doesNotContain("Max-Age");
        Integer inactiveInterval = jdbc.queryForObject(
                "SELECT max_inactive_interval FROM spring_session", Integer.class);
        assertThat(inactiveInterval).isNegative();
    }

    @Test
    void reqSh003PasswordIsStoredOnlyAsAnArgon2Hash() {
        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM user_account WHERE id = ?", String.class, REQUESTER_ID);

        assertThat(stored).isNotEqualTo(VALID_PASSWORD).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, stored)).isTrue();
    }

    @Test
    void nfrSec002SessionSerializationContainsNoPasswordOrPasswordHash() throws Exception {
        performSuccessfulLogin("requester1", VALID_PASSWORD, "/requester");

        var serializedAttributes = jdbc.query(
                "SELECT attribute_bytes FROM spring_session_attributes",
                (resultSet, rowNumber) -> resultSet.getBytes(1));
        assertThat(serializedAttributes).isNotEmpty();
        assertThat(serializedAttributes)
                .allSatisfy(bytes -> {
                    String serialized = new String(bytes, StandardCharsets.ISO_8859_1);
                    assertThat(serialized).doesNotContain(VALID_PASSWORD);
                    assertThat(serialized).doesNotContain(validPasswordHash);
                    assertThat(serialized).doesNotContain("$argon2id$");
                });
    }

    @Test
    void nfrA11y001LoginAndLogoutWorkWithoutJavaScript() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<label for=\"username\">Username</label>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<label for=\"password\">Password</label>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("method=\"post\"")));
    }

    private Cookie login(String username, String password, String destination) throws Exception {
        MvcResult result = performSuccessfulLogin(username, password, destination);
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("Spring Session cookie").isNotNull();
        return cookie;
    }

    private MvcResult performSuccessfulLogin(
            String username,
            String password,
            String destination) throws Exception {
        return mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(destination))
                .andReturn();
    }

    private String failedLoginRedirect(String username, String password) throws Exception {
        return mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private void insertAccount(UUID id, String username, AccountRole role, boolean active) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                        INSERT INTO user_account (
                            id, username, normalized_username, display_name, password_hash,
                            role, active, session_version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                username,
                username.toLowerCase(Locale.ROOT),
                username + " display",
                validPasswordHash,
                role.name(),
                active,
                0L,
                now,
                now);
    }
}
