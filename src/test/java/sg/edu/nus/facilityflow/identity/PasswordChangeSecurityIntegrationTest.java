package sg.edu.nus.facilityflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.ConfigurableWebApplicationContext;

/** REQ-SH-004/006/014: real password encoder, PostgreSQL audit, filters and JDBC sessions together. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordChangeSecurityIntegrationTest {
    private PasswordChangeFixtures db;
    private ConfigurableWebApplicationContext context;
    private MockMvc mvc;
    private PasswordEncoder encoder;
    private String initialHash;
    private static final String REPLACEMENT = "synthetic-new-password";

    @BeforeAll void start() throws Exception {
        db = PasswordChangeFixtures.start();
        var application = new org.springframework.boot.SpringApplication(
                sg.edu.nus.facilityflow.FacilityFlowApplication.class);
        var environment = new org.springframework.web.context.support.StandardServletEnvironment();
        environment.getPropertySources().remove(
                org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        context = (ConfigurableWebApplicationContext) application.run(
                "--spring.profiles.active=test", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.flyway.enabled=false", "--spring.datasource.url=jdbc:postgresql://localhost:"
                        + db.postgres.getPort() + "/postgres",
                "--spring.datasource.username=facilityflow_app", "--spring.datasource.password=",
                "--spring.datasource.hikari.data-source-properties.sslmode=disable",
                "--spring.datasource.hikari.data-source-properties.sslrootcert=synthetic-test.crt");
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSessionRepositoryFilter", jakarta.servlet.Filter.class))
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        encoder = context.getBean(PasswordEncoder.class);
        initialHash = "{pbkdf2@SpringSecurity_v5_8}"
                + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(PasswordChangeFixtures.OLD_PASSWORD);
    }

    @BeforeEach void seed() {
        db.admin.execute("TRUNCATE facilityflow.spring_session CASCADE");
        db.reset();
        db.account("TECHNICIAN", true, 4, initialHash);
    }

    @AfterAll void stop() throws Exception {
        if (context != null) context.close();
        if (db != null) db.close();
    }

    @Test void ownPasswordRetainsBothSessionsAndChangesTheNextLoginCredential() throws Exception {
        Cookie first = login(PasswordChangeFixtures.OLD_PASSWORD);
        Cookie second = login(PasswordChangeFixtures.OLD_PASSWORD);
        int sessions = db.app.queryForObject("SELECT count(*) FROM facilityflow.spring_session", Integer.class);
        mvc.perform(post("/account/password").cookie(first).with(csrf())
                        .param("currentPassword", PasswordChangeFixtures.OLD_PASSWORD)
                        .param("newPassword", REPLACEMENT).param("confirmPassword", REPLACEMENT))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/account/password?changed"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        String hash = db.app.queryForObject("SELECT password_hash FROM facilityflow.user_account", String.class);
        assertThat(hash).startsWith("{argon2}").isNotEqualTo(initialHash);
        assertThat(encoder.matches(REPLACEMENT, hash)).isTrue();
        assertThat(encoder.matches(PasswordChangeFixtures.OLD_PASSWORD, hash)).isFalse();
        assertThat(db.app.queryForObject("SELECT session_version FROM facilityflow.user_account", Long.class)).isEqualTo(4L);
        assertThat(db.app.queryForObject("SELECT version FROM facilityflow.user_account", Long.class)).isEqualTo(1L);
        assertThat(db.app.queryForObject("SELECT count(*) FROM facilityflow.spring_session", Integer.class)).isEqualTo(sessions);
        for (Cookie cookie : new Cookie[]{first, second}) {
            mvc.perform(get("/account/password").param("changed", "").cookie(cookie)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Your password was changed.")));
            mvc.perform(get("/technician").cookie(cookie)).andExpect(status().isOk());
        }
        assertThat(db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = 'ACCOUNT_PASSWORD_CHANGED'",
                Integer.class)).isEqualTo(1);
        mvc.perform(post("/login").with(csrf()).param("username", "password-test")
                        .param("password", PasswordChangeFixtures.OLD_PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        login(REPLACEMENT);
        db.app.query("SELECT attribute_bytes FROM facilityflow.spring_session_attributes", (rs, row) -> rs.getBytes(1))
                .forEach(bytes -> assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
                        .doesNotContain(REPLACEMENT, PasswordChangeFixtures.OLD_PASSWORD, hash, initialHash));
    }

    @Test void auditFailurePreservesCredentialAndCurrentSessionEndToEnd() throws Exception {
        Cookie session = login(PasswordChangeFixtures.OLD_PASSWORD);
        db.admin.execute("""
                CREATE FUNCTION facilityflow.fail_password_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'synthetic failure'; END $$
                """);
        db.admin.execute("CREATE TRIGGER fail_password_audit BEFORE INSERT ON facilityflow.audit_event "
                + "FOR EACH ROW WHEN (NEW.action = 'ACCOUNT_PASSWORD_CHANGED') EXECUTE FUNCTION facilityflow.fail_password_audit()");
        mvc.perform(post("/account/password").cookie(session).with(csrf())
                        .param("currentPassword", PasswordChangeFixtures.OLD_PASSWORD)
                        .param("newPassword", REPLACEMENT).param("confirmPassword", REPLACEMENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(REPLACEMENT))));
        assertThat(db.app.queryForObject("SELECT password_hash FROM facilityflow.user_account", String.class)).isEqualTo(initialHash);
        assertThat(db.app.queryForObject("SELECT version FROM facilityflow.user_account", Long.class)).isZero();
        assertThat(db.app.queryForObject("SELECT count(*) FROM facilityflow.audit_event WHERE action = 'ACCOUNT_PASSWORD_CHANGED'",
                Integer.class)).isZero();
        mvc.perform(get("/technician").cookie(session)).andExpect(status().isOk());
    }

    private Cookie login(String password) throws Exception {
        Cookie cookie = mvc.perform(post("/login").with(csrf()).param("username", "password-test")
                        .param("password", password))
                .andExpect(redirectedUrl("/technician")).andReturn().getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
