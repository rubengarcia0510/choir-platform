package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionSelectionPersistencePort;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FormationVersionSelectionPersistenceIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionSelectionPersistencePort persistencePort;

    @Test
    void selectingPublishedVersionUpdatesOccurrenceAndAppendsHistory() {
        Fixture f = fixture();

        FormationVersionSelection selection = persistencePort.selectPublishedVersion(
                f.concertId(),
                f.repertoireItemId(),
                f.formationId(),
                f.versionId(),
                f.email())
                .block(TIMEOUT);

        assertThat(selection).isNotNull();
        assertThat(selection.hasSelection()).isTrue();
        assertThat(selection.formationId()).isEqualTo(f.formationId());
        assertThat(selection.formationVersionId()).isEqualTo(f.versionId());

        assertThat(jdbc.queryForMap("""
                SELECT selected_formation_id, selected_formation_version_id
                FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, f.concertId(), f.repertoireItemId()))
                .containsEntry("SELECTED_FORMATION_ID", f.formationId())
                .containsEntry("SELECTED_FORMATION_VERSION_ID", f.versionId());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, f.concertId(), f.repertoireItemId()))
                .isEqualTo(1);

        assertThat(jdbc.queryForMap("""
                SELECT previous_formation_id, previous_formation_version_id,
                       selected_formation_id, selected_formation_version_id,
                       actor_email
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, f.concertId(), f.repertoireItemId()))
                .containsEntry("PREVIOUS_FORMATION_ID", null)
                .containsEntry("PREVIOUS_FORMATION_VERSION_ID", null)
                .containsEntry("SELECTED_FORMATION_ID", f.formationId())
                .containsEntry("SELECTED_FORMATION_VERSION_ID", f.versionId())
                .containsEntry("ACTOR_EMAIL", f.email());
    }

    @Test
    void selectingSameVersionTwiceDoesNotAppendDuplicateHistory() {
        Fixture f = fixture();

        persistencePort.selectPublishedVersion(
                f.concertId(), f.repertoireItemId(), f.formationId(),
                f.versionId(), f.email()).block(TIMEOUT);

        FormationVersionSelection secondSelection = persistencePort.selectPublishedVersion(
                f.concertId(), f.repertoireItemId(), f.formationId(),
                f.versionId(), f.email()).block(TIMEOUT);

        assertThat(secondSelection).isNotNull();
        assertThat(secondSelection.formationId()).isEqualTo(f.formationId());
        assertThat(secondSelection.formationVersionId()).isEqualTo(f.versionId());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, f.concertId(), f.repertoireItemId()))
                .isEqualTo(1);
    }

    @Test
    void rejectingDraftVersionDoesNotChangeSelectionOrHistory() {
        Fixture f = fixture();

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 2, 'DRAFT', 'Borrador')
                """, f.formationId());

        long draftVersionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 2
                """, Long.class, f.formationId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                persistencePort.selectPublishedVersion(
                        f.concertId(), f.repertoireItemId(), f.formationId(),
                        draftVersionId, f.email()).block(TIMEOUT))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Solo se pueden seleccionar versiones publicadas");

        assertThat(jdbc.queryForObject("""
                SELECT selected_formation_version_id
                FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Long.class, f.concertId(), f.repertoireItemId()))
                .isNull();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, f.concertId(), f.repertoireItemId()))
                .isZero();
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

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación " + suffix);

        long formationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Formación " + suffix);

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Versión inicial')
                """, formationId);

        long versionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concierto " + suffix);

        long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concierto " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Obra " + suffix);

        long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Obra " + suffix);

        // V26 exige crear primero la ocurrencia, antes de asociarle la formación.
        jdbc.update("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index
                )
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbc.update("""
                UPDATE formations
                SET concert_id = ?, repertoire_item_id = ?
                WHERE id = ?
                """, concertId, repertoireItemId, formationId);

        return new Fixture(
                email, formationId, versionId, concertId, repertoireItemId);
    }

    private record Fixture(
            String email,
            long formationId,
            long versionId,
            long concertId,
            long repertoireItemId) {
    }
}
