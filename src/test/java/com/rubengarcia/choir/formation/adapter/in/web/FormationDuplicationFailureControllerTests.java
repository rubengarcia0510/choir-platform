package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.adapter.out.persistence.FormationPositionEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationPositionRepository;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationVersionEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationVersionRepository;
import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import java.math.BigDecimal;
import java.time.Duration;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationDuplicationFailureControllerTests {

    private static final String PASSWORD = "Choir-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final FormationDuplicationPort duplicationPort;

    @MockitoSpyBean
    private FormationVersionRepository versionRepository;

    @MockitoSpyBean
    private FormationPositionRepository positionRepository;

    private Long directorId;
    private Long choirId;

    FormationDuplicationFailureControllerTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            FormationDuplicationPort duplicationPort) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.duplicationPort = duplicationPort;
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

            // Las posiciones ya se eliminaron; ahora se pueden borrar
            // los cantantes referenciados por el coro.
            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?", choirId);

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

    @Test
    void rollsBackFormationWhenInitialVersionPersistenceFails() {
        Fixture fixture = createFixture();
        preparePublishedSourceWithPositions(fixture);

        int formationsBefore = countFormations();
        int versionsBefore = countVersions();
        int positionsBefore = countPositions();

        doReturn(Mono.error(
                new IllegalStateException("forced version persistence failure")))
                .when(versionRepository)
                .save(any(FormationVersionEntity.class));

        assertThatThrownBy(() -> duplicate(fixture).block(Duration.ofSeconds(10)))
                .isInstanceOf(RuntimeException.class);

        assertThat(countFormations()).isEqualTo(formationsBefore);
        assertThat(countVersions()).isEqualTo(versionsBefore);
        assertThat(countPositions()).isEqualTo(positionsBefore);
    }

    @Test
    void rollsBackFormationVersionAndFirstPositionWhenNextPositionFails() {
        Fixture fixture = createFixture();
        preparePublishedSourceWithPositions(fixture);

        int formationsBefore = countFormations();
        int versionsBefore = countVersions();
        int positionsBefore = countPositions();

        doAnswer(invocation -> {
            Iterable<FormationPositionEntity> positions =
                    invocation.getArgument(0);

            return Flux.fromIterable(positions)
                    .take(1)
                    .concatMap(positionRepository::save)
                    .concatWith(Flux.error(
                            new IllegalStateException(
                                    "forced position persistence failure")));
        }).when(positionRepository).saveAll(any(Iterable.class));

        assertThatThrownBy(() -> duplicate(fixture).block(Duration.ofSeconds(10)))
                .isInstanceOf(RuntimeException.class);

        assertThat(countFormations()).isEqualTo(formationsBefore);
        assertThat(countVersions()).isEqualTo(versionsBefore);
        assertThat(countPositions()).isEqualTo(positionsBefore);
    }

    private Mono<FormationDuplicationPort.CreatedFormation> duplicate(
            Fixture fixture) {
        return duplicationPort.duplicate(
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                fixture.formationId(),
                null,
                "Rollback test copy " + UUID.randomUUID(),
                "Integration test");
    }

    private void preparePublishedSourceWithPositions(Fixture fixture) {
        jdbcTemplate.update("""
                UPDATE formation_versions
                SET status = 'PUBLISHED'
                WHERE formation_id = ? AND version_number = 1
                """, fixture.formationId());

        Long versionId = jdbcTemplate.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, fixture.formationId());
        assertNotNull(versionId);

        Long firstSingerId = insertSinger("Rollback Singer One");
        Long secondSingerId = insertSinger("Rollback Singer Two");

        insertPosition(versionId, firstSingerId, "Singer One", "Soprano", "Left", 0.2, 0.3);
        insertPosition(versionId, secondSingerId, "Singer Two", "Alto", "Right", 0.7, 0.6);
    }

    private Long insertSinger(String name) {
        jdbcTemplate.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, ?, TRUE)
                """, choirId, name, "Soprano");

        return jdbcTemplate.queryForObject("""
                SELECT id FROM singers
                WHERE choir_id = ? AND display_name = ?
                """, Long.class, choirId, name);
    }

    private void insertPosition(
            Long versionId,
            Long singerId,
            String singerName,
            String voicePart,
            String label,
            double x,
            double y) {
        jdbcTemplate.update("""
                INSERT INTO formation_positions (
                    formation_version_id,
                    singer_id,
                    singer_name_snapshot,
                    voice_part_snapshot,
                    position_label,
                    x_coordinate,
                    y_coordinate
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                versionId,
                singerId,
                singerName,
                voicePart,
                label,
                BigDecimal.valueOf(x),
                BigDecimal.valueOf(y));
    }

    private int countFormations() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                Integer.class,
                choirId);
    }

    private int countVersions() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM formation_versions fv
                JOIN formations f ON f.id = fv.formation_id
                WHERE f.choir_id = ?
                """, Integer.class, choirId);
    }

    private int countPositions() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM formation_positions fp
                JOIN formation_versions fv ON fv.id = fp.formation_version_id
                JOIN formations f ON f.id = fv.formation_id
                WHERE f.choir_id = ?
                """, Integer.class, choirId);
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
                "Source for duplication failure test");

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
