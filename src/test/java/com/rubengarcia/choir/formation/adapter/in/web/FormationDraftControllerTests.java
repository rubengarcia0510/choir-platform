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
class FormationDraftControllerTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private Long directorId;
    private Long choirId;
    private Long concertId;
    private Long repertoireItemId;

    FormationDraftControllerTests(
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
                    "DELETE FROM formation_positions WHERE version_id IN "
                            + "(SELECT fv.id FROM formation_versions fv "
                            + "JOIN formations f ON f.id = fv.formation_id "
                            + "WHERE f.choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM formation_versions WHERE formation_id IN "
                            + "(SELECT id FROM formations WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM formations WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concert_repertoire WHERE concert_id IN "
                            + "(SELECT id FROM concerts WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concerts WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?",
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
    void unauthenticatedListRequestIsRejected() {
        webTestClient.get()
                .uri("/api/choirs/1/concerts/1/repertoire/1/formations")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedCreateRequestIsRejected() {
        CsrfSession csrf = obtainCsrf();

        webTestClient.post()
                .uri("/api/choirs/1/concerts/1/repertoire/1/formations")
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "Formación de prueba",
                        "description", "Prueba sin autenticar"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedDraftReadRequestIsRejected() {
        webTestClient.get()
                .uri("/api/choirs/1/concerts/1/repertoire/1/formations/1/draft")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedPositionReplacementIsRejected() {
        CsrfSession csrf = obtainCsrf();

        webTestClient.put()
                .uri("/api/choirs/1/concerts/1/repertoire/1/formations/1/versions/1/positions")
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("positions", java.util.List.of()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedDirectorCannotAccessFormationForIncorrectConcertRepertoireAssociation()
            throws Exception {

        DirectorAccount director = createDirector();
        Session session = login(director.email());

        ChoirFixture fixture = createChoirConcertAndRepertoire(director.id());

        webTestClient.get()
                .uri("/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations",
                        fixture.choirId(),
                        fixture.foreignConcertId(),
                        fixture.repertoireItemId())
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post()
                .uri("/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations",
                        fixture.choirId(),
                        fixture.foreignConcertId(),
                        fixture.repertoireItemId())
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "No autorizado",
                        "description", "Asociación incorrecta"))
                .exchange()
                .expectStatus().isNotFound();
    }

    private DirectorAccount createDirector() {
        String unique = UUID.randomUUID().toString();
        String email = "formation-draft-" + unique + "@example.test";
        String displayName = "Formation Draft Director " + unique;

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
        directorId = id;

        return new DirectorAccount(id, displayName, email);
    }

    private ChoirFixture createChoirConcertAndRepertoire(Long ownerDirectorId) {
        jdbcTemplate.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """,
                ownerDirectorId,
                "Formation Draft Choir " + UUID.randomUUID());

        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                ownerDirectorId);

        assertNotNull(choirId);

        jdbcTemplate.update("""
                INSERT INTO concerts (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                "Formation Draft Concert " + UUID.randomUUID());

        concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                choirId);

        jdbcTemplate.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                "Formation Draft Work " + UUID.randomUUID());

        repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                choirId);

        /*
         * Creamos un segundo concierto del mismo coro, pero NO asociamos
         * el repertorio a este concierto. La combinación es deliberadamente
         * inexistente en concert_repertoire.
         */
        jdbcTemplate.update("""
                INSERT INTO concerts (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                "Formation Draft Foreign Concert " + UUID.randomUUID());

        Long foreignConcertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                choirId);

        assertNotNull(foreignConcertId);

        return new ChoirFixture(
                choirId,
                concertId,
                foreignConcertId,
                repertoireItemId);
    }

    private CsrfSession obtainCsrf() {
        EntityExchangeResult<Map<String, String>> result = webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertNotNull(cookie, "El endpoint CSRF debe crear una sesión");
        assertNotNull(result.getResponseBody());

        String token = result.getResponseBody().get("token");
        assertNotNull(token, "El endpoint CSRF debe devolver un token");

        return new CsrfSession(cookie.getValue(), token);
    }

    private Session login(String email) throws Exception {
        CsrfSession csrf = obtainCsrf();

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        EntityExchangeResult<String> loginResult = webTestClient.post()
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

    private record DirectorAccount(Long id, String displayName, String email) {}

    private record Session(String sessionId, String csrfToken) {}

    private record CsrfSession(String sessionId, String token) {}

    private record ChoirFixture(
            Long choirId,
            Long concertId,
            Long foreignConcertId,
            Long repertoireItemId) {}
}
