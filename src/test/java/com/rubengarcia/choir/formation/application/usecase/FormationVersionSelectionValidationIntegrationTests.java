package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionSelectionPersistencePort;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FormationVersionSelectionValidationIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionSelectionPersistencePort persistencePort;

    @Test
    void archivedVersionIsRejectedWithoutChangingSelectionOrHistory() {
        Fixture f = fixture();
        selectInitialVersion(f);

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 2, 'ARCHIVED', 'Versión archivada')
                """, f.formationId());

        long archivedVersionId = jdbc.queryForObject("""
                SELECT id
                FROM formation_versions
                WHERE formation_id = ? AND version_number = 2
                """, Long.class, f.formationId());

        assertRejected(
                f,
                f.formationId(),
                archivedVersionId,
                HttpStatus.CONFLICT);
    }

    @Test
    void versionFromAnotherFormationIsRejectedWithoutChangingSelectionOrHistory() {
        Fixture f = fixture();
        selectInitialVersion(f);

        String suffix = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO formations (
                    choir_id, name, concert_id, repertoire_item_id
                )
                VALUES (?, ?, ?, ?)
                """,
                f.choirId(),
                "Formación ajena " + suffix,
                f.concertId(),
                f.repertoireItemId());

        long otherFormationId = jdbc.queryForObject("""
                SELECT id
                FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, f.choirId(), "Formación ajena " + suffix);

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Versión de otra formación')
                """, otherFormationId);

        long foreignVersionId = jdbc.queryForObject("""
                SELECT id
                FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, otherFormationId);

        assertRejected(
                f,
                f.formationId(),
                foreignVersionId,
                HttpStatus.NOT_FOUND);
    }

    @Test
    void publishedVersionFromAnotherOccurrenceIsRejectedWithoutChangingSelectionOrHistory() {
        Fixture f = fixture();
        selectInitialVersion(f);

        String suffix = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, f.choirId(), "Otro concierto " + suffix);

        long otherConcertId = jdbc.queryForObject("""
                SELECT id
                FROM concerts
                WHERE choir_id = ? AND title = ?
                """, Long.class, f.choirId(), "Otro concierto " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, f.choirId(), "Otra obra " + suffix);

        long otherRepertoireItemId = jdbc.queryForObject("""
                SELECT id
                FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, f.choirId(), "Otra obra " + suffix);

        jdbc.update("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index
                )
                VALUES (?, ?, 0)
                """, otherConcertId, otherRepertoireItemId);

        jdbc.update("""
                INSERT INTO formations (
                    choir_id, name, concert_id, repertoire_item_id
                )
                VALUES (?, ?, ?, ?)
                """,
                f.choirId(),
                "Formación de otra ocurrencia " + suffix,
                otherConcertId,
                otherRepertoireItemId);

        long otherFormationId = jdbc.queryForObject("""
                SELECT id
                FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, f.choirId(),
                "Formación de otra ocurrencia " + suffix);

        jdbc.update("""
                INSERT INTO formation_versions (
                    formation_id, version_number, status, notes
                )
                VALUES (?, 1, 'PUBLISHED', 'Versión publicada de otra ocurrencia')
                """, otherFormationId);

        long otherVersionId = jdbc.queryForObject("""
                SELECT id
                FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, otherFormationId);

        assertRejected(
                f,
                otherFormationId,
                otherVersionId,
                HttpStatus.NOT_FOUND);
    }

    @Test
    void nonexistentVersionIsRejectedWithoutChangingSelectionOrHistory() {
        Fixture f = fixture();
        selectInitialVersion(f);

        assertRejected(
                f,
                f.formationId(),
                Long.MAX_VALUE,
                HttpStatus.NOT_FOUND);
    }

    private void assertRejected(
            Fixture f,
            long requestedFormationId,
            long requestedVersionId,
            HttpStatus expectedStatus) {

        assertThatThrownBy(() -> persistencePort.selectPublishedVersion(
                f.concertId(),
                f.repertoireItemId(),
                requestedFormationId,
                requestedVersionId,
                f.email()).block(TIMEOUT))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(expectedStatus));

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
                SELECT previous_formation_id,
                       previous_formation_version_id,
                       selected_formation_id,
                       selected_formation_version_id
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, f.concertId(), f.repertoireItemId()))
                .containsEntry("PREVIOUS_FORMATION_ID", null)
                .containsEntry("PREVIOUS_FORMATION_VERSION_ID", null)
                .containsEntry("SELECTED_FORMATION_ID", f.formationId())
                .containsEntry("SELECTED_FORMATION_VERSION_ID", f.versionId());
    }

    private void selectInitialVersion(Fixture f) {
        FormationVersionSelection selection = persistencePort.selectPublishedVersion(
                f.concertId(),
                f.repertoireItemId(),
                f.formationId(),
                f.versionId(),
                f.email()).block(TIMEOUT);

        assertThat(selection).isNotNull();
        assertThat(selection.formationId()).isEqualTo(f.formationId());
        assertThat(selection.formationVersionId()).isEqualTo(f.versionId());
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);

        long directorId = jdbc.queryForObject("""
                SELECT id
                FROM directors
                WHERE email = ?
                """, Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro " + suffix);

        long choirId = jdbc.queryForObject("""
                SELECT id
                FROM choirs
                WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Coro " + suffix);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación " + suffix);

        long formationId = jdbc.queryForObject("""
                SELECT id
                FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, "Formación " + suffix);

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Versión inicial')
                """, formationId);

        long versionId = jdbc.queryForObject("""
                SELECT id
                FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concierto " + suffix);

        long concertId = jdbc.queryForObject("""
                SELECT id
                FROM concerts
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Concierto " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Obra " + suffix);

        long repertoireItemId = jdbc.queryForObject("""
                SELECT id
                FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Obra " + suffix);

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
                email,
                choirId,
                formationId,
                versionId,
                concertId,
                repertoireItemId);
    }

    private record Fixture(
            String email,
            long choirId,
            long formationId,
            long versionId,
            long concertId,
            long repertoireItemId) {
    }
}
