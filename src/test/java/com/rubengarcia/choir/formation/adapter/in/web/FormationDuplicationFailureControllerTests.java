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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationDuplicationFailureControllerTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long directorId;
    private Long choirId;

    FormationDuplicationFailureControllerTests(
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
                        SELECT fv.id
                        FROM formation_versions fv
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
            choirId = null;
        }

        if (directorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", directorId);
            directorId = null;
        }
    }

    @Test
    void archivedOnlySourceReturnsConflictWithoutCreatingFormation() {
        Fixture fixture = createFixture();
        Session session = login(fixture.email());

        jdbcTemplate.update("""
                UPDATE formation_versions
                SET status = 'ARCHIVED'
                WHERE formation_id = ? AND version_number = 1
                """, fixture.formationId());

        int formationsBefore = countFormations();

        webTestClient.post()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}"
                                + "/formations/{formationId}/duplicate",
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId())
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Duplicación sin fuente válida"))
                .exchange()
                .expectStatus().isEqualTo(409);

        assertThat(countFormations()).isEqualTo(formationsBefore);
    }

    private int countFormations() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                Integer.class,
                choirId);
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "dup-failure-" + suffix + "@example.test";
        String choirName = "Dup Failure Choir " + suffix;
        String concertName = "Concert " + suffix;
        String repertoireName = "Work " + suffix;
        String formationName = "Source Formation " + suffix;

        jdbcTemplate.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Dup Failure Director " + suffix,
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
                concertName);

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                concertName);
        assertNotNull(concertId);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                repertoireName);

        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                repertoireName);
        assertNotNull(repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """,
                concertId,
                repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, ?)
                """,
                choirId,
                concertId,
                repertoireItemId,
                formationName,
                "Source for archived-only duplication test");

        Long formationId = jdbcTemplate.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """,
                Long.class,
                choirId,
                concertId,
                repertoireItemId,
                formationName);
        assertNotNull(formationId);

        jdbcTemplate.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """,
                formationId);

        return new Fixture(
                email, choirId, concertId, repertoireItemId, formationId);
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

        assertNotNull(cookie);
        assertNotNull(result.getResponseBody());

        String token = result.getResponseBody().get("token");
        assertNotNull(token);

        return new CsrfSession(cookie.getValue(), token);
    }

    private Session login(String email) {
        CsrfSession csrf = obtainCsrf();

        EntityExchangeResult<String> result =
                webTestClient.post()
                        .uri("/api/auth/login")
                        .cookie("SESSION", csrf.sessionId())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(String.class)
                        .returnResult();

        ResponseCookie cookie =
                result.getResponseCookies().getFirst("SESSION");

        assertNotNull(cookie, "El login debe devolver la sesión autenticada");

        return new Session(cookie.getValue(), csrf.token());
    }

    private record Fixture(
            String email,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long formationId) {}

    private record Session(String sessionId, String csrfToken) {}

    private record CsrfSession(String sessionId, String token) {}
}
