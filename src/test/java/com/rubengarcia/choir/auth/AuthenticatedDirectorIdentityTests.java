package com.rubengarcia.choir.auth;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthenticatedDirectorIdentityTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private Long firstDirectorId;
    private Long secondDirectorId;

    AuthenticatedDirectorIdentityTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @AfterEach
    void cleanup() {
        if (firstDirectorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", firstDirectorId);
            firstDirectorId = null;
        }

        if (secondDirectorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", secondDirectorId);
            secondDirectorId = null;
        }
    }

    @Test
    void independentSessionsReturnTheirOwnDirectorIdentity() throws Exception {
        DirectorAccount first = createDirector("First");
        DirectorAccount second = createDirector("Second");

        Session firstSession = login(first.email());
        Session secondSession = login(second.email());

        assertThat(firstSession.sessionId())
                .isNotEqualTo(secondSession.sessionId());

        assertIdentity(firstSession, first);
        assertIdentity(secondSession, second);
    }

    @Test
    void clientSuppliedDirectorIdCannotChangeAuthenticatedIdentity() {
        DirectorAccount authenticatedDirector = createDirector("Authenticated");
        DirectorAccount otherDirector = createDirector("Other");

        Session session = login(authenticatedDirector.email());

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/auth/me")
                        .queryParam("directorId", otherDirector.id())
                        .build())
                .cookie("SESSION", session.sessionId())
                .header("X-Director-Id", String.valueOf(otherDirector.id()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(authenticatedDirector.id())
                .jsonPath("$.email").isEqualTo(authenticatedDirector.email())
                .jsonPath("$.displayName")
                .isEqualTo(authenticatedDirector.displayName());
    }

    private DirectorAccount createDirector(String label) {
        String unique = UUID.randomUUID().toString();
        String email = label.toLowerCase() + "-" + unique + "@example.test";
        String displayName = "Director " + label + " " + unique;

        jdbcTemplate.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                displayName,
                email,
                passwordEncoder.encode(PASSWORD));

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);

        assertNotNull(id);

        if (firstDirectorId == null) {
            firstDirectorId = id;
        } else {
            secondDirectorId = id;
        }

        return new DirectorAccount(id, displayName, email);
    }

    private Session login(String email) {
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
        assertNotNull(initialCookie, "Debe crearse una sesión para CSRF");

        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken, "Debe generarse el token CSRF");

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", PASSWORD));
        } catch (Exception exception) {
            throw new AssertionError("No se pudo serializar el login", exception);
        }

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", initialCookie.getValue())
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");

        assertNotNull(
                authenticatedCookie,
                "El login debe devolver la cookie de sesión autenticada");

        return new Session(authenticatedCookie.getValue());
    }

    private void assertIdentity(Session session, DirectorAccount director) {
        webTestClient.get()
                .uri("/api/auth/me")
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(director.id())
                .jsonPath("$.email").isEqualTo(director.email())
                .jsonPath("$.displayName").isEqualTo(director.displayName());
    }

    private record DirectorAccount(
            Long id,
            String displayName,
            String email) {
    }

    private record Session(String sessionId) {
    }
}
