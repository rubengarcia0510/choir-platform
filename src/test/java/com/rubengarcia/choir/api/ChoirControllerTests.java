package com.rubengarcia.choir.api;

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
class ChoirControllerTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private Long firstDirectorId;
    private Long secondDirectorId;

    ChoirControllerTests(
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
            jdbcTemplate.update("DELETE FROM choirs WHERE director_id = ?", firstDirectorId);
            jdbcTemplate.update("DELETE FROM directors WHERE id = ?", firstDirectorId);
            firstDirectorId = null;
        }
        if (secondDirectorId != null) {
            jdbcTemplate.update("DELETE FROM choirs WHERE director_id = ?", secondDirectorId);
            jdbcTemplate.update("DELETE FROM directors WHERE id = ?", secondDirectorId);
            secondDirectorId = null;
        }
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        webTestClient.get()
                .uri("/api/choirs")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void directorCanCreateAndListOwnChoir() throws Exception {
        DirectorAccount director = createDirector("First");
        Session session = login(director.email());

        String response = webTestClient.post()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of(
                        "name", "  Coro Central  ",
                        "description", "  Ensayo semanal  ")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> created = objectMapper.readValue(response,
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(created.get("name")).isEqualTo("Coro Central");
        assertThat(created.get("directorId")).isEqualTo(director.id().intValue());
        assertThat(created.get("archived")).isEqualTo(false);

        webTestClient.get()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("Coro Central");
    }

    @Test
    void invalidChoirNameIsRejected() throws Exception {
        DirectorAccount director = createDirector("Invalid");
        Session session = login(director.email());

        webTestClient.post()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of(
                        "name", "   ",
                        "description", "Descripción")))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void directorCannotAccessAnotherDirectorsChoir() throws Exception {
        DirectorAccount first = createDirector("Owner");
        DirectorAccount second = createDirector("Other");
        Session ownerSession = login(first.email());
        Session otherSession = login(second.email());

        String response = webTestClient.post()
                .uri("/api/choirs")
                .cookie("SESSION", ownerSession.sessionId())
                .header("X-CSRF-TOKEN", ownerSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of(
                        "name", "Privado",
                        "description", "")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> created = objectMapper.readValue(response,
                new tools.jackson.core.type.TypeReference<>() {});
        Long choirId = ((Number) created.get("id")).longValue();

        webTestClient.get()
                .uri("/api/choirs/{id}", choirId)
                .cookie("SESSION", otherSession.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.put()
                .uri("/api/choirs/{id}", choirId)
                .cookie("SESSION", otherSession.sessionId())
                .header("X-CSRF-TOKEN", otherSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Modificado", "description", ""))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri("/api/choirs/{id}", choirId)
                .cookie("SESSION", otherSession.sessionId())
                .header("X-CSRF-TOKEN", otherSession.csrfToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void directorCanUpdateAndArchiveOwnChoir() throws Exception {
        DirectorAccount director = createDirector("Lifecycle");
        Session session = login(director.email());

        String response = webTestClient.post()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Inicial", "description", "Antes"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> created = objectMapper.readValue(response,
                new tools.jackson.core.type.TypeReference<>() {});
        Long choirId = ((Number) created.get("id")).longValue();

        webTestClient.put()
                .uri("/api/choirs/{id}", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Actualizado", "description", "Después"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Actualizado")
                .jsonPath("$.description").isEqualTo("Después");

        webTestClient.delete()
                .uri("/api/choirs/{id}", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isEmpty();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/choirs")
                        .queryParam("includeArchived", true)
                        .build())
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].archived").isEqualTo(true);
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

    private Session login(String email) throws Exception {
        EntityExchangeResult<Map<String, String>> csrfResult = webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie initialCookie = csrfResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(initialCookie, "Debe crearse una sesión para CSRF");

        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken, "Debe generarse el token CSRF");

        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", PASSWORD));

        EntityExchangeResult<String> loginResult = webTestClient.post()
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
        assertNotNull(authenticatedCookie,
                "El login debe devolver la cookie de sesión autenticada");

        return new Session(authenticatedCookie.getValue(), csrfToken);
    }

    private record DirectorAccount(Long id, String displayName, String email) {}
    private record Session(String sessionId, String csrfToken) {}
}
