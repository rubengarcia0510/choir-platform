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
class SingerHeightControllerTests {

    private static final String PASSWORD = "Singer-Height-Test-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private Long directorId;

    SingerHeightControllerTests(
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
        if (directorId == null) {
            return;
        }

        jdbcTemplate.update("""
                DELETE FROM concert_cast_snapshots
                WHERE concert_id IN (
                    SELECT id FROM concerts
                    WHERE choir_id IN (
                        SELECT id FROM choirs WHERE director_id = ?
                    )
                )
                """, directorId);

        jdbcTemplate.update("""
                DELETE FROM concerts
                WHERE choir_id IN (
                    SELECT id FROM choirs WHERE director_id = ?
                )
                """, directorId);

        jdbcTemplate.update("""
                DELETE FROM singers
                WHERE choir_id IN (
                    SELECT id FROM choirs WHERE director_id = ?
                )
                """, directorId);

        jdbcTemplate.update("""
                DELETE FROM vocal_sections
                WHERE choir_id IN (
                    SELECT id FROM choirs WHERE director_id = ?
                )
                """, directorId);

        jdbcTemplate.update(
                "DELETE FROM choirs WHERE director_id = ?", directorId);

        jdbcTemplate.update(
                "DELETE FROM directors WHERE id = ?", directorId);

        directorId = null;
    }

    @Test
    void directorCanCreateUpdateAndReadSingerHeightAndOmitItWhenUnknown()
            throws Exception {
        Director director = createDirector();
        Session session = login(director.email());
        Long choirId = createChoir(session);

        Long singerId = createSinger(
                session, choirId, "Ana Pérez", "ana@example.test", 172);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT height_cm FROM singers WHERE id = ?",
                Integer.class,
                singerId)).isEqualTo(172);

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers/{singerId}",
                        choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.heightCm").isEqualTo(172);

        webTestClient.put()
                .uri("/api/choirs/{choirId}/singers/{singerId}",
                        choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "displayName", "Ana Pérez",
                        "email", "ana@example.test",
                        "voicePart", "Soprano",
                        "mobility", "",
                        "restrictions", "",
                        "heightCm", 175))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.heightCm").isEqualTo(175);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT height_cm FROM singers WHERE id = ?",
                Integer.class,
                singerId)).isEqualTo(175);

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers/{singerId}",
                        choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.heightCm").isEqualTo(175);

        Long singerWithoutHeight = createSinger(
                session, choirId, "Luis Gómez", "luis@example.test", null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT height_cm FROM singers WHERE id = ?",
                Integer.class,
                singerWithoutHeight)).isNull();

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers/{singerId}",
                        choirId, singerWithoutHeight)
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.heightCm").doesNotExist();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM singers WHERE choir_id = ?",
                Integer.class,
                choirId)).isEqualTo(2);
    }

    @Test
    void zeroAndNegativeSingerHeightsAreRejected() throws Exception {
        Director director = createDirector();
        Session session = login(director.email());
        Long choirId = createChoir(session);

        webTestClient.post()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "displayName", "Altura cero",
                        "email", "zero@example.test",
                        "voicePart", "Soprano",
                        "heightCm", 0))
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.post()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "displayName", "Altura negativa",
                        "email", "negative@example.test",
                        "voicePart", "Soprano",
                        "heightCm", -1))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM singers WHERE choir_id = ?",
                Integer.class,
                choirId)).isZero();
    }

    private Long createSinger(
            Session session,
            Long choirId,
            String name,
            String email,
            Integer heightCm) throws Exception {

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("displayName", name);
        request.put("email", email);
        request.put("voicePart", "Soprano");

        if (heightCm != null) {
            request.put("heightCm", heightCm);
        }

        String response = webTestClient.post()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);

        Map<String, Object> body = objectMapper.readValue(
                response,
                new tools.jackson.core.type.TypeReference<>() {});

        return ((Number) body.get("id")).longValue();
    }

    private Long createChoir(Session session) throws Exception {
        String response = webTestClient.post()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "Coro altura " + UUID.randomUUID(),
                        "description", ""))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);

        Map<String, Object> body = objectMapper.readValue(
                response,
                new tools.jackson.core.type.TypeReference<>() {});

        return ((Number) body.get("id")).longValue();
    }

    private Director createDirector() {
        String unique = UUID.randomUUID().toString();
        String email = "height-" + unique + "@example.test";

        jdbcTemplate.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Director Height " + unique,
                email,
                passwordEncoder.encode(PASSWORD));

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);

        assertNotNull(id);
        directorId = id;

        return new Director(id, email);
    }

    private Session login(String email) throws Exception {
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
                        .bodyValue(objectMapper.writeValueAsString(
                                Map.of(
                                        "email", email,
                                        "password", PASSWORD)))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");

        assertNotNull(authenticatedCookie);

        return new Session(
                authenticatedCookie.getValue(),
                csrfToken);
    }

    private record Director(Long id, String email) {}

    private record Session(String sessionId, String csrfToken) {}
}
