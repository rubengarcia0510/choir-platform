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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "server.reactive.session.timeout=5s")
@AutoConfigureWebTestClient
class DirectorAuthenticatedSessionExpirationTests {

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
        email = "expiration-" + UUID.randomUUID() + "@example.test";

        Director director = new Director();
        director.setDisplayName("Session Expiration Test");
        director.setEmail(email);
        director.setPasswordHash(passwordEncoder.encode(PASSWORD));
        director.setEmailVerified(true);

        directorRepository.save(director).block(Duration.ofSeconds(10));
    }

    @Test
    void expiredAuthenticatedSessionCanNoLongerAccessMe() throws Exception {
        EntityExchangeResult<Map<String, String>> csrfResult =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<Map<String, String>>() {})
                        .returnResult();

        ResponseCookie initialCookie =
                csrfResult.getResponseCookies().getFirst(SESSION_COOKIE);
        assertNotNull(initialCookie, "Debe crearse la sesión inicial");

        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken, "Debe devolverse el token CSRF");

        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/auth/login")
                .cookie(SESSION_COOKIE, initialCookie.getValue())
                .header("X-CSRF-TOKEN", csrfToken)
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
                "El login debe devolver la cookie autenticada");

        String sessionId = authenticatedCookie.getValue();

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, sessionId)
                .exchange()
                .expectStatus().isOk();

        Thread.sleep(6000);

        webTestClient.get()
                .uri("/api/auth/me")
                .cookie(SESSION_COOKIE, sessionId)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
