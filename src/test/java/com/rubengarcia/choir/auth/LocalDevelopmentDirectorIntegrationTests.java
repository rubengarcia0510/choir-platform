package com.rubengarcia.choir.auth;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "choir.auth.local-director.enabled=true",
        "CHOIR_DEV_DIRECTOR_NAME=CHOIR Local Integration Director",
        "CHOIR_DEV_DIRECTOR_EMAIL=choir-local-integration@example.test",
        "CHOIR_DEV_DIRECTOR_PASSWORD=Choir-Local-Integration-2026!"
})
@ActiveProfiles("local")
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LocalDevelopmentDirectorIntegrationTests {

    private static final String EMAIL =
            "choir-local-integration@example.test";
    private static final String PASSWORD =
            "Choir-Local-Integration-2026!";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final WebTestClient webTestClient;

    LocalDevelopmentDirectorIntegrationTests(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            WebTestClient webTestClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.webTestClient = webTestClient;
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM directors WHERE email = ?", EMAIL);
    }

    @Test
    void initializesLocalDirectorAndAllowsRealAuthentication() {
        Map<String, Object> director = jdbcTemplate.queryForMap(
                """
                SELECT display_name, email, password_hash, email_verified
                FROM directors
                WHERE email = ?
                """,
                EMAIL);

        assertThat(director.get("display_name"))
                .isEqualTo("CHOIR Local Integration Director");
        assertThat(director.get("email")).isEqualTo(EMAIL);
        assertThat(director.get("email_verified")).isEqualTo(true);

        String passwordHash = (String) director.get("password_hash");
        assertThat(passwordHash).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();

        EntityExchangeResult<Map<String, String>> csrfResult =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        Map<String, String>>() {})
                        .returnResult();

        ResponseCookie initialCookie =
                csrfResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(initialCookie);

        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken);

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", initialCookie.getValue())
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {
                                  "email": "choir-local-integration@example.test",
                                  "password": "Choir-Local-Integration-2026!"
                                }
                                """)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticatedCookie);

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie("SESSION", authenticatedCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(EMAIL)
                .jsonPath("$.displayName")
                .isEqualTo("CHOIR Local Integration Director");
    }

    @Test
    void initializerIsRestrictedToLocalProfileAndExplicitProperty() {
        Profile profile =
                LocalDevelopmentDirectorInitializer.class
                        .getAnnotation(Profile.class);
        ConditionalOnProperty condition =
                LocalDevelopmentDirectorInitializer.class
                        .getAnnotation(ConditionalOnProperty.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local");

        assertThat(condition).isNotNull();
        assertThat(condition.prefix())
                .isEqualTo("choir.auth.local-director");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
