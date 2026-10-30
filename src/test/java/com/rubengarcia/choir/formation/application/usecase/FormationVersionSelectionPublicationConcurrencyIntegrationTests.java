package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionSelectionPersistencePort;
import com.rubengarcia.choir.formation.application.usecase.FormationVersionLifecycleUseCase;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
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

@SpringBootTest
class FormationVersionSelectionPublicationConcurrencyIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionLifecycleUseCase lifecycle;

    @Autowired
    private FormationVersionSelectionPersistencePort selectionPersistence;

    @Test
    void concurrentSelectionAndPublicationNeverLeaveArchivedVersionSelected()
            throws Exception {
        Fixture f = fixture();

        FormationVersion draft = lifecycle.createDraft(
                f.email(),
                f.choirId(),
                f.formationId(),
                "Versión publicada concurrentemente")
                .block(TIMEOUT);

        assertThat(draft).isNotNull();
        assertThat(draft.getStatus()).isEqualTo("DRAFT");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<SelectionOutcome> selectionFuture =
                    CompletableFuture.supplyAsync(() -> {
                        awaitStart(start);

                        try {
                            FormationVersionSelection selection =
                                    selectionPersistence.selectPublishedVersion(
                                            f.concertId(),
                                            f.repertoireItemId(),
                                            f.formationId(),
                                            f.initialVersionId(),
                                            f.email())
                                            .block(TIMEOUT);

                            return new SelectionOutcome(selection, null);
                        } catch (Throwable exception) {
                            return new SelectionOutcome(null, exception);
                        }
                    }, executor);

            CompletableFuture<FormationVersion> publicationFuture =
                    CompletableFuture.supplyAsync(() -> {
                        awaitStart(start);

                        return lifecycle.publish(
                                f.email(),
                                f.choirId(),
                                f.formationId(),
                                draft.getId())
                                .block(TIMEOUT);
                    }, executor);

            start.countDown();

            CompletableFuture.allOf(
                    selectionFuture,
                    publicationFuture)
                    .get(30, TimeUnit.SECONDS);

            SelectionOutcome selectionOutcome = selectionFuture.join();
            FormationVersion published = publicationFuture.join();

            assertThat(published).isNotNull();
            assertThat(published.getId()).isEqualTo(draft.getId());
            assertThat(published.getStatus()).isEqualTo("PUBLISHED");

            String initialVersionStatus = jdbc.queryForObject("""
                    SELECT status
                    FROM formation_versions
                    WHERE id = ?
                    """, String.class, f.initialVersionId());

            Long selectedVersionId = jdbc.queryForObject("""
                    SELECT selected_formation_version_id
                    FROM concert_repertoire
                    WHERE concert_id = ?
                      AND repertoire_item_id = ?
                    """, Long.class, f.concertId(), f.repertoireItemId());

            Integer historyCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM formation_version_selection_history
                    WHERE concert_id = ?
                      AND repertoire_item_id = ?
                    """, Integer.class, f.concertId(), f.repertoireItemId());

            if (selectedVersionId == null) {
                // La publicación ganó el lock: la selección posterior
                // debe rechazar la versión que ya quedó archivada.
                assertThat(initialVersionStatus).isEqualTo("ARCHIVED");
                assertThat(selectionOutcome.selection()).isNull();
                assertThat(selectionOutcome.error())
                        .isInstanceOf(ResponseStatusException.class)
                        .hasMessageContaining(
                                "Solo se pueden seleccionar versiones publicadas");
                assertThat(historyCount).isZero();
            } else {
                // La selección ganó el lock: publicar debe conservar
                // como PUBLISHED la versión que continúa seleccionada.
                assertThat(selectedVersionId).isEqualTo(f.initialVersionId());
                assertThat(initialVersionStatus).isEqualTo("PUBLISHED");
                assertThat(selectionOutcome.error()).isNull();
                assertThat(selectionOutcome.selection()).isNotNull();
                assertThat(selectionOutcome.selection().formationVersionId())
                        .isEqualTo(f.initialVersionId());
                assertThat(historyCount).isEqualTo(1);
            }

            if (selectedVersionId != null) {
                assertThat(jdbc.queryForObject("""
                        SELECT status
                        FROM formation_versions
                        WHERE id = ?
                        """, String.class, selectedVersionId))
                        .isEqualTo("PUBLISHED");
            }

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM formation_versions
                    WHERE formation_id = ?
                      AND status = 'PUBLISHED'
                    """, Integer.class, f.formationId()))
                    .isEqualTo(selectedVersionId == null ? 1 : 2);
        } finally {
            executor.shutdownNow();
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
                    "Interrumpido al iniciar la operación concurrente",
                    exception);
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de concurrencia', ?)
                """, email);

        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro concurrencia " + suffix);

        long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs
                WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Coro concurrencia " + suffix);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación concurrencia " + suffix);

        long formationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, "Formación concurrencia " + suffix);

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Versión inicial')
                """, formationId);

        long initialVersionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concierto concurrencia " + suffix);

        long concertId = jdbc.queryForObject("""
                SELECT id FROM concerts
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Concierto concurrencia " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Obra concurrencia " + suffix);

        long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Obra concurrencia " + suffix);

        jdbc.update("""
                INSERT INTO concert_repertoire (
                    concert_id,
                    repertoire_item_id,
                    order_index
                )
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbc.update("""
                UPDATE formations
                SET concert_id = ?,
                    repertoire_item_id = ?
                WHERE id = ?
                """, concertId, repertoireItemId, formationId);

        return new Fixture(
                email,
                choirId,
                formationId,
                initialVersionId,
                concertId,
                repertoireItemId);
    }

    private record Fixture(
            String email,
            long choirId,
            long formationId,
            long initialVersionId,
            long concertId,
            long repertoireItemId) {
    }

    private record SelectionOutcome(
            FormationVersionSelection selection,
            Throwable error) {
    }
}
