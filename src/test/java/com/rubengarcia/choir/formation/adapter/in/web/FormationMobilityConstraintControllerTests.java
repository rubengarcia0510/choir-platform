package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationMobilityConstraintControllerTests {

    private static final String PASSWORD = "Mobility-Constraint-Test-2026!";
    private static final long UNKNOWN_ID = 987_654_321L;

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final List<Fixture> fixtures = new ArrayList<>();

    FormationMobilityConstraintControllerTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @AfterEach
    void cleanUp() {
        for (Fixture fixture : fixtures) {
            deleteChoirData(fixture.choirId());
            jdbcTemplate.update("DELETE FROM directors WHERE id = ?", fixture.directorId());
        }
        fixtures.clear();
    }

    // ------------------------------------------------------------------
    // Autenticación y validación
    // ------------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() {
        Fixture fixture = newFixture();

        webTestClient.get()
                .uri(base(fixture))
                .exchange()
                .expectStatus().isUnauthorized();

        Session anonymous = obtainCsrf();
        webTestClient.post()
                .uri(base(fixture) + "/mobility")
                .cookie("SESSION", anonymous.sessionId())
                .header("X-CSRF-TOKEN", anonymous.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(fixture.singerOneId(), "1.000", "8.000", "0.000", "10.000"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidRegionsAreRejectedWithBadRequest() {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());

        postMobility(session, fixture, createBody(
                fixture.singerOneId(), "5.000", "5.000", "0.000", "10.000"))
                .expectStatus().isBadRequest();

        postMobility(session, fixture, createBody(
                fixture.singerOneId(), "0.000", "10.000", "6.000", "5.000"))
                .expectStatus().isBadRequest();

        assertEquals(0, countMobility(fixture.formationId()));
    }

    // ------------------------------------------------------------------
    // Flujo CRUD con autorización
    // ------------------------------------------------------------------

    @Test
    void authorizedDirectorCreatesAMobilityConstraint() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());

        Map<String, Object> body = jsonBody(postMobility(session, fixture,
                createBody(fixture.singerOneId(), "1.250", "8.750", "-0.500", "12.000"))
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());

        assertNotNull(body.get("id"));
        assertEquals(true, body.get("active"));
        assertEquals(countMobility(fixture.formationId()), 1);
    }

    @Test
    void listReturnsOnlyActiveConstraints() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());
        long activeId = createMobilityId(session, fixture, fixture.singerOneId());
        long inactiveId = createMobilityId(session, fixture, fixture.singerTwoId());

        putMobility(session, fixture, inactiveId, "2.000", "3.000", "0.000", "5.000", false)
                .expectStatus().isOk();

        List<Map<String, Object>> listed = jsonList(webTestClient.get()
                .uri(base(fixture))
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());

        assertEquals(1, listed.size());
        assertEquals(activeId, ((Number) listed.get(0).get("id")).longValue());
    }

    @Test
    void updateChangesRegionAndActiveState() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());
        long id = createMobilityId(session, fixture, fixture.singerOneId());

        Map<String, Object> updated = jsonBody(putMobility(
                        session, fixture, id, "2.000", "3.000", "0.000", "5.000", false)
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());

        assertEquals(false, updated.get("active"));
        assertEquals(0, new java.math.BigDecimal("2.000")
                .compareTo(new java.math.BigDecimal(updated.get("minX").toString())));
        assertEquals(false, jdbcTemplate.queryForObject(
                "SELECT active FROM formation_mobility_constraints WHERE id = ?",
                Boolean.class, id));
        assertEquals(0, new java.math.BigDecimal("3.000").compareTo(
                jdbcTemplate.queryForObject(
                        "SELECT max_x FROM formation_mobility_constraints WHERE id = ?",
                        java.math.BigDecimal.class, id)));
    }

    @Test
    void deleteRemovesTheRecordAndSecondDeleteIsNotFound() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());
        long id = createMobilityId(session, fixture, fixture.singerOneId());

        webTestClient.delete()
                .uri(base(fixture) + "/" + id)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        assertEquals(0, countMobility(fixture.formationId()));

        webTestClient.delete()
                .uri(base(fixture) + "/" + id)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void secondConstraintForTheSameSingerIsConflict() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());
        createMobilityId(session, fixture, fixture.singerOneId());

        postMobility(session, fixture, createBody(
                fixture.singerOneId(), "0.000", "4.000", "0.000", "4.000"))
                .expectStatus().isEqualTo(409);

        assertEquals(1, countMobility(fixture.formationId()));
    }

    // ------------------------------------------------------------------
    // Aislamiento y contexto
    // ------------------------------------------------------------------

    @Test
    void directorWithoutOwnershipCannotCreateReadUpdateOrDelete() throws Exception {
        Fixture owner = newFixture();
        Session ownerSession = login(owner.email());
        long id = createMobilityId(ownerSession, owner, owner.singerOneId());
        Map<String, Object> before = snapshot(id);

        Fixture intruder = newFixture();
        Session intruderSession = login(intruder.email());

        // El intruso usa el contexto del propietario: la ocurrencia no le pertenece.
        postMobility(intruderSession, owner, createBody(
                owner.singerTwoId(), "0.000", "4.000", "0.000", "4.000"))
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri(base(owner))
                .cookie("SESSION", intruderSession.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        putMobility(intruderSession, owner, id, "2.000", "3.000", "0.000", "5.000", false)
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri(base(owner) + "/" + id)
                .cookie("SESSION", intruderSession.sessionId())
                .header("X-CSRF-TOKEN", intruderSession.csrfToken())
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(1, countMobility(owner.formationId()));
        assertEquals(before, snapshot(id));
    }

    @Test
    void constraintOfAnotherFormationCannotBeModifiedOrDeletedFromThisContext() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());
        createMobilityId(session, fixture, fixture.singerOneId());

        long otherFormationId = insertDraftFormation(fixture, "Formación alternativa");
        Fixture otherContext = fixture.withFormation(otherFormationId);
        long foreignId = createMobilityId(session, otherContext, fixture.singerOneId());
        Map<String, Object> before = snapshot(foreignId);

        putMobility(session, fixture, foreignId, "2.000", "3.000", "0.000", "5.000", false)
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri(base(fixture) + "/" + foreignId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(before, snapshot(foreignId));
        assertEquals(1, countMobility(fixture.formationId()));
    }

    @Test
    void singerWithoutDraftPositionCannotReceiveAConstraint() throws Exception {
        Fixture fixture = newFixture();
        Session session = login(fixture.email());

        postMobility(session, fixture, createBody(
                fixture.outsiderSingerId(), "0.000", "4.000", "0.000", "4.000"))
                .expectStatus().isBadRequest();

        assertEquals(0, countMobility(fixture.formationId()));
    }

    // ------------------------------------------------------------------
    // Helpers HTTP
    // ------------------------------------------------------------------

    private String base(Fixture fixture) {
        return "/api/choirs/%d/concerts/%d/repertoire/%d/formations/%d/constraints/physical"
                .formatted(fixture.choirId(), fixture.concertId(),
                        fixture.repertoireItemId(), fixture.formationId());
    }

    private WebTestClient.ResponseSpec postMobility(
            Session session, Fixture fixture, Map<String, Object> body) {
        return webTestClient.post()
                .uri(base(fixture) + "/mobility")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private WebTestClient.ResponseSpec putMobility(
            Session session, Fixture fixture, long id,
            String minX, String maxX, String minY, String maxY, boolean active) {
        return webTestClient.put()
                .uri(base(fixture) + "/" + id)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "minX", minX, "maxX", maxX,
                        "minY", minY, "maxY", maxY,
                        "active", active))
                .exchange();
    }

    private long createMobilityId(Session session, Fixture fixture, long singerId)
            throws Exception {
        Map<String, Object> body = jsonBody(postMobility(session, fixture,
                        createBody(singerId, "1.000", "8.000", "0.000", "10.000"))
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());
        return ((Number) body.get("id")).longValue();
    }

    private static Map<String, Object> createBody(
            long singerId, String minX, String maxX, String minY, String maxY) {
        return Map.of(
                "singerId", singerId,
                "minX", minX, "maxX", maxX,
                "minY", minY, "maxY", maxY);
    }

    private Map<String, Object> jsonBody(String json) {
        return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<>() {});
    }

    private List<Map<String, Object>> jsonList(String json) {
        return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<>() {});
    }

    private Session obtainCsrf() {
        EntityExchangeResult<Map<String, String>> result = webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertNotNull(cookie);
        String token = result.getResponseBody().get("token");
        assertNotNull(token);
        return new Session(cookie.getValue(), token);
    }

    private Session login(String email) {
        Session initial = obtainCsrf();

        EntityExchangeResult<String> result = webTestClient.post()
                .uri("/api/auth/login")
                .cookie("SESSION", initial.sessionId())
                .header("X-CSRF-TOKEN", initial.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticated = result.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticated);
        return new Session(authenticated.getValue(), initial.csrfToken());
    }

    // ------------------------------------------------------------------
    // Fixtures y consultas de estado
    // ------------------------------------------------------------------

    private Fixture newFixture() {
        String unique = UUID.randomUUID().toString();
        String email = "mobility-" + unique + "@example.test";

        jdbcTemplate.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Director Mobility " + unique,
                email,
                passwordEncoder.encode(PASSWORD));
        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro movilidad " + unique);
        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro movilidad " + unique);

        Long singerOneId = insertSinger(choirId, "Primera " + unique);
        Long singerTwoId = insertSinger(choirId, "Segunda " + unique);
        Long outsiderSingerId = insertSinger(choirId, "Sin borrador " + unique);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, "Concierto movilidad " + unique);
        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concierto movilidad " + unique);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra movilidad " + unique);
        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Obra movilidad " + unique);

        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name, sort_order)
                VALUES (?, ?, 0)
                """, choirId, "Sección movilidad " + unique);
        Long sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Sección movilidad " + unique);

        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) VALUES (?, ?)",
                repertoireItemId, choirId);
        Long vocalPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class, repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, 1)
                """, vocalPlanId, choirId, sectionId);
        Long divisionId = jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """, Long.class, vocalPlanId, sectionId);

        insertAssignment(choirId, concertId, repertoireItemId, singerOneId, divisionId);
        insertAssignment(choirId, concertId, repertoireItemId, singerTwoId, divisionId);

        Fixture base = new Fixture(
                directorId, email, choirId, concertId, repertoireItemId,
                null, singerOneId, singerTwoId, outsiderSingerId);
        long formationId = insertFormation(base, "Formación movilidad " + unique);
        Long versionId = insertDraftVersion(formationId);
        insertPosition(versionId, singerOneId);
        insertPosition(versionId, singerTwoId);

        Fixture fixture = base.withFormation(formationId);
        fixtures.add(fixture);
        return fixture;
    }

    private long insertDraftFormation(Fixture fixture, String name) {
        long formationId = insertFormation(fixture, name + " " + UUID.randomUUID());
        Long versionId = insertDraftVersion(formationId);
        insertPosition(versionId, fixture.singerOneId());
        return formationId;
    }

    private long insertFormation(Fixture base, String name) {
        jdbcTemplate.update("""
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, ?)
                """,
                base.choirId(), base.concertId(), base.repertoireItemId(),
                name, "Fixture de restricciones de movilidad");
        return jdbcTemplate.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """, Long.class, base.choirId(), base.concertId(),
                base.repertoireItemId(), name);
    }

    private Long insertDraftVersion(long formationId) {
        jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """, formationId);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private void insertPosition(Long versionId, long singerId) {
        String name = jdbcTemplate.queryForObject(
                "SELECT display_name FROM singers WHERE id = ?", String.class, singerId);
        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, ?, 'ALTO', 'Posición', 10.000, 20.000)
                """, versionId, singerId, name);
    }

    private Long insertSinger(Long choirId, String name) {
        jdbcTemplate.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'ALTO', TRUE)
                """, choirId, name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, name);
    }

    private void insertAssignment(
            Long choirId, Long concertId, Long repertoireItemId,
            Long singerId, Long divisionId) {
        jdbcTemplate.update("""
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id,
                     vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """, concertId, repertoireItemId, singerId, divisionId, choirId);
    }

    private int countMobility(Long formationId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM formation_mobility_constraints
                WHERE formation_id = ?
                """, Integer.class, formationId);
    }

    private Map<String, Object> snapshot(long constraintId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM formation_mobility_constraints WHERE id = ?", constraintId);
    }

    private void deleteChoirData(Long choirId) {
        jdbcTemplate.update("DELETE FROM formation_mobility_constraints WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM formation_visibility_constraints WHERE choir_id = ?", choirId);
        jdbcTemplate.update("""
                DELETE FROM formation_positions
                WHERE formation_version_id IN (
                    SELECT v.id FROM formation_versions v
                    JOIN formations f ON f.id = v.formation_id
                    WHERE f.choir_id = ?)
                """, choirId);
        jdbcTemplate.update("""
                DELETE FROM formation_versions
                WHERE formation_id IN (SELECT id FROM formations WHERE choir_id = ?)
                """, choirId);
        jdbcTemplate.update("DELETE FROM formations WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM concert_vocal_assignments WHERE choir_id = ?", choirId);
        jdbcTemplate.update("""
                DELETE FROM concert_repertoire
                WHERE concert_id IN (SELECT id FROM concerts WHERE choir_id = ?)
                """, choirId);
        jdbcTemplate.update("DELETE FROM vocal_plan_divisions WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM vocal_plans WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM singers WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM vocal_sections WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
        jdbcTemplate.update("DELETE FROM choirs WHERE id = ?", choirId);
    }

    private record Fixture(
            Long directorId,
            String email,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long formationId,
            Long singerOneId,
            Long singerTwoId,
            Long outsiderSingerId) {

        Fixture withFormation(long newFormationId) {
            return new Fixture(directorId, email, choirId, concertId,
                    repertoireItemId, newFormationId, singerOneId,
                    singerTwoId, outsiderSingerId);
        }
    }

    private record Session(String sessionId, String csrfToken) {
    }
}
