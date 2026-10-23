package com.rubengarcia.choir.formation.adapter.in.web;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationDuplicationAuthorizationControllerTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long ownerDirectorId;
    private Long otherDirectorId;
    private Long choirId;

    FormationDuplicationAuthorizationControllerTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanup() {
        if (choirId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM formation_positions
                    WHERE formation_version_id IN (
                        SELECT fv.id
                        FROM formation_versions fv
                        JOIN formations f ON f.id = fv.formation_id
                        WHERE f.choir_id = ?
                    )
                    """,
                    choirId);

            jdbcTemplate.update(
                    """
                    DELETE FROM formation_versions
                    WHERE formation_id IN (
                        SELECT id FROM formations WHERE choir_id = ?
                    )
                    """,
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM formations WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    """
                    DELETE FROM concert_repertoire
                    WHERE concert_id IN (
                        SELECT id FROM concerts WHERE choir_id = ?
                    )
                    """,
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concerts WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM choirs WHERE id = ?",
                    choirId);

            choirId = null;
        }

        if (ownerDirectorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?",
                    ownerDirectorId);
            ownerDirectorId = null;
        }

        if (otherDirectorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?",
                    otherDirectorId);
            otherDirectorId = null;
        }
    }

    @Test
    void directorCannotDuplicateAnotherDirectorsFormation() {
        Fixture fixture = createFixture();
        Session otherDirectorSession = login(fixture.otherDirectorEmail());

        Long formationsBefore = countFormations();

        webTestClient.post()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}"
                                + "/formations/{formationId}/duplicate",
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId())
                .cookie("SESSION", otherDirectorSession.sessionId())
                .header("X-CSRF-TOKEN", otherDirectorSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "Copia no autorizada " + fixture.suffix()))
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(
                formationsBefore,
                countFormations(),
                "Un director no autorizado no debe crear otra formación");
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "dup-owner-" + suffix + "@example.test";
        String otherEmail = "dup-other-" + suffix + "@example.test";

        jdbcTemplate.update(
                """
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Duplication Owner " + suffix,
                ownerEmail,
                passwordEncoder.encode(PASSWORD));

        ownerDirectorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                ownerEmail);
        assertNotNull(ownerDirectorId);

        jdbcTemplate.update(
                """
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Duplication Other Director " + suffix,
                otherEmail,
                passwordEncoder.encode(PASSWORD));

        otherDirectorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                otherEmail);
        assertNotNull(otherDirectorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                ownerDirectorId,
                "Duplication Authorization Choir " + suffix);

        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                ownerDirectorId,
                "Duplication Authorization Choir " + suffix);
        assertNotNull(choirId);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                "Concert " + suffix);

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Concert " + suffix);
        assertNotNull(concertId);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                "Work " + suffix);

        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Work " + suffix);
        assertNotNull(repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """,
                concertId,
                repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, ?)
                """,
                choirId,
                concertId,
                repertoireItemId,
                "Owner Formation " + suffix,
                "Formation belonging to another director");

        Long formationId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """,
                Long.class,
                choirId,
                concertId,
                repertoireItemId,
                "Owner Formation " + suffix);
        assertNotNull(formationId);

        jdbcTemplate.update(
                """
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """,
                formationId);

        return new Fixture(
                suffix,
                otherEmail,
                choirId,
                concertId,
                repertoireItemId,
                formationId);
    }

    private Long countFormations() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                Long.class,
                choirId);
    }

    private CsrfSession obtainCsrf() {
        EntityExchangeResult<Map<String, String>> result =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        Map<String, String>>() {})
                        .returnResult();

        ResponseCookie cookie =
                result.getResponseCookies().getFirst("SESSION");

        assertNotNull(cookie, "El endpoint CSRF debe crear una sesión");
        assertNotNull(result.getResponseBody());

        String token = result.getResponseBody().get("token");
        assertNotNull(token, "El endpoint CSRF debe devolver un token");

        return new CsrfSession(cookie.getValue(), token);
    }

    private Session login(String email) {
        CsrfSession csrf = obtainCsrf();

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", csrf.sessionId())
                        .header("X-CSRF-TOKEN", csrf.token())
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

        return new Session(authenticatedCookie.getValue(), csrf.token());
    }

    private record Fixture(
            String suffix,
            String otherDirectorEmail,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long formationId) {}

    private record Session(String sessionId, String csrfToken) {}

    private record CsrfSession(String sessionId, String token) {}
}
