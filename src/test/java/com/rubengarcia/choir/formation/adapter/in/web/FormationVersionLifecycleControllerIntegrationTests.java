package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@SpringBootTest
@AutoConfigureWebTestClient(timeout = "30s")
class FormationVersionLifecycleControllerIntegrationTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private static final ParameterizedTypeReference<Map<String, String>> STRING_MAP =
            new ParameterizedTypeReference<>() {};

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    private Fixture fixture;
    private ForeignFixture foreignFixture;

    @Autowired
    FormationVersionLifecycleControllerIntegrationTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder) {
        this.webTestClient = webTestClient;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanup() {
        if (foreignFixture != null) {
            deleteChoir(foreignFixture.choirId());
            jdbc.update(
                    "DELETE FROM directors WHERE id = ?", foreignFixture.directorId());
            foreignFixture = null;
        }

        if (fixture != null) {
            deleteChoir(fixture.choirId());
            jdbc.update("DELETE FROM directors WHERE id = ?", fixture.directorId());
            fixture = null;
        }
    }

    @Test
    void lifecycleErrorsReturnConsistentSafeProblemDetails() {
        Fixture f = fixture();
        Session session = login(f.email());

        expectProblem(webTestClient.post()
                        .uri(versionsUri(f))
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("notes", "x".repeat(2001)))
                        .exchange(),
                400,
                "Bad Request",
                "La solicitud contiene datos inválidos");

        expectProblem(webTestClient.post()
                        .uri(versionsUri(f) + "/0/publish")
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .exchange(),
                400,
                "Bad Request",
                "La solicitud contiene datos inválidos");

        expectProblem(webTestClient.post()
                        .uri(versionsUri(f) + "/" + f.publishedVersionId() + "/publish")
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .exchange(),
                409,
                "Conflict",
                "La operación no puede completarse en el estado actual");

        expectProblem(webTestClient.post()
                        .uri(versionsUri(f) + "/" + Long.MAX_VALUE + "/publish")
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        long foreignVersionId = insertVersion(
                f.choirId(), "Otra formación " + UUID.randomUUID(), "DRAFT");
        expectProblem(webTestClient.post()
                        .uri(versionsUri(f) + "/" + foreignVersionId + "/publish")
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        expectProblem(webTestClient.get()
                        .uri(versionsUri(fixtureWithFormationId(
                                f, Long.MAX_VALUE)))
                        .cookie("SESSION", session.sessionId())
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        assertTrue(jdbc.queryForObject("""
                SELECT status = 'PUBLISHED'
                FROM formation_versions WHERE id = ?
                """, Boolean.class, f.publishedVersionId()));
    }

    @Test
    void authorizationErrorsHideForeignFormationExistence() {
        Fixture f = fixture();
        Session session = login(f.email());
        ForeignFixture other = foreignFixture();

        expectProblem(webTestClient.get()
                        .uri("/api/choirs/{choirId}/formations/{formationId}/versions",
                                other.choirId(), other.formationId())
                        .cookie("SESSION", session.sessionId())
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        webTestClient.get()
                .uri(versionsUri(f))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post()
                .uri(versionsUri(f))
                .cookie("SESSION", session.sessionId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("notes", "Sin CSRF"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void successfulLifecycleResponsesRemainUnchanged() {
        Fixture f = fixture();
        Session session = login(f.email());

        EntityExchangeResult<Map<String, String>> created = webTestClient.post()
                .uri(versionsUri(f))
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("notes", "Nueva versión"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(STRING_MAP)
                .returnResult();

        Map<String, String> body = created.getResponseBody();
        assertNotNull(body);
        assertTrue(body.containsKey("id"));
        assertTrue(body.containsKey("formationId"));
        assertTrue(body.containsKey("versionNumber"));
        assertTrue(body.containsKey("status"));
        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", body.get("status"));

        webTestClient.post()
                .uri(versionsUri(f) + "/" + body.get("id") + "/publish")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "lifecycle-http-" + suffix + "@example.test";
        long directorId = insertDirector(email, "Director " + suffix);
        jdbc.update("""
                INSERT INTO choirs (director_id, name) VALUES (?, ?)
                """, directorId, "Coro " + suffix);
        long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Coro " + suffix);
        jdbc.update("""
                INSERT INTO formations (choir_id, name) VALUES (?, ?)
                """, choirId, "Formación " + suffix);
        long formationId = jdbc.queryForObject("""
                SELECT id FROM formations WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, "Formación " + suffix);
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED')
                """, formationId);
        long versionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
        fixture = new Fixture(directorId, choirId, formationId, versionId, email);
        return fixture;
    }

    private ForeignFixture foreignFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "foreign-lifecycle-" + suffix + "@example.test";
        long directorId = insertDirector(email, "Otro director " + suffix);
        jdbc.update("""
                INSERT INTO choirs (director_id, name) VALUES (?, ?)
                """, directorId, "Otro coro " + suffix);
        long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Otro coro " + suffix);
        jdbc.update("""
                INSERT INTO formations (choir_id, name) VALUES (?, ?)
                """, choirId, "Formación ajena " + suffix);
        long formationId = jdbc.queryForObject("""
                SELECT id FROM formations WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, "Formación ajena " + suffix);
        foreignFixture = new ForeignFixture(directorId, choirId, formationId);
        return foreignFixture;
    }

    private long insertVersion(long choirId, String name, String status) {
        jdbc.update("""
                INSERT INTO formations (choir_id, name) VALUES (?, ?)
                """, choirId, name);
        long formationId = jdbc.queryForObject("""
                SELECT id FROM formations WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, name);
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, ?)
                """, formationId, status);
        return jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private long insertDirector(String email, String name) {
        jdbc.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """, name, email, passwordEncoder.encode(PASSWORD));
        return jdbc.queryForObject("""
                SELECT id FROM directors WHERE email = ?
                """, Long.class, email);
    }

    private void deleteChoir(long choirId) {
        jdbc.update("""
                DELETE FROM formation_positions
                WHERE formation_version_id IN (
                    SELECT fv.id
                    FROM formation_versions fv
                    JOIN formations f ON f.id = fv.formation_id
                    WHERE f.choir_id = ?
                )
                """, choirId);
        jdbc.update("""
                DELETE FROM formation_versions
                WHERE formation_id IN (
                    SELECT id FROM formations WHERE choir_id = ?
                )
                """, choirId);
        jdbc.update("DELETE FROM formations WHERE choir_id = ?", choirId);
        jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
    }

    private Session login(String email) {
        EntityExchangeResult<Map<String, String>> csrfResult = webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(STRING_MAP)
                .returnResult();

        ResponseCookie csrfCookie =
                csrfResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(csrfCookie);
        Map<String, String> csrfBody = csrfResult.getResponseBody();
        assertNotNull(csrfBody);

        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/auth/login")
                .cookie("SESSION", csrfCookie.getValue())
                .header("X-CSRF-TOKEN", csrfBody.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticatedCookie);
        return new Session(authenticatedCookie.getValue(), csrfBody.get("token"));
    }

    private String versionsUri(Fixture f) {
        return "/api/choirs/" + f.choirId()
                + "/formations/" + f.formationId() + "/versions";
    }

    private Fixture fixtureWithFormationId(Fixture f, long formationId) {
        return new Fixture(f.directorId(), f.choirId(), formationId,
                f.publishedVersionId(), f.email());
    }

    private void expectProblem(
            ResponseSpec response,
            int status,
            String title,
            String detail) {
        response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.title").isEqualTo(title)
                .jsonPath("$.detail").isEqualTo(detail);
    }

    private record Fixture(
            long directorId,
            long choirId,
            long formationId,
            long publishedVersionId,
            String email) {
    }

    private record ForeignFixture(
            long directorId,
            long choirId,
            long formationId) {
    }

    private record Session(String sessionId, String csrfToken) {
    }
}
