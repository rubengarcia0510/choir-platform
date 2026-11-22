package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationMobilityConstraintControllerTests {

    private static final String PASSWORD = "Mobility-Constraint-Test-2026!";

    private static final String BASE_URI =
            "/api/choirs/1/concerts/2/repertoire/3/formations/4"
                    + "/constraints/physical/mobility";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final List<String> directorEmails = new ArrayList<>();

    FormationMobilityConstraintControllerTests(
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
    void cleanUp() {
        for (String email : directorEmails) {
            jdbcTemplate.update("DELETE FROM directors WHERE email = ?", email);
        }
        directorEmails.clear();
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        webTestClient.get()
                .uri(BASE_URI)
                .exchange()
                .expectStatus().isUnauthorized();

        Session anonymous = obtainCsrf();

        webTestClient.post()
                .uri(BASE_URI)
                .cookie("SESSION", anonymous.sessionId())
                .header("X-CSRF-TOKEN", anonymous.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateBody())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createRejectsMissingBoundsWithBadRequest() throws Exception {
        Session session = login(createDirector());

        webTestClient.post()
                .uri(BASE_URI)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("singerId", 10, "minX", 1, "minY", 0))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createRejectsNonIncreasingRegionWithBadRequest() throws Exception {
        Session session = login(createDirector());

        webTestClient.post()
                .uri(BASE_URI)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "singerId", 10,
                        "minX", "5.000",
                        "maxX", "5.000",
                        "minY", "0.000",
                        "maxY", "10.000"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void directorWithoutOwnershipGetsNotFoundForEveryOperation() throws Exception {
        Session session = login(createDirector());

        webTestClient.get()
                .uri(BASE_URI)
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post()
                .uri(BASE_URI)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateBody())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.put()
                .uri(BASE_URI + "/5")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "minX", "1.000", "maxX", "2.000",
                        "minY", "1.000", "maxY", "2.000",
                        "active", true))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri(BASE_URI + "/5")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    private Map<String, Object> validCreateBody() {
        return Map.of(
                "singerId", 10,
                "minX", "1.250",
                "maxX", "8.750",
                "minY", "-0.500",
                "maxY", "12.000");
    }

    private Session obtainCsrf() {
        EntityExchangeResult<Map<String, String>> result =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(new ParameterizedTypeReference<
                                Map<String, String>>() {})
                        .returnResult();

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertNotNull(cookie);
        String token = result.getResponseBody().get("token");
        assertNotNull(token);
        return new Session(cookie.getValue(), token);
    }

    private String createDirector() {
        String unique = UUID.randomUUID().toString();
        String email = "mobility-" + unique + "@example.test";

        jdbcTemplate.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Director Mobility " + unique,
                email,
                passwordEncoder.encode(PASSWORD));

        directorEmails.add(email);
        return email;
    }

    private Session login(String email) throws Exception {
        Session initial = obtainCsrf();

        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/auth/login")
                .cookie("SESSION", initial.sessionId())
                .header("X-CSRF-TOKEN", initial.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticated =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticated);

        return new Session(authenticated.getValue(), initial.csrfToken());
    }

    private record Session(String sessionId, String csrfToken) {
    }
}
