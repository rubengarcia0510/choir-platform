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
class FormationDraftCreationIntegrationTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long directorId;
    private Long choirId;

    FormationDraftCreationIntegrationTests(
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

        if (directorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?",
                    directorId);
            directorId = null;
        }
    }

    @Test
    void createsFormationAssociatedWithExactOccurrenceAndInitialDraft() {
        String unique = UUID.randomUUID().toString();
        String email = "formation-create-" + unique + "@example.test";
        String choirName = "Formation Creation Choir " + unique;
        String concertTitle = "Formation Creation Concert " + unique;
        String repertoireTitle = "Formation Creation Work " + unique;

        jdbcTemplate.update(
                """
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Formation Creation Director " + unique,
                email,
                passwordEncoder.encode(PASSWORD));

        directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);
        assertNotNull(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                choirName);

        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                choirName);
        assertNotNull(choirId);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                concertTitle);

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                concertTitle);
        assertNotNull(concertId);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                repertoireTitle);

        Long repertoireItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """,
                Long.class,
                choirId,
                repertoireTitle);
        assertNotNull(repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, ?)
                """,
                concertId,
                repertoireItemId,
                1);

        CsrfSession csrf = obtainCsrf();
        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", csrf.sessionId())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(loginBody)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticatedCookie);

        EntityExchangeResult<Map<String, Object>> createResult =
                webTestClient.post()
                        .uri(
                                "/api/choirs/{choirId}/concerts/{concertId}"
                                        + "/repertoire/{repertoireItemId}/formations",
                                choirId,
                                concertId,
                                repertoireItemId)
                        .cookie("SESSION", authenticatedCookie.getValue())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "name", "Formación integración",
                                "description", "Verificación de asociación y borrador inicial"))
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        Map<String, Object>>() {})
                        .returnResult();

        Map<String, Object> response = createResult.getResponseBody();
        assertNotNull(response);

        assertEquals("Formación integración", response.get("name"));
        assertEquals(
                "Verificación de asociación y borrador inicial",
                response.get("description"));
        assertEquals("DRAFT", response.get("status"));
        assertEquals(1, ((Number) response.get("versionNumber")).intValue());

        Long formationId = ((Number) response.get("id")).longValue();
        Long draftVersionId =
                ((Number) response.get("draftVersionId")).longValue();

        assertNotNull(formationId);
        assertNotNull(draftVersionId);

        Map<String, Object> formationRow = jdbcTemplate.queryForMap(
                """
                SELECT choir_id, concert_id, repertoire_item_id
                FROM formations
                WHERE id = ?
                """,
                formationId);

        assertEquals(
                choirId.longValue(),
                ((Number) formationRow.get("choir_id")).longValue());
        assertEquals(
                concertId.longValue(),
                ((Number) formationRow.get("concert_id")).longValue());
        assertEquals(
                repertoireItemId.longValue(),
                ((Number) formationRow.get("repertoire_item_id")).longValue());

        Map<String, Object> versionRow = jdbcTemplate.queryForMap(
                """
                SELECT formation_id, version_number, status
                FROM formation_versions
                WHERE id = ?
                """,
                draftVersionId);

        assertEquals(
                formationId.longValue(),
                ((Number) versionRow.get("formation_id")).longValue());
        assertEquals(
                1,
                ((Number) versionRow.get("version_number")).intValue());
        assertEquals("DRAFT", versionRow.get("status"));
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

    private record CsrfSession(String sessionId, String token) {}
}
