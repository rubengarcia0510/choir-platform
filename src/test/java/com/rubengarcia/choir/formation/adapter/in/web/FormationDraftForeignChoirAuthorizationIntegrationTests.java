package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;
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
class FormationDraftForeignChoirAuthorizationIntegrationTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long ownerDirectorId;
    private Long ownerChoirId;
    private Long foreignDirectorId;
    private Long foreignChoirId;

    FormationDraftForeignChoirAuthorizationIntegrationTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanup() {
        cleanupChoir(ownerChoirId);
        cleanupChoir(foreignChoirId);

        if (ownerDirectorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", ownerDirectorId);
        }

        if (foreignDirectorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", foreignDirectorId);
        }
    }

    @Test
    void rejectsDirectorFromAnotherChoirAndPreservesForeignDraftPositions() {
        String unique = UUID.randomUUID().toString();

        ownerDirectorId = createDirector(
                "Owner " + unique,
                "choir71-owner-" + unique + "@example.test");
        ownerChoirId = createChoir(ownerDirectorId, "Owner Choir " + unique);

        foreignDirectorId = createDirector(
                "Foreign " + unique,
                "choir71-foreign-" + unique + "@example.test");
        foreignChoirId = createChoir(
                foreignDirectorId, "Foreign Choir " + unique);

        Long concertId = createConcert(foreignChoirId, "Concert " + unique);
        Long repertoireItemId = createRepertoire(
                foreignChoirId, "Work " + unique);
        addToConcert(concertId, repertoireItemId);

        Long sectionId = createSection(foreignChoirId, "Section " + unique);
        Long singerId = createSinger(
                foreignChoirId, sectionId, "Singer " + unique);

        Long vocalPlanId = createVocalPlan(foreignChoirId, repertoireItemId);
        Long divisionId = createDivision(
                vocalPlanId, foreignChoirId, sectionId);
        addSingerToConcert(
                concertId, repertoireItemId, singerId, divisionId, foreignChoirId);

        Session foreignSession = login(
                "choir71-foreign-" + unique + "@example.test");
        Session ownerSession = login(
                "choir71-owner-" + unique + "@example.test");

        Map<String, Object> created = webTestClient.post()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}/formations",
                        foreignChoirId, concertId, repertoireItemId)
                .cookie("SESSION", foreignSession.sessionId())
                .header("X-CSRF-TOKEN", foreignSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "Foreign Formation " + unique,
                        "description", "Authorization isolation test"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertNotNull(created);

        Long formationId = ((Number) created.get("id")).longValue();
        Long versionId = ((Number) created.get("draftVersionId")).longValue();

        BigDecimal originalX = new BigDecimal("31.250");
        BigDecimal originalY = new BigDecimal("68.750");
        String originalLabel = "Foreign original position";

        Map<String, Object> originalPosition = Map.of(
                "singerId", singerId,
                "positionLabel", originalLabel,
                "x", originalX,
                "y", originalY);

        webTestClient.put()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}"
                                + "/formations/{formationId}"
                                + "/versions/{versionId}/positions",
                        foreignChoirId, concertId, repertoireItemId,
                        formationId, versionId)
                .cookie("SESSION", foreignSession.sessionId())
                .header("X-CSRF-TOKEN", foreignSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("positions", List.of(originalPosition)))
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> attemptedPosition = Map.of(
                "singerId", singerId,
                "positionLabel", "Unauthorized modification",
                "x", new BigDecimal("10.000"),
                "y", new BigDecimal("20.000"));

        webTestClient.put()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}"
                                + "/formations/{formationId}"
                                + "/versions/{versionId}/positions",
                        foreignChoirId, concertId, repertoireItemId,
                        formationId, versionId)
                .cookie("SESSION", ownerSession.sessionId())
                .header("X-CSRF-TOKEN", ownerSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("positions", List.of(attemptedPosition)))
                .exchange()
                .expectStatus().isNotFound();

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM formation_positions
                WHERE formation_version_id = ?
                """, Integer.class, versionId);

        assertEquals(1, count);

        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT singer_id, position_label, x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """, versionId);

        assertEquals(
                singerId.longValue(),
                ((Number) stored.get("singer_id")).longValue());
        assertEquals(originalLabel, stored.get("position_label"));
        assertEquals(0, originalX.compareTo(
                new BigDecimal(stored.get("x_coordinate").toString())));
        assertEquals(0, originalY.compareTo(
                new BigDecimal(stored.get("y_coordinate").toString())));
    }

    private Long createDirector(String name, String email) {
        jdbcTemplate.update("""
                INSERT INTO directors (
                    display_name, email, password_hash, email_verified
                ) VALUES (?, ?, ?, TRUE)
                """, name, email, passwordEncoder.encode(PASSWORD));

        return jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
    }

    private Long createChoir(Long directorId, String name) {
        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, name);
    }

    private Long createConcert(Long choirId, String title) {
        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, title);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
    }

    private Long createRepertoire(Long choirId, String title) {
        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, title);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
    }

    private void addToConcert(Long concertId, Long repertoireItemId) {
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index
                ) VALUES (?, ?, 0)
                """, concertId, repertoireItemId);
    }

    private Long createSection(Long choirId, String name) {
        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name, sort_order, active)
                VALUES (?, ?, 0, TRUE)
                """, choirId, name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private Long createSinger(Long choirId, Long sectionId, String name) {
        jdbcTemplate.update("""
                INSERT INTO singers (
                    choir_id, display_name, voice_part, voice_section_id, active
                ) VALUES (?, ?, ?, ?, TRUE)
                """, choirId, name, "Soprano", sectionId);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, name);
    }

    private Long createVocalPlan(Long choirId, Long repertoireItemId) {
        jdbcTemplate.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, repertoireItemId, choirId);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, Long.class, repertoireItemId, choirId);
    }

    private Long createDivision(Long planId, Long choirId, Long sectionId) {
        jdbcTemplate.update("""
                INSERT INTO vocal_plan_divisions (
                    vocal_plan_id, choir_id, vocal_section_id, division_number
                ) VALUES (?, ?, ?, 1)
                """, planId, choirId, sectionId);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                  AND division_number = 1
                """, Long.class, planId, sectionId);
    }

    private void addSingerToConcert(
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            Long divisionId,
            Long choirId) {
        jdbcTemplate.update("""
                INSERT INTO concert_vocal_assignments (
                    concert_id, repertoire_item_id, singer_id,
                    vocal_plan_division_id, choir_id
                ) VALUES (?, ?, ?, ?, ?)
                """, concertId, repertoireItemId, singerId, divisionId, choirId);
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

    private void cleanupChoir(Long choirId) {
        if (choirId == null) {
            return;
        }

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
        jdbcTemplate.update("DELETE FROM singers WHERE choir_id = ?", choirId);
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
        jdbcTemplate.update("DELETE FROM choirs WHERE id = ?", choirId);
    }

    private record Session(String sessionId, String csrfToken) {
    }
}
