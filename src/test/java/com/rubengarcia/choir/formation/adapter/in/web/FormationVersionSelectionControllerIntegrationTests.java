package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@SpringBootTest
@AutoConfigureWebTestClient(timeout = "30s")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationVersionSelectionControllerIntegrationTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    private Fixture fixture;
    private ForeignFixture foreignFixture;

    FormationVersionSelectionControllerIntegrationTests(
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
            jdbc.update("""
                    DELETE FROM formation_versions
                    WHERE formation_id = ?
                    """, foreignFixture.formationId());
            jdbc.update(
                    "DELETE FROM formations WHERE id = ?", foreignFixture.formationId());
            jdbc.update("""
                    DELETE FROM concert_repertoire
                    WHERE concert_id = ? AND repertoire_item_id = ?
                    """, foreignFixture.concertId(), foreignFixture.repertoireItemId());
            jdbc.update("""
                    DELETE FROM concerts WHERE id = ?
                    """, foreignFixture.concertId());
            jdbc.update("""
                    DELETE FROM repertoire_items WHERE id = ?
                    """, foreignFixture.repertoireItemId());
            jdbc.update("DELETE FROM choirs WHERE id = ?", foreignFixture.choirId());
            jdbc.update(
                    "DELETE FROM directors WHERE id = ?", foreignFixture.directorId());
            foreignFixture = null;
        }

        if (fixture == null) {
            return;
        }

        Integer historyEntries = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, fixture.concertId(), fixture.repertoireItemId());

        // El historial es inmutable en PostgreSQL. Si ya existe auditoría,
        // conservamos también sus entidades relacionadas.
        if (historyEntries != null && historyEntries > 0) {
            fixture = null;
            return;
        }

        jdbc.update("""
                UPDATE concert_repertoire
                SET selected_formation_id = NULL,
                    selected_formation_version_id = NULL
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, fixture.concertId(), fixture.repertoireItemId());

        jdbc.update("""
                DELETE FROM formation_positions
                WHERE formation_version_id IN (
                    SELECT fv.id
                    FROM formation_versions fv
                    JOIN formations f ON f.id = fv.formation_id
                    WHERE f.choir_id = ?
                )
                """, fixture.choirId());

        jdbc.update("""
                DELETE FROM formation_versions
                WHERE formation_id IN (
                    SELECT id FROM formations WHERE choir_id = ?
                )
                """, fixture.choirId());

        jdbc.update(
                "DELETE FROM formations WHERE choir_id = ?", fixture.choirId());

        jdbc.update("""
                DELETE FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, fixture.concertId(), fixture.repertoireItemId());

        jdbc.update(
                "DELETE FROM concerts WHERE id = ?", fixture.concertId());

        jdbc.update(
                "DELETE FROM repertoire_items WHERE id = ?",
                fixture.repertoireItemId());

        jdbc.update(
                "DELETE FROM choirs WHERE id = ?", fixture.choirId());

        jdbc.update(
                "DELETE FROM directors WHERE id = ?", fixture.directorId());

        fixture = null;
    }

    @Test
    void getReturnsUnselectedStateWhenNoVersionHasBeenSelected() {
        Fixture f = fixture();
        Session session = login(f.email());

        Map<String, Object> response = getSelection(f, session);

        assertNotNull(response);
        assertEquals(false, response.get("selected"));
        assertNull(response.get("formationId"));
        assertNull(response.get("formationVersionId"));
        assertEquals(f.concertId(), number(response, "concertId"));
        assertEquals(f.repertoireItemId(), number(response, "repertoireItemId"));
    }

    @Test
    void putSelectsPublishedVersionAndRepeatedSelectionDoesNotDuplicateHistory() {
        Fixture f = fixture();
        Session session = login(f.email());

        Map<String, Object> selected = putSelection(f, session, f.formationId(),
                f.versionId());

        assertNotNull(selected);
        assertEquals(true, selected.get("selected"));
        assertEquals(f.formationId(), number(selected, "formationId"));
        assertEquals(f.versionId(), number(selected, "formationVersionId"));
        assertNotNull(selected.get("selectedAt"));

        Map<String, Object> current = getSelection(f, session);
        assertEquals(true, current.get("selected"));
        assertEquals(f.formationId(), number(current, "formationId"));
        assertEquals(f.versionId(), number(current, "formationVersionId"));

        List<Map<String, Object>> history = getHistory(f, session);
        assertEquals(1, history.size());

        Map<String, Object> firstEvent = history.getFirst();
        assertNull(firstEvent.get("previousFormationId"));
        assertNull(firstEvent.get("previousFormationVersionId"));
        assertEquals(f.formationId(), number(firstEvent, "selectedFormationId"));
        assertEquals(f.versionId(), number(firstEvent, "selectedFormationVersionId"));
        assertEquals(f.email(), firstEvent.get("actorEmail"));

        putSelection(f, session, f.formationId(), f.versionId());

        List<Map<String, Object>> historyAfterRepeat = getHistory(f, session);
        assertEquals(1, historyAfterRepeat.size());

        assertEquals(1, historyCount(f));
    }

    @Test
    void putRejectsInvalidIdentifiersWithoutChangingSelectionOrHistory() {
        Fixture f = fixture();
        Session session = login(f.email());

        webTestClient.put()
                .uri(selectionUri(f))
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "formationId", 0,
                        "formationVersionId", f.versionId()))
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.put()
                .uri(selectionUri(f))
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "formationId": %d,
                          "formationVersionId": null
                        }
                        """.formatted(f.formationId()))
                .exchange()
                .expectStatus().isBadRequest();

        Map<String, Object> current = getSelection(f, session);
        assertEquals(false, current.get("selected"));
        assertNull(current.get("formationId"));
        assertNull(current.get("formationVersionId"));
        assertEquals(0, historyCount(f));
    }

    @Test
    void selectionErrorsReturnConsistentSafeProblemDetails() {
        Fixture f = fixture();
        Session session = login(f.email());

        expectProblem(webTestClient.put()
                        .uri(selectionUri(f))
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "formationId", 0,
                                "formationVersionId", f.versionId()))
                        .exchange(),
                400,
                "Bad Request",
                "La solicitud contiene datos inválidos");

        jdbc.update("""
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name)
                VALUES (?, ?, ?, ?)
                """, f.choirId(), f.concertId(), f.repertoireItemId(),
                "Formación extranjera " + UUID.randomUUID());
        long foreignFormationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name LIKE 'Formación extranjera %'
                ORDER BY id DESC LIMIT 1
                """, Long.class, f.choirId(), f.concertId(), f.repertoireItemId());
        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED')
                """, foreignFormationId);
        long foreignVersionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, foreignFormationId);

        expectProblem(webTestClient.put()
                        .uri(selectionUri(f))
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "formationId", f.formationId(),
                                "formationVersionId", foreignVersionId))
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        expectProblem(webTestClient.put()
                        .uri(selectionUri(f))
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "formationId", f.formationId(),
                                "formationVersionId", Long.MAX_VALUE))
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        expectProblem(webTestClient.put()
                        .uri("/api/choirs/{choirId}/concerts/{concertId}"
                                        + "/repertoire/{repertoireItemId}/formations/selection",
                                Long.MAX_VALUE, f.concertId(), f.repertoireItemId())
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "formationId", f.formationId(),
                                "formationVersionId", f.versionId()))
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        ForeignFixture other = foreignFixture();
        expectProblem(webTestClient.get()
                        .uri(selectionUri(
                                other.choirId(),
                                other.concertId(),
                                other.repertoireItemId()))
                        .cookie("SESSION", session.sessionId())
                        .exchange(),
                404,
                "Not Found",
                "El recurso solicitado no existe o no está disponible");

        assertEquals(0, historyCount(f));
    }

    @Test
    void selectingDraftVersionReturnsConsistentConflictProblemDetail() {
        Fixture f = fixture();
        Session session = login(f.email());

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 2, 'DRAFT')
                """, f.formationId());
        long draftVersionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 2
                """, Long.class, f.formationId());

        expectProblem(webTestClient.put()
                        .uri(selectionUri(f))
                        .cookie("SESSION", session.sessionId())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "formationId", f.formationId(),
                                "formationVersionId", draftVersionId))
                        .exchange(),
                409,
                "Conflict",
                "La operación no puede completarse en el estado actual");

        assertEquals(0, historyCount(f));
    }

    @Test
    void selectionRequiresAuthenticationAndCsrfProtection() {
        Fixture f = fixture();

        webTestClient.get()
                .uri(selectionUri(f))
                .exchange()
                .expectStatus().isUnauthorized();

        Session session = login(f.email());

        webTestClient.put()
                .uri(selectionUri(f))
                .cookie("SESSION", session.sessionId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "formationId", f.formationId(),
                        "formationVersionId", f.versionId()))
                .exchange()
                .expectStatus().isForbidden();

        assertEquals(0, historyCount(f));
        assertNull(jdbc.queryForObject("""
                SELECT selected_formation_id
                FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Long.class, f.concertId(), f.repertoireItemId()));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "choir101-http-" + suffix + "@example.test";

        jdbc.update("""
                INSERT INTO directors (
                    display_name, email, password_hash, email_verified
                ) VALUES (?, ?, ?, TRUE)
                """, "Director " + suffix, email, passwordEncoder.encode(PASSWORD));

        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Choir " + suffix);

        long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Choir " + suffix);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concert " + suffix);

        long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concert " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Work " + suffix);

        long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Work " + suffix);

        jdbc.update("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index
                ) VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formation " + suffix);

        long formationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Formation " + suffix);

        jdbc.update("""
                UPDATE formations
                SET concert_id = ?, repertoire_item_id = ?
                WHERE id = ?
                """, concertId, repertoireItemId, formationId);

        jdbc.update("""
                INSERT INTO formation_versions (
                    formation_id, version_number, status, notes
                ) VALUES (?, 1, 'PUBLISHED', 'Published HTTP test version')
                """, formationId);

        long versionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        fixture = new Fixture(
                directorId,
                choirId,
                concertId,
                repertoireItemId,
                formationId,
                versionId,
                email);

        return fixture;
    }

    private ForeignFixture foreignFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "foreign-selection-" + suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES (?, ?)
                """, "Otro director " + suffix, email);
        long directorId = jdbc.queryForObject("""
                SELECT id FROM directors WHERE email = ?
                """, Long.class, email);
        jdbc.update("""
                INSERT INTO choirs (director_id, name) VALUES (?, ?)
                """, directorId, "Otro coro " + suffix);
        long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Otro coro " + suffix);
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Otro concierto " + suffix);
        long concertId = jdbc.queryForObject("""
                SELECT id FROM concerts WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Otro concierto " + suffix);
        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Otra obra " + suffix);
        long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Otra obra " + suffix);
        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);
        jdbc.update("""
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name)
                VALUES (?, ?, ?, ?)
                """, choirId, concertId, repertoireItemId, "Formación ajena " + suffix);
        long formationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """, Long.class, choirId, concertId, repertoireItemId,
                "Formación ajena " + suffix);
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED')
                """, formationId);
        foreignFixture = new ForeignFixture(
                directorId, choirId, concertId, repertoireItemId, formationId);
        return foreignFixture;
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

    private Map<String, Object> getSelection(Fixture f, Session session) {
        return webTestClient.get()
                .uri(selectionUri(f))
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MAP_TYPE)
                .returnResult()
                .getResponseBody();
    }

    private Map<String, Object> putSelection(
            Fixture f,
            Session session,
            long formationId,
            long versionId) {
        return webTestClient.put()
                .uri(selectionUri(f))
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "formationId", formationId,
                        "formationVersionId", versionId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(MAP_TYPE)
                .returnResult()
                .getResponseBody();
    }

    private List<Map<String, Object>> getHistory(Fixture f, Session session) {
        return webTestClient.get()
                .uri(selectionUri(f) + "/history")
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(LIST_TYPE)
                .returnResult()
                .getResponseBody();
    }

    private int historyCount(Fixture f) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, f.concertId(), f.repertoireItemId());
    }

    private String selectionUri(Fixture f) {
        return selectionUri(f.choirId(), f.concertId(), f.repertoireItemId());
    }

    private String selectionUri(
            long choirId, long concertId, long repertoireItemId) {
        return "/api/choirs/" + choirId
                + "/concerts/" + concertId
                + "/repertoire/" + repertoireItemId
                + "/formations/selection";
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

    private long number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).longValue();
    }

    private record Fixture(
            long directorId,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long versionId,
            String email) {
    }

    private record Session(String sessionId, String csrfToken) {
    }

    private record ForeignFixture(
            long directorId,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
    }
}
