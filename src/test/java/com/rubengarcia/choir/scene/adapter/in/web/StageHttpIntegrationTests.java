package com.rubengarcia.choir.scene.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Flujo HTTP completo de escenarios con sesiones reales de dos directores. */
@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StageHttpIntegrationTests {

    private static final String PASSWORD = "Stage-Http-Test-Password-2026!";
    private static final long MISSING = 999_999_999L;

    private final WebTestClient web;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final java.util.List<Long> directorIds = new java.util.ArrayList<>();
    private final java.util.List<Long> choirIds = new java.util.ArrayList<>();

    @Autowired
    StageHttpIntegrationTests(WebTestClient web, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.web = web;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanUp() {
        new StagePersistenceFixture(jdbc).cleanup();
        for (Long choirId : choirIds) {
            jdbc.update("DELETE FROM concert_stage_snapshot_elements WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_stage_snapshot_accesses WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_stage_snapshot_boundary_points WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_stage_snapshots WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_elements WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_accesses WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_boundary_points WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_templates WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_repertoire WHERE concert_id IN (SELECT id FROM concerts WHERE choir_id = ?)",
                    choirId);
            jdbc.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
        }
        for (Long directorId : directorIds) {
            jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
        }
        choirIds.clear();
        directorIds.clear();
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        web.get().uri(stagesPath(1)).exchange().expectStatus().isUnauthorized();
        web.get().uri(stagePath(1, 1)).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void ownerCreatesReadsAndArchivesATemplate() {
        Scenario owner = scenario("owner");
        Session session = login(owner.email());

        Map<String, Object> created = post(session, stagesPath(owner.choirId()), rectangleBody("Escenario A"), 201);
        long stageId = ((Number) created.get("id")).longValue();

        get(session, stagePath(owner.choirId(), stageId), 200);
        assertThat(list(session, stagesPath(owner.choirId()))).hasSize(1);
        delete(session, stagePath(owner.choirId(), stageId), 204);
        assertThat(get(session, stagePath(owner.choirId(), stageId), 200).get("archived")).isEqualTo(true);
    }

    @Test
    void anotherDirectorIsForbiddenFromAnExistingChoir() {
        Scenario owner = scenario("dueno");
        Scenario intruder = scenario("intruso");
        Session intruderSession = login(intruder.email());

        get(intruderSession, stagesPath(owner.choirId()), 403);
        post(intruderSession, stagesPath(owner.choirId()), rectangleBody("Ajeno"), 403);
    }

    @Test
    void missingChoirAnswersNotFound() {
        Scenario owner = scenario("inexistente");
        Session session = login(owner.email());

        get(session, stagesPath(MISSING), 404);
    }

    @Test
    void invalidGeometryAnswersBadRequest() {
        Scenario owner = scenario("geometria");
        Session session = login(owner.email());
        Map<String, Object> body = Map.of("name", "Dos vértices", "widthCm", 800, "depthCm", 600,
                "boundary", List.of(Map.of("x", 0, "y", 0), Map.of("x", 800, "y", 0)),
                "accesses", List.of(), "elements", List.of());

        post(session, stagesPath(owner.choirId()), body, 400);
    }

    @Test
    void associationIsCreatedOnceAndExposesTheGeometryContract() {
        Scenario owner = scenario("asociacion");
        Session session = login(owner.email());
        Map<String, Object> template = post(session, stagesPath(owner.choirId()), rectangleBody("Plantilla"), 201);
        long stageId = ((Number) template.get("id")).longValue();
        String stagePath = concertStagePath(owner);

        Map<String, Object> association = post(session, stagePath, Map.of("stageId", stageId), 201);
        assertThat(((Number) association.get("version")).intValue()).isEqualTo(1);
        post(session, stagePath, Map.of("stageId", stageId), 409);

        Map<String, Object> geometry = get(session, stagePath, 200);
        assertThat(((Number) geometry.get("snapshotId")).longValue())
                .isEqualTo(((Number) association.get("snapshotId")).longValue());
        assertThat(geometry.get("contour")).isNotNull();
        assertThat(geometry.get("elements")).isNotNull();
    }

    @Test
    void anotherDirectorCannotReadOrAssociateAnotherChoirGeometry() {
        Scenario owner = scenario("geometria-dueno");
        Scenario intruder = scenario("geometria-intruso");
        Session ownerSession = login(owner.email());
        Map<String, Object> template = post(ownerSession, stagesPath(owner.choirId()), rectangleBody("Base"), 201);
        long stageId = ((Number) template.get("id")).longValue();
        post(ownerSession, concertStagePath(owner), Map.of("stageId", stageId), 201);

        Session intruderSession = login(intruder.email());
        get(intruderSession, concertStagePath(owner), 403);
        post(intruderSession, concertStagePath(owner), Map.of("stageId", stageId), 403);
    }

    private Scenario scenario(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        String email = unique + "@example.test";
        jdbc.update("INSERT INTO directors (display_name, email, password_hash, email_verified) VALUES (?, ?, ?, TRUE)",
                "Director " + unique, email, passwordEncoder.encode(PASSWORD));
        Long directorId = jdbc.queryForObject("SELECT id FROM directors WHERE email = ?", Long.class, email);
        directorIds.add(directorId);
        jdbc.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)", directorId, "Coro " + unique);
        Long choirId = jdbc.queryForObject("SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro " + unique);
        choirIds.add(choirId);
        jdbc.update("INSERT INTO concerts (choir_id, title) VALUES (?, ?)", choirId, "Concierto " + unique);
        Long concertId = jdbc.queryForObject("SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concierto " + unique);
        jdbc.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)", choirId, "Obra " + unique);
        Long workId = jdbc.queryForObject("SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Obra " + unique);
        jdbc.update("INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index) VALUES (?, ?, 0)",
                concertId, workId);
        return new Scenario(email, choirId, concertId, workId);
    }

    private Session login(String email) {
        EntityExchangeResult<Map<String, String>> csrf = web.get().uri("/api/auth/csrf")
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();
        ResponseCookie initial = csrf.getResponseCookies().getFirst("SESSION");
        String token = csrf.getResponseBody().get("token");

        EntityExchangeResult<String> login = web.post().uri("/api/auth/login")
                .cookie("SESSION", initial.getValue())
                .header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", PASSWORD))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult();
        return new Session(login.getResponseCookies().getFirst("SESSION").getValue(), token);
    }

    private Map<String, Object> post(Session s, String uri, Object body, int status) {
        return exchange(web.post().uri(uri).cookie("SESSION", s.id()).header("X-CSRF-TOKEN", s.csrf())
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body), status);
    }

    private Map<String, Object> get(Session s, String uri, int status) {
        return exchange(web.get().uri(uri).cookie("SESSION", s.id()), status);
    }

    private List<Map<String, Object>> list(Session s, String uri) {
        return web.get().uri(uri).cookie("SESSION", s.id()).exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .returnResult().getResponseBody();
    }

    private void delete(Session s, String uri, int status) {
        web.delete().uri(uri).cookie("SESSION", s.id()).header("X-CSRF-TOKEN", s.csrf())
                .exchange().expectStatus().isEqualTo(status);
    }

    private Map<String, Object> exchange(WebTestClient.RequestHeadersSpec<?> request, int status) {
        WebTestClient.ResponseSpec response = request.exchange().expectStatus().isEqualTo(status);
        if (status == 204) {
            return Map.of();
        }
        return response.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();
    }

    private static Map<String, Object> rectangleBody(String name) {
        return Map.of("name", name, "widthCm", 800, "depthCm", 600,
                "boundary", List.of(Map.of("x", 0, "y", 0), Map.of("x", 800, "y", 0),
                        Map.of("x", 800, "y", 600), Map.of("x", 0, "y", 600)),
                "accesses", List.of(Map.of("name", "Puerta",
                        "start", Map.of("x", 300, "y", 0), "end", Map.of("x", 400, "y", 0))),
                "elements", List.of(Map.of("name", "Escalón", "type", "STEP",
                        "x", 100, "y", 500, "widthCm", 200, "depthCm", 30, "elevationCm", 15.125)));
    }

    private static String stagesPath(long choirId) {
        return "/api/choirs/" + choirId + "/stages";
    }

    private static String stagePath(long choirId, long stageId) {
        return stagesPath(choirId) + "/" + stageId;
    }

    private static String concertStagePath(Scenario s) {
        return "/api/choirs/" + s.choirId() + "/concerts/" + s.concertId()
                + "/repertoire/" + s.workId() + "/stage";
    }

    private record Scenario(String email, long choirId, long concertId, long workId) {
    }

    private record Session(String id, String csrf) {
    }
}
