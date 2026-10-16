package com.rubengarcia.choir.domain;

import java.time.Duration;

import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureWebTestClient
class DirectorAuthenticationTests {

    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DirectorRepository directorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;
    private final String password = "Choir-Test-Password-2026!";

    @BeforeEach
    void createDirector() {
        email = "auth-" + UUID.randomUUID() + "@example.test";

        Director director = new Director();
        director.setDisplayName("Test Director");
        director.setEmail(email);
        director.setPasswordHash(passwordEncoder.encode(password));
        director.setEmailVerified(true);

        directorRepository.save(director).block(Duration.ofSeconds(10));
    }

    @Test
    void unauthenticatedRequestToMeReturns401() {
        webTestClient.get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void directorWithoutPasswordHashCannotLogin() throws Exception {
        String unconfiguredEmail =
                "no-password-" + UUID.randomUUID() + "@example.test";

        Director director = new Director();
        director.setDisplayName("Director Without Password");
        director.setEmail(unconfiguredEmail);
        director.setPasswordHash(null);
        directorRepository.save(director).block(Duration.ofSeconds(10));

        CsrfSession csrf = obtainCsrf();

        webTestClient.post()
                .uri("/api/auth/login")
                .cookie(SESSION_COOKIE, csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of(
                                "email", unconfiguredEmail,
                                "password", "Any-Password-2026!"
                        )))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void directorWithUnverifiedEmailCannotLogin() throws Exception {
        String unverifiedEmail =
                "unverified-" + UUID.randomUUID() + "@example.test";

        Director director = new Director();
        director.setDisplayName("Unverified Director");
        director.setEmail(unverifiedEmail);
        director.setPasswordHash(passwordEncoder.encode(password));
        directorRepository.save(director).block(Duration.ofSeconds(10));

        CsrfSession csrf = obtainCsrf();

        webTestClient.post()
                .uri("/api/auth/login")
                .cookie(SESSION_COOKIE, csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of("email", unverifiedEmail, "password", password)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginWithInvalidPasswordReturns401() throws Exception {
        CsrfSession csrf = obtainCsrf();

        webTestClient.post()
                .uri("/api/auth/login")
                .cookie(SESSION_COOKIE, csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", "wrong-password")))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, csrf.sessionId())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginAndMeReturnAuthenticatedDirector() throws Exception {
        CsrfSession csrf = obtainCsrf();

        LoginResult login = login(csrf, password);
        assertNotNull(login.sessionId());
        assertNotEquals(csrf.sessionId(), login.sessionId(),
                "El identificador de sesión debe cambiar después del login");
        assertTrue(login.body().contains(email));

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, login.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains(email)));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", password)))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        CsrfSession csrf = obtainCsrf();
        LoginResult login = login(csrf, password);

        webTestClient.post()
                .uri("/api/auth/logout")
                .cookie(SESSION_COOKIE, login.sessionId())
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, login.sessionId())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        CsrfSession csrf = obtainCsrf();
        LoginResult login = login(csrf, password);
        CsrfSession authenticatedCsrf = obtainCsrf(login.sessionId());

        webTestClient.post()
                .uri("/api/auth/logout")
                .cookie(SESSION_COOKIE, login.sessionId())
                .header("X-CSRF-TOKEN", authenticatedCsrf.token())
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, login.sessionId())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private LoginResult login(CsrfSession csrf, String suppliedPassword) throws Exception {
        EntityExchangeResult<String> result = webTestClient.post()
                .uri("/api/auth/login")
                .cookie(SESSION_COOKIE, csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", suppliedPassword)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie cookie = result.getResponseCookies().getFirst(SESSION_COOKIE);
        assertNotNull(cookie, "El login debe devolver la cookie de sesión nueva");
        return new LoginResult(cookie.getValue(), result.getResponseBody());
    }

    private CsrfSession obtainCsrf() {
        return obtainCsrf(null);
    }

    private CsrfSession obtainCsrf(String existingSessionId) {
        WebTestClient.RequestHeadersSpec<?> request = webTestClient.get()
                .uri("/api/auth/csrf");

        if (existingSessionId != null) {
            request = request.cookie(SESSION_COOKIE, existingSessionId);
        }

        EntityExchangeResult<Map<String, String>> result = request.exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie cookie = result.getResponseCookies().getFirst(SESSION_COOKIE);
        String sessionId = cookie != null
                ? cookie.getValue()
                : existingSessionId;
        assertNotNull(sessionId, "El endpoint CSRF debe crear o reutilizar una sesión");
        String token = result.getResponseBody().get("token");
        assertNotNull(token);

        return new CsrfSession(sessionId, token);
    }

    private record CsrfSession(String sessionId, String token) {
    }

    private record LoginResult(String sessionId, String body) {
    }
}
