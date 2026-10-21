package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
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

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationDraftForeignVersionIntegrationTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long directorId;
    private Long choirId;

    FormationDraftForeignVersionIntegrationTests(
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
            jdbcTemplate.update("""
                    DELETE FROM formation_positions
                    WHERE formation_version_id IN (
                        SELECT fv.id FROM formation_versions fv
                        JOIN formations f ON f.id = fv.formation_id
                        WHERE f.choir_id = ?
                    )
                    """, choirId);
            jdbcTemplate.update("""
                    DELETE FROM formation_versions
                    WHERE formation_id IN (
                        SELECT id FROM formations WHERE choir_id = ?
                    )
                    """, choirId);
            jdbcTemplate.update(
                    "DELETE FROM formations WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM concert_vocal_assignments WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_plan_divisions WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_plans WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?", choirId);
            jdbcTemplate.update("""
                    DELETE FROM concert_repertoire
                    WHERE concert_id IN (
                        SELECT id FROM concerts WHERE choir_id = ?
                    )
                    """, choirId);
            jdbcTemplate.update(
                    "DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM choirs WHERE id = ?", choirId);
            choirId = null;
        }

        if (directorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", directorId);
            directorId = null;
        }
    }

    @Test
    void rejectsVersionFromAnotherFormationAndPreservesItsPositions() {
        String unique = UUID.randomUUID().toString();
        String email = "choir71-foreign-version-" + unique + "@example.test";
        String choirName = "Foreign Version Choir " + unique;
        String concertName = "Foreign Version Concert " + unique;
        String repertoireName = "Foreign Version Repertoire " + unique;
        String sectionName = "Foreign Version Section " + unique;
        String singerName = "Foreign Version Singer " + unique;
        String firstFormationName = "First Formation " + unique;
        String secondFormationName = "Second Formation " + unique;

        jdbcTemplate.update("""
                INSERT INTO directors (
                    display_name, email, password_hash, email_verified
                ) VALUES (?, ?, ?, TRUE)
                """,
                "Foreign Version Director " + unique,
                email,
                passwordEncoder.encode(PASSWORD));

        directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        assertNotNull(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);
        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, choirName);
        assertNotNull(choirId);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, concertName);
        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, concertName);
        assertNotNull(concertId);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, repertoireName);
        Long repertoireItemId = jdbcTemplate.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, repertoireName);
        assertNotNull(repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index
                ) VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name, sort_order, active)
                VALUES (?, ?, 0, TRUE)
                """, choirId, sectionName);
        Long sectionId = jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_sections
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, sectionName);
        assertNotNull(sectionId);

        jdbcTemplate.update("""
                INSERT INTO singers (
                    choir_id, display_name, voice_part, voice_section_id, active
                ) VALUES (?, ?, ?, ?, TRUE)
                """, choirId, singerName, "Soprano", sectionId);
        Long singerId = jdbcTemplate.queryForObject("""
                SELECT id FROM singers
                WHERE choir_id = ? AND display_name = ?
                """, Long.class, choirId, singerName);
        assertNotNull(singerId);

        jdbcTemplate.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, repertoireItemId, choirId);
        Long vocalPlanId = jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, Long.class, repertoireItemId, choirId);
        assertNotNull(vocalPlanId);

        jdbcTemplate.update("""
                INSERT INTO vocal_plan_divisions (
                    vocal_plan_id, choir_id, vocal_section_id, division_number
                ) VALUES (?, ?, ?, 1)
                """, vocalPlanId, choirId, sectionId);
        Long divisionId = jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                  AND division_number = 1
                """, Long.class, vocalPlanId, sectionId);
        assertNotNull(divisionId);

        jdbcTemplate.update("""
                INSERT INTO concert_vocal_assignments (
                    concert_id, repertoire_item_id, singer_id,
                    vocal_plan_division_id, choir_id
                ) VALUES (?, ?, ?, ?, ?)
                """, concertId, repertoireItemId, singerId, divisionId, choirId);

        Session session = login(email);

        Map<String, Object> firstFormation = createFormation(
                session, choirId, concertId, repertoireItemId, firstFormationName);
        Map<String, Object> secondFormation = createFormation(
                session, choirId, concertId, repertoireItemId, secondFormationName);

        Long firstFormationId =
                ((Number) firstFormation.get("id")).longValue();
        Long secondFormationId =
                ((Number) secondFormation.get("id")).longValue();
        Long firstVersionId =
                ((Number) firstFormation.get("draftVersionId")).longValue();
        Long secondVersionId =
                ((Number) secondFormation.get("draftVersionId")).longValue();

        assertNotNull(firstFormationId);
        assertNotNull(secondFormationId);
        assertNotNull(firstVersionId);
        assertNotNull(secondVersionId);

        Integer originalCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM formation_positions
                WHERE formation_version_id = ?
                """, Integer.class, secondVersionId);
        assertEquals(1, originalCount);

        Map<String, Object> originalPosition = jdbcTemplate.queryForMap("""
                SELECT singer_id, singer_name_snapshot, position_label,
                       x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """, secondVersionId);

        // La URL identifica la primera formación, pero se envía el ID
        // de versión de la segunda. Esa combinación debe rechazarse.
        webTestClient.put()
                .uri("/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations/{formationId}/versions/{versionId}/positions",
                        choirId, concertId, repertoireItemId,
                        firstFormationId, secondVersionId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("positions", java.util.List.of()))
                .exchange()
                .expectStatus().isNotFound();

        Integer countAfterRejection = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM formation_positions
                WHERE formation_version_id = ?
                """, Integer.class, secondVersionId);
        assertEquals(1, countAfterRejection);

        Map<String, Object> storedAfterRejection = jdbcTemplate.queryForMap("""
                SELECT singer_id, singer_name_snapshot, position_label,
                       x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """, secondVersionId);

        assertEquals(
                ((Number) originalPosition.get("singer_id")).longValue(),
                ((Number) storedAfterRejection.get("singer_id")).longValue());
        assertEquals(
                originalPosition.get("singer_name_snapshot"),
                storedAfterRejection.get("singer_name_snapshot"));
        assertEquals(
                originalPosition.get("position_label"),
                storedAfterRejection.get("position_label"));
        assertEquals(0, new BigDecimal(
                originalPosition.get("x_coordinate").toString()).compareTo(
                        new BigDecimal(
                                storedAfterRejection.get("x_coordinate").toString())));
        assertEquals(0, new BigDecimal(
                originalPosition.get("y_coordinate").toString()).compareTo(
                        new BigDecimal(
                                storedAfterRejection.get("y_coordinate").toString())));
    }

    private Map<String, Object> createFormation(
            Session session,
            Long choir,
            Long concert,
            Long repertoireItem,
            String name) {
        EntityExchangeResult<Map<String, Object>> result =
                webTestClient.post()
                        .uri("/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations",
                                choir, concert, repertoireItem)
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "name", name,
                                "description", "Foreign version association test"))
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(
                                new ParameterizedTypeReference<Map<String, Object>>() {})
                        .returnResult();

        Map<String, Object> body = result.getResponseBody();
        assertNotNull(body);
        assertEquals(name, body.get("name"));
        assertEquals("DRAFT", body.get("status"));
        return body;
    }

    private Session login(String email) {
        EntityExchangeResult<Map<String, String>> csrfResult =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<Map<String, String>>() {})
                        .returnResult();

        ResponseCookie csrfCookie =
                csrfResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(csrfCookie);

        Map<String, String> csrfBody = csrfResult.getResponseBody();
        assertNotNull(csrfBody);
        String csrfToken = csrfBody.get("token");
        assertNotNull(csrfToken);

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", csrfCookie.getValue())
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("email", email, "password", PASSWORD))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticatedCookie);

        return new Session(authenticatedCookie.getValue(), csrfToken);
    }

    private record Session(String sessionId, String csrfToken) {
    }
}
