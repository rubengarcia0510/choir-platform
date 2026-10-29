package com.rubengarcia.choir.service;

import com.rubengarcia.choir.formation.application.exception.FormationVersionPublicationException;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FormationVersionLifecycleServiceTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionLifecycleService service;

    @Test
    void createsDraftByCopyingPublishedPositionsAndSnapshots() {
        Fixture f = fixture("PUBLISHED");

        FormationVersion draft = service.createDraft(
                f.email(), f.choirId(), f.formationId(), "Nueva revisión")
                .block(TIMEOUT);

        assertThat(draft).isNotNull();
        assertThat(draft.getVersionNumber()).isEqualTo(2);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getNotes()).isEqualTo("Nueva revisión");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM formation_positions
                WHERE formation_version_id = ?
                """, Integer.class, draft.getId())).isEqualTo(1);

        assertThat(jdbc.queryForMap("""
                SELECT singer_id, singer_name_snapshot, voice_part_snapshot,
                       voice_section_id_snapshot, voice_section_name_snapshot,
                       division_number_snapshot, position_label,
                       x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """, draft.getId()))
                .containsEntry("SINGER_ID", f.singerId())
                .containsEntry("SINGER_NAME_SNAPSHOT", "Nombre histórico")
                .containsEntry("VOICE_PART_SNAPSHOT", "SOPRANO")
                .containsEntry("VOICE_SECTION_ID_SNAPSHOT", f.sectionId())
                .containsEntry("VOICE_SECTION_NAME_SNAPSHOT", "Sopranos")
                .containsEntry("DIVISION_NUMBER_SNAPSHOT", 2)
                .containsEntry("POSITION_LABEL", "Posición original")
                .containsEntry("X_COORDINATE", new java.math.BigDecimal("1.000"))
                .containsEntry("Y_COORDINATE", new java.math.BigDecimal("2.000"));
    }

    @Test
    void publishingDraftArchivesPreviousPublishedVersion() {
        Fixture f = fixture("PUBLISHED");

        FormationVersion draft = service.createDraft(
                f.email(), f.choirId(), f.formationId(), "Versión nueva")
                .block(TIMEOUT);

        FormationVersion published = service.publish(
                f.email(), f.choirId(), f.formationId(), draft.getId())
                .block(TIMEOUT);

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedAt()).isNotNull();

        assertThat(jdbc.queryForObject("""
                SELECT status FROM formation_versions WHERE id = ?
                """, String.class, f.versionId())).isEqualTo("ARCHIVED");

        assertThat(service.published(
                f.email(), f.choirId(), f.formationId())
                .block(TIMEOUT).getId()).isEqualTo(draft.getId());

        assertThat(service.list(
                f.email(), f.choirId(), f.formationId())
                .collectList().block(TIMEOUT))
                .extracting(FormationVersion::getVersionNumber)
                .containsExactly(2, 1);
    }

    @Test
    void rejectsPublishingVersionThatIsNotDraft() {
        Fixture f = fixture("PUBLISHED");

        assertThatThrownBy(() -> service.publish(
                f.email(), f.choirId(), f.formationId(), f.versionId())
                .block(TIMEOUT))
                .isInstanceOf(FormationVersionPublicationException.class)
                .hasMessageContaining("Solo se pueden publicar versiones");
    }

    @Test
    void rejectsAccessFromAnotherDirector() {
        Fixture f = fixture("PUBLISHED");

        assertThatThrownBy(() -> service.createDraft(
                "otro-director@example.test",
                f.choirId(), f.formationId(), "No autorizado")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM formation_versions
                WHERE formation_id = ?
                """, Integer.class, f.formationId())).isEqualTo(1);
    }

    @Test
    void createsFirstDraftWithoutPositionsWhenNothingWasPublished() {
        Fixture f = fixtureWithoutVersion();

        FormationVersion draft = service.createDraft(
                f.email(), f.choirId(), f.formationId(), "Primera versión")
                .block(TIMEOUT);

        assertThat(draft.getVersionNumber()).isEqualTo(1);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM formation_positions fp
                JOIN formation_versions fv ON fv.id = fp.formation_version_id
                WHERE fv.id = ?
                """, Integer.class, draft.getId())).isZero();
    }

    @Test
    void publishesConcurrentlyWithoutLeavingMultiplePublishedVersions()
            throws Exception {
        Fixture f = fixture("PUBLISHED");

        FormationVersion firstDraft = service.createDraft(
                f.email(), f.choirId(), f.formationId(),
                "Publicación concurrente A").block(TIMEOUT);

        FormationVersion secondDraft = service.createDraft(
                f.email(), f.choirId(), f.formationId(),
                "Publicación concurrente B").block(TIMEOUT);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<FormationVersion> first = CompletableFuture.supplyAsync(
                    () -> {
                        awaitStart(start);
                        return service.publish(
                                f.email(), f.choirId(), f.formationId(),
                                firstDraft.getId()).block(TIMEOUT);
                    }, executor);

            CompletableFuture<FormationVersion> second = CompletableFuture.supplyAsync(
                    () -> {
                        awaitStart(start);
                        return service.publish(
                                f.email(), f.choirId(), f.formationId(),
                                secondDraft.getId()).block(TIMEOUT);
                    }, executor);

            start.countDown();
            CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

            assertThat(first.join().getStatus()).isEqualTo("PUBLISHED");
            assertThat(second.join().getStatus()).isEqualTo("PUBLISHED");

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM formation_versions
                    WHERE formation_id = ? AND status = 'PUBLISHED'
                    """, Integer.class, f.formationId())).isEqualTo(1);

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM formation_versions
                    WHERE formation_id = ? AND status = 'ARCHIVED'
                    """, Integer.class, f.formationId())).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackPreviousPublicationWhenPublishingDraftFails() {
        Fixture f = fixture("PUBLISHED");

        FormationVersion draft = service.createDraft(
                f.email(), f.choirId(), f.formationId(), "Fallo intermedio")
                .block(TIMEOUT);

        String constraintName = "chk_choir31_force_publish_failure";
        boolean constraintCreated = false;

        try {
            jdbc.execute("""
                    ALTER TABLE formation_versions
                    ADD CONSTRAINT chk_choir31_force_publish_failure
                    CHECK (
                        id <> %d
                        OR status <> 'PUBLISHED'
                        OR published_at IS NULL
                    )
                    """.formatted(draft.getId()));
            constraintCreated = true;

            assertThatThrownBy(() -> service.publish(
                    f.email(), f.choirId(), f.formationId(), draft.getId())
                    .block(TIMEOUT))
                    .isNotNull();

            assertThat(jdbc.queryForObject("""
                    SELECT status FROM formation_versions WHERE id = ?
                    """, String.class, f.versionId())).isEqualTo("PUBLISHED");

            assertThat(jdbc.queryForObject("""
                    SELECT status FROM formation_versions WHERE id = ?
                    """, String.class, draft.getId())).isEqualTo("DRAFT");
        } finally {
            if (constraintCreated) {
                jdbc.execute("ALTER TABLE formation_versions DROP CONSTRAINT "
                        + constraintName);
            }
        }
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "No se liberó la barrera de concurrencia");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrumpido al iniciar la publicación concurrente",
                    exception);
        }
    }

    private Fixture fixture(String status) {
        Fixture f = baseFixture();

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, ?, 'Versión inicial')
                """, f.formationId(), status);

        long versionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, f.formationId());

        jdbc.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot,
                     position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, 'Nombre histórico', 'SOPRANO', ?,
                        'Sopranos', 2, 'Posición original', 1.000, 2.000)
                """, versionId, f.singerId(), f.sectionId());

        return new Fixture(
                f.email(), f.choirId(), f.formationId(),
                f.sectionId(), f.singerId(), versionId);
    }

    private Fixture fixtureWithoutVersion() {
        return baseFixture();
    }

    private Fixture baseFixture() {
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

        jdbc.update("""
                INSERT INTO vocal_sections (choir_id, name)
                VALUES (?, 'Sopranos')
                """, choirId);

        long sectionId = jdbc.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ?",
                Long.class, choirId);

        jdbc.update("""
                INSERT INTO singers
                    (choir_id, display_name, voice_part, voice_section_id)
                VALUES (?, 'Nombre histórico', 'SOPRANO', ?)
                """, choirId, sectionId);

        long singerId = jdbc.queryForObject("""
                SELECT id FROM singers
                WHERE choir_id = ? AND display_name = 'Nombre histórico'
                """, Long.class, choirId);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación " + suffix);

        long formationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Formación " + suffix);

        return new Fixture(
                email, choirId, formationId, sectionId, singerId, null);
    }

    private record Fixture(
            String email,
            long choirId,
            long formationId,
            long sectionId,
            long singerId,
            Long versionId) {
    }
}
