package com.rubengarcia.choir.transition;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionLifecyclePort;
import com.rubengarcia.choir.transition.application.usecase.TransitionVersionLifecycleService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransitionVersionLifecycleServiceTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransitionVersionLifecycleService service;

    @Autowired
    private TransitionVersionLifecyclePort lifecyclePort;

    @Test
    void createsAndListsDraftsWithIncreasingVersionNumbers() {
        Fixture fixture = fixture();

        TransitionVersion first = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Indicaciones iniciales")
                .block(TIMEOUT);
        TransitionVersion second = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                null)
                .block(TIMEOUT);

        assertThat(first).isNotNull();
        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo("DRAFT");
        assertThat(first.getInstructions()).isEqualTo("Indicaciones iniciales");
        assertThat(first.getFromFormationVersionId())
                .isEqualTo(fixture.fromFormationVersionId());
        assertThat(first.getToFormationVersionId())
                .isEqualTo(fixture.toFormationVersionId());
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(service.list(
                fixture.email(), fixture.choirId(), fixture.transitionId())
                .collectList().block(TIMEOUT))
                .extracting(TransitionVersion::getVersionNumber)
                .containsExactly(2, 1);
    }

    @Test
    void rejectsFormationVersionsFromAnotherChoir() {
        Fixture fixture = fixture();
        long foreignFormationVersionId = foreignFormationVersion();

        assertThatThrownBy(() -> service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                foreignFormationVersionId,
                "No debe guardarse")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no pertenecen");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE transition_id = ?
                """, Integer.class, fixture.transitionId())).isZero();
    }

    @Test
    void rejectsUsingTheSameFormationVersionOnBothSides() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.fromFormationVersionId(),
                "Origen y destino iguales")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE transition_id = ?
                """, Integer.class, fixture.transitionId())).isZero();
    }

    @Test
    void publishesDraftWithoutChangingPreviouslyPublishedVersions() {
        Fixture fixture = fixture();
        jdbc.update("""
                INSERT INTO transition_versions
                    (transition_id, version_number,
                     from_formation_version_id, to_formation_version_id,
                     status, instructions, published_at)
                VALUES (?, 1, ?, ?, 'PUBLISHED', 'Histórica',
                        TIMESTAMP '2025-01-02 03:04:05')
                """,
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId());
        long previousVersionId = jdbc.queryForObject("""
                SELECT id FROM transition_versions
                WHERE transition_id = ? AND version_number = 1
                """, Long.class, fixture.transitionId());
        Object previousPublishedAt = jdbc.queryForObject("""
                SELECT published_at FROM transition_versions WHERE id = ?
                """, Object.class, previousVersionId);

        TransitionVersion draft = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Nueva versión")
                .block(TIMEOUT);
        TransitionVersion published = lifecyclePort.publishAfterRuleEvaluation(
                fixture.choirId(),
                fixture.transitionId(),
                draft.getId())
                .block(TIMEOUT);

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(jdbc.queryForMap("""
                SELECT status, instructions, published_at
                FROM transition_versions WHERE id = ?
                """, previousVersionId))
                .containsEntry("STATUS", "PUBLISHED")
                .containsEntry("INSTRUCTIONS", "Histórica")
                .containsEntry("PUBLISHED_AT", previousPublishedAt);

        assertThatThrownBy(() -> lifecyclePort.publishAfterRuleEvaluation(
                fixture.choirId(),
                fixture.transitionId(),
                draft.getId())
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("estado DRAFT");
    }

    @Test
    void rejectsAccessFromAnotherDirector() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.createDraft(
                "otro-director@example.test",
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "No autorizado")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE transition_id = ?
                """, Integer.class, fixture.transitionId())).isZero();
    }

    @Test
    void rejectsListingAnotherDirectorsTransition() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.list(
                "otro-director@example.test",
                fixture.choirId(),
                fixture.transitionId())
                .collectList().block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsListingTransitionOutsideTheRequestedChoir() {
        Fixture fixture = fixture();
        long otherChoirId = createOtherChoir();

        assertThatThrownBy(() -> service.list(
                fixture.email(), otherChoirId, fixture.transitionId())
                .collectList().block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void serializesConcurrentDraftNumberAllocation() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<TransitionVersion> first = CompletableFuture.supplyAsync(
                    () -> createAfter(start, fixture), executor);
            CompletableFuture<TransitionVersion> second = CompletableFuture.supplyAsync(
                    () -> createAfter(start, fixture), executor);

            start.countDown();
            CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

            assertThat(first.join().getVersionNumber()).isNotEqualTo(
                    second.join().getVersionNumber());
            assertThat(jdbc.queryForList("""
                    SELECT version_number FROM transition_versions
                    WHERE transition_id = ?
                    ORDER BY version_number
                    """, Integer.class, fixture.transitionId()))
                    .containsExactly(1, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private TransitionVersion createAfter(CountDownLatch start, Fixture fixture) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "No se liberó la barrera de creación concurrente");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrumpido al crear versiones concurrentemente", exception);
        }

        return service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Versión concurrente")
                .block(TIMEOUT);
    }

    private long foreignFormationVersion() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director externo', ?)
                """, email);
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro externo " + suffix);
        long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro externo " + suffix);
        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación externa " + suffix);
        long formationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Formación externa " + suffix);
        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Externa')
                """, formationId);
        return jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private long createOtherChoir() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Otro director', ?)
                """, email);
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Otro coro " + suffix);
        return jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Otro coro " + suffix);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro " + suffix);
        long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro " + suffix);

        long fromFormationId = createFormation(choirId, "FROM " + suffix);
        long toFormationId = createFormation(choirId, "TO " + suffix);
        long fromVersionId = createFormationVersion(fromFormationId);
        long toVersionId = createFormationVersion(toFormationId);

        jdbc.update("""
                INSERT INTO transitions (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Transición " + suffix);
        long transitionId = jdbc.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Transición " + suffix);

        return new Fixture(
                email, choirId, transitionId, fromVersionId, toVersionId);
    }

    private long createFormation(long choirId, String name) {
        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, name);
        return jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private long createFormationVersion(long formationId) {
        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Versión de prueba')
                """, formationId);
        return jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private record Fixture(
            String email,
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId) {
    }
}
