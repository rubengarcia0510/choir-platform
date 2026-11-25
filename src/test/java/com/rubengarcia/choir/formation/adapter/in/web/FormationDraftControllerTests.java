package com.rubengarcia.choir.formation.adapter.in.web;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
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
                    "DELETE FROM formation_proximity_constraints WHERE choir_id = ?",
                    choirId);

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
                    "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                    choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                    choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_plans WHERE choir_id = ?",
                    choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?",
                    choirId);
            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?",
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
                .uri(
                        "/api/choirs/1/concerts/1/repertoire/1"
                                + "/formations/1/versions/1/positions")
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("positions", List.of()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedPositionLockAndUnlockAreRejected() {
        CsrfSession csrf = obtainCsrf();
        String uri = "/api/choirs/1/concerts/1/repertoire/1"
                + "/formations/1/versions/1/positions/1/lock";

        webTestClient.put()
                .uri(uri)
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.delete()
                .uri(uri)
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedDirectorCannotAccessFormationForIncorrectConcertRepertoireAssociation() {
        DirectorAccount director = createDirector();
        Session session = login(director.email());

        ChoirFixture fixture =
                createChoirConcertAndRepertoire(director.id());

        webTestClient.get()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}/formations",
                        fixture.choirId(),
                        fixture.foreignConcertId(),
                        fixture.repertoireItemId())
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post()
                .uri(
                        "/api/choirs/{choirId}/concerts/{concertId}"
                                + "/repertoire/{repertoireItemId}/formations",
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

        jdbcTemplate.update(
                """
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

        return new DirectorAccount(id, email);
    }

    private ChoirFixture createChoirConcertAndRepertoire(Long ownerDirectorId) {
        jdbcTemplate.update(
                """
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """,
                ownerDirectorId,
                "Formation Draft Choir " + UUID.randomUUID());

        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class,
                ownerDirectorId);

        assertNotNull(choirId);

        jdbcTemplate.update(
                """
                INSERT INTO concerts (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                "Formation Draft Concert " + UUID.randomUUID());

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class,
                choirId);

        assertNotNull(concertId);

        jdbcTemplate.update(
                """
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                "Formation Draft Work " + UUID.randomUUID());

        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class,
                choirId);

        assertNotNull(repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO concerts (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                "Formation Draft Foreign Concert " + UUID.randomUUID());

        Long foreignConcertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class,
                choirId);

        assertNotNull(foreignConcertId);

        return new ChoirFixture(choirId, foreignConcertId, repertoireItemId);
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

    private Session login(String email) {
        CsrfSession csrf = obtainCsrf();

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        EntityExchangeResult<String> loginResult =
                webTestClient.post()
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

    @Test
    void authenticatedDirectorReceivesConflictWhenDraftReplacementViolatesProximity() {
        DirectorAccount director = createDirector();
        Session session = login(director.email());
        ChoirFixture fixture = createChoirConcertAndRepertoire(director.id());
        long concertId = insertConcertWithWork(fixture.choirId(), fixture.repertoireItemId());
        long division = insertVocalDivision(fixture.choirId(), fixture.repertoireItemId());
        long first = insertSinger(fixture.choirId(), "Primera proximidad");
        long second = insertSinger(fixture.choirId(), "Segunda proximidad");
        insertAssignment(fixture.choirId(), concertId, fixture.repertoireItemId(), first, division);
        insertAssignment(fixture.choirId(), concertId, fixture.repertoireItemId(), second, division);
        long formationId = insertFormation(fixture.choirId(), concertId, fixture.repertoireItemId());
        long versionId = insertDraftVersion(formationId);
        insertPosition(versionId, first, "Primera proximidad", "A",
                new BigDecimal("10"), new BigDecimal("10"));
        insertPosition(versionId, second, "Segunda proximidad", "B",
                new BigDecimal("12"), new BigDecimal("10"));
        long constraintId = insertProximityConstraint(
                fixture.choirId(), formationId, first, second, new BigDecimal("5"));
        List<Map<String, Object>> before = positionRows(versionId);

        webTestClient.put()
                .uri("/api/choirs/%d/concerts/%d/repertoire/%d/formations/%d/versions/%d/positions"
                        .formatted(fixture.choirId(), concertId, fixture.repertoireItemId(),
                                formationId, versionId))
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("positions", List.of(
                        Map.of("singerId", first, "positionLabel", "A", "x", 10, "y", 10),
                        Map.of("singerId", second, "positionLabel", "B", "x", 90, "y", 90))))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.title").isEqualTo("Conflict")
                .jsonPath("$.detail").isEqualTo("La operación no puede completarse en el estado actual")
                .jsonPath("$.violations.length()").isEqualTo(1)
                .jsonPath("$.violations[0].code").isEqualTo("PROXIMITY_VIOLATION")
                .jsonPath("$.violations[0].constraintId").isEqualTo(Math.toIntExact(constraintId))
                .jsonPath("$.violations[0].singerOneId").isEqualTo(Math.toIntExact(first))
                .jsonPath("$.violations[0].singerTwoId").isEqualTo(Math.toIntExact(second))
                .jsonPath("$.violations[0].message").isNotEmpty();

        assertThat(positionRows(versionId)).isEqualTo(before);
    }

    private long insertConcertWithWork(long choirId, long repertoireItemId) {
        String title = "Concierto conflicto " + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, title);
        long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
        jdbcTemplate.update(
                "INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index) VALUES (?, ?, 0)",
                concertId, repertoireItemId);
        return concertId;
    }

    private long insertVocalDivision(long choirId, long repertoireItemId) {
        String sectionName = "Sección conflicto " + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO vocal_sections (choir_id, name, sort_order) VALUES (?, ?, 0)",
                choirId, sectionName);
        long sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class, choirId, sectionName);
        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) VALUES (?, ?)",
                repertoireItemId, choirId);
        long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ? AND choir_id = ?",
                Long.class, repertoireItemId, choirId);
        jdbcTemplate.update(
                "INSERT INTO vocal_plan_divisions (vocal_plan_id, choir_id, vocal_section_id, division_number) VALUES (?, ?, ?, 1)",
                planId, choirId, sectionId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plan_divisions WHERE vocal_plan_id = ? AND vocal_section_id = ?",
                Long.class, planId, sectionId);
    }

    private long insertSinger(long choirId, String name) {
        String displayName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO singers (choir_id, display_name, voice_part, active) VALUES (?, ?, 'ALTO', TRUE)",
                choirId, displayName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, displayName);
    }

    private void insertAssignment(long choirId, long concertId, long repertoireItemId,
                                  long singerId, long divisionId) {
        jdbcTemplate.update(
                "INSERT INTO concert_vocal_assignments (concert_id, repertoire_item_id, singer_id, vocal_plan_division_id, choir_id) VALUES (?, ?, ?, ?, ?)",
                concertId, repertoireItemId, singerId, divisionId, choirId);
    }

    private long insertFormation(long choirId, long concertId, long repertoireItemId) {
        String name = "Formación conflicto " + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO formations (choir_id, concert_id, repertoire_item_id, name, description) VALUES (?, ?, ?, ?, ?)",
                choirId, concertId, repertoireItemId, name, "Fixture de conflicto HTTP");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private long insertDraftVersion(long formationId) {
        jdbcTemplate.update(
                "INSERT INTO formation_versions (formation_id, version_number, status) VALUES (?, 1, 'DRAFT')",
                formationId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions WHERE formation_id = ? AND version_number = 1",
                Long.class, formationId);
    }

    private void insertPosition(long versionId, long singerId, String singerName, String label,
                                BigDecimal x, BigDecimal y) {
        jdbcTemplate.update(
                "INSERT INTO formation_positions (formation_version_id, singer_id, singer_name_snapshot, voice_part_snapshot, position_label, x_coordinate, y_coordinate) VALUES (?, ?, ?, 'ALTO', ?, ?, ?)",
                versionId, singerId, singerName, label, x, y);
    }

    private long insertProximityConstraint(long choirId, long formationId, long singerA,
                                           long singerB, BigDecimal maxDistance) {
        long low = Math.min(singerA, singerB);
        long high = Math.max(singerA, singerB);
        jdbcTemplate.update(
                "INSERT INTO formation_proximity_constraints (formation_id, choir_id, singer_one_id, singer_two_id, max_distance, active) VALUES (?, ?, ?, ?, ?, TRUE)",
                formationId, choirId, low, high, maxDistance);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM formation_proximity_constraints WHERE formation_id = ? AND singer_one_id = ? AND singer_two_id = ?",
                Long.class, formationId, low, high);
    }

    private List<Map<String, Object>> positionRows(long versionId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM formation_positions WHERE formation_version_id = ? ORDER BY id",
                versionId);
    }

    private record DirectorAccount(Long id, String email) {}

    private record Session(String sessionId, String csrfToken) {}

    private record CsrfSession(String sessionId, String token) {}

    private record ChoirFixture(
            Long choirId,
            Long foreignConcertId,
            Long repertoireItemId) {}
}
