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

@SpringBootTest
@AutoConfigureWebTestClient
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
                DELETE FROM formation_versions
                WHERE formation_id = ?
                """, fixture.formationId());

        jdbc.update(
                "DELETE FROM formations WHERE id = ?", fixture.formationId());

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
        return "/api/choirs/" + f.choirId()
                + "/concerts/" + f.concertId()
                + "/repertoire/" + f.repertoireItemId()
                + "/formations/selection";
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
}
