package com.rubengarcia.choir.formation.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationDraftListingIntegrationTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long directorId;
    private Long choirId;

    FormationDraftListingIntegrationTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanup() {
        if (choirId == null) {
            cleanupDirector();
            return;
        }

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
        cleanupDirector();
    }

    @Test
    void listsAllFormationsForExactOccurrenceAndReadsInitialDraft() {
        String unique = UUID.randomUUID().toString();
        String email = "formation-list-" + unique + "@example.test";

        jdbcTemplate.update(
                """
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Formation Listing Director " + unique,
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
                "Formation Listing Choir " + unique);

        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                "Formation Listing Choir " + unique);
        assertNotNull(choirId);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                "Formation Listing Concert " + unique);

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Formation Listing Concert " + unique);
        assertNotNull(concertId);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                "Formation Listing Work " + unique);

        Long repertoireItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """,
                Long.class,
                choirId,
                "Formation Listing Work " + unique);
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

        Session session = login(email);

        Long firstFormationId = createFormation(
                session,
                choirId,
                concertId,
                repertoireItemId,
                "Formación A " + unique);

        Long secondFormationId = createFormation(
                session,
                choirId,
                concertId,
                repertoireItemId,
                "Formación B " + unique);

        EntityExchangeResult<List<Map<String, Object>>> listResult =
                webTestClient.get()
                        .uri(
                                "/api/choirs/{choirId}/concerts/{concertId}"
                                        + "/repertoire/{repertoireItemId}/formations",
                                choirId,
                                concertId,
                                repertoireItemId)
                        .cookie("SESSION", session.sessionId())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        List<Map<String, Object>>>() {})
                        .returnResult();

        List<Map<String, Object>> formations =
                listResult.getResponseBody();
        assertNotNull(formations);

        Set<Long> returnedIds = formations.stream()
                .map(item -> ((Number) item.get("id")).longValue())
                .collect(Collectors.toSet());

        assertTrue(
                returnedIds.contains(firstFormationId),
                "El listado debe incluir la primera formación");
        assertTrue(
                returnedIds.contains(secondFormationId),
                "El listado debe incluir la segunda formación");

        assertEquals(
                2,
                formations.stream()
                        .filter(item -> {
                            long id = ((Number) item.get("id")).longValue();
                            return id == firstFormationId
                                    || id == secondFormationId;
                        })
                        .count(),
                "Ambas formaciones deben aparecer una sola vez");

        EntityExchangeResult<Map<String, Object>> draftResult =
                webTestClient.get()
                        .uri(
                                "/api/choirs/{choirId}/concerts/{concertId}"
                                        + "/repertoire/{repertoireItemId}"
                                        + "/formations/{formationId}/draft",
                                choirId,
                                concertId,
                                repertoireItemId,
                                firstFormationId)
                        .cookie("SESSION", session.sessionId())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        Map<String, Object>>() {})
                        .returnResult();

        Map<String, Object> draft = draftResult.getResponseBody();
        assertNotNull(draft);

        assertEquals(
                firstFormationId.longValue(),
                ((Number) draft.get("formationId")).longValue());
        assertEquals("Formación A " + unique, draft.get("name"));
        assertEquals("DRAFT", draft.get("status"));
        assertEquals(
                1,
                ((Number) draft.get("versionNumber")).intValue());
        assertNotNull(draft.get("draftVersionId"));
        assertNotNull(draft.get("createdAt"));
        assertNotNull(draft.get("positions"));
        assertTrue(
                ((List<?>) draft.get("positions")).isEmpty(),
                "La versión inicial debe tener las posiciones vacías");
    }

    private Long createFormation(
            Session session,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            String name) {
        EntityExchangeResult<Map<String, Object>> result =
                webTestClient.post()
                        .uri(
                                "/api/choirs/{choirId}/concerts/{concertId}"
                                        + "/repertoire/{repertoireItemId}/formations",
                                choirId,
                                concertId,
                                repertoireItemId)
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "name", name,
                                "description", "Prueba de listado"))
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        Map<String, Object>>() {})
                        .returnResult();

        Map<String, Object> body = result.getResponseBody();
        assertNotNull(body);
        assertEquals(name, body.get("name"));
        assertEquals("DRAFT", body.get("status"));
        assertEquals(1, ((Number) body.get("versionNumber")).intValue());

        return ((Number) body.get("id")).longValue();
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

        ResponseCookie csrfCookie =
                csrfResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(csrfCookie);
        assertNotNull(csrfResult.getResponseBody());

        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken);

        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", csrfCookie.getValue())
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(loginBody)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticatedCookie);

        return new Session(authenticatedCookie.getValue(), csrfToken);
    }

    private void cleanupDirector() {
        if (directorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?",
                    directorId);
            directorId = null;
        }
    }

    private record Session(String sessionId, String csrfToken) {}
}
