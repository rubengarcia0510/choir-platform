package com.rubengarcia.choir.domain;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
class DirectorSessionRotationTests {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String PASSWORD = "Choir-Test-Password-2026!";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DirectorRepository directorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;

    @BeforeEach
    void createDirector() {
        email = "rotation-" + UUID.randomUUID() + "@example.test";

        Director director = new Director();
        director.setDisplayName("Session Rotation Test");
        director.setEmail(email);
        director.setPasswordHash(passwordEncoder.encode(PASSWORD));
        director.setEmailVerified(true);

        directorRepository.save(director).block(Duration.ofSeconds(10));
    }

    @Test
    void previousSessionIdCannotAuthenticateAfterLogin() throws Exception {
        CsrfSession anonymousSession = obtainCsrf();

        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/auth/login")
                .cookie(SESSION_COOKIE, anonymousSession.sessionId())
                .header("X-CSRF-TOKEN", anonymousSession.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst(SESSION_COOKIE);

        assertNotNull(authenticatedCookie,
                "El login debe devolver la cookie de sesión autenticada");

        String authenticatedSessionId = authenticatedCookie.getValue();

        assertNotEquals(anonymousSession.sessionId(), authenticatedSessionId,
                "El login debe rotar el identificador de sesión");

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, authenticatedSessionId)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, anonymousSession.sessionId())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private CsrfSession obtainCsrf() {
        EntityExchangeResult<Map<String, String>> result = webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie cookie =
                result.getResponseCookies().getFirst(SESSION_COOKIE);

        assertNotNull(cookie, "El endpoint CSRF debe crear una sesión");

        String token = result.getResponseBody().get("token");
        assertNotNull(token, "El endpoint CSRF debe devolver un token");

        return new CsrfSession(cookie.getValue(), token);
    }

    private record CsrfSession(String sessionId, String token) {
    }
}
