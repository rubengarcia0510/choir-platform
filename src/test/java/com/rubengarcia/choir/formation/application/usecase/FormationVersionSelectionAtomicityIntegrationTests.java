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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FormationVersionSelectionAtomicityIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionSelectionPersistencePort persistencePort;

    @Test
    void successiveSelectionsPreserveCompleteImmutableHistory() {
        Fixture f = fixture();

        long secondVersionId = createVersion(f.formationId(), 2);
        long thirdVersionId = createVersion(f.formationId(), 3);

        select(f, f.versionId());
        select(f, secondVersionId);
        select(f, thirdVersionId);

        assertThat(selectedVersionId(f)).isEqualTo(thirdVersionId);
        assertThat(historyCount(f)).isEqualTo(3);

        assertThat(jdbc.queryForList("""
                SELECT previous_formation_version_id
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                ORDER BY id
                """, Long.class, f.concertId(), f.repertoireItemId()))
                .containsExactly(null, f.versionId(), secondVersionId);

        assertThat(jdbc.queryForList("""
                SELECT selected_formation_version_id
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                ORDER BY id
                """, Long.class, f.concertId(), f.repertoireItemId()))
                .containsExactly(f.versionId(), secondVersionId, thirdVersionId);

        assertThat(jdbc.queryForList("""
                SELECT actor_email
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                ORDER BY id
                """, String.class, f.concertId(), f.repertoireItemId()))
                .containsExactly(f.email(), f.email(), f.email());
    }

    @Test
    void historyInsertFailureDoesNotChangeCurrentSelection() {
        Fixture f = fixture();
        long nextVersionId = createVersion(f.formationId(), 2);
        select(f, f.versionId());

        String constraint = "chk_selection_history_force_failure";
        boolean created = false;

        try {
            jdbc.execute("""
                    ALTER TABLE formation_version_selection_history
                    ADD CONSTRAINT chk_selection_history_force_failure
                    CHECK (selected_formation_version_id <> %d)
                    """.formatted(nextVersionId));
            created = true;

            assertThatThrownBy(() -> select(f, nextVersionId))
                    .isNotNull();

            assertThat(selectedVersionId(f)).isEqualTo(f.versionId());
            assertThat(historyCount(f)).isEqualTo(1);

            assertThat(jdbc.queryForObject("""
                    SELECT selected_formation_version_id
                    FROM formation_version_selection_history
                    WHERE concert_id = ? AND repertoire_item_id = ?
                    """, Long.class, f.concertId(), f.repertoireItemId()))
                    .isEqualTo(f.versionId());
        } finally {
            if (created) {
                jdbc.execute("ALTER TABLE formation_version_selection_history "
                        + "DROP CONSTRAINT " + constraint);
            }
        }
    }

    @Test
    void selectionUpdateFailureRollsBackPreviouslyInsertedHistory() {
        Fixture f = fixture();
        long nextVersionId = createVersion(f.formationId(), 2);
        select(f, f.versionId());

        String constraint = "chk_selection_update_force_failure";
        boolean created = false;

        try {
            jdbc.execute("""
                    ALTER TABLE concert_repertoire
                    ADD CONSTRAINT chk_selection_update_force_failure
                    CHECK (
                        selected_formation_version_id IS NULL
                        OR selected_formation_version_id <> %d
                    )
                    """.formatted(nextVersionId));
            created = true;

            assertThatThrownBy(() -> select(f, nextVersionId))
                    .isNotNull();

            assertThat(selectedVersionId(f)).isEqualTo(f.versionId());
            assertThat(historyCount(f)).isEqualTo(1);

            assertThat(jdbc.queryForMap("""
                    SELECT previous_formation_version_id,
                           selected_formation_version_id
                    FROM formation_version_selection_history
                    WHERE concert_id = ? AND repertoire_item_id = ?
                    """, f.concertId(), f.repertoireItemId()))
                    .containsEntry(
                            "PREVIOUS_FORMATION_VERSION_ID", null)
                    .containsEntry(
                            "SELECTED_FORMATION_VERSION_ID", f.versionId());
        } finally {
            if (created) {
                jdbc.execute("ALTER TABLE concert_repertoire "
                        + "DROP CONSTRAINT " + constraint);
            }
        }
    }

    private void select(Fixture f, long versionId) {
        FormationVersionSelection selection =
                persistencePort.selectPublishedVersion(
                        f.concertId(),
                        f.repertoireItemId(),
                        f.formationId(),
                        versionId,
                        f.email()).block(TIMEOUT);

        assertThat(selection).isNotNull();
        assertThat(selection.formationId()).isEqualTo(f.formationId());
        assertThat(selection.formationVersionId()).isEqualTo(versionId);
    }

    private long selectedVersionId(Fixture f) {
        return jdbc.queryForObject("""
                SELECT selected_formation_version_id
                FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Long.class, f.concertId(), f.repertoireItemId());
    }

    private int historyCount(Fixture f) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM formation_version_selection_history
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, f.concertId(), f.repertoireItemId());
    }

    private long createVersion(long formationId, int versionNumber) {
        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, ?, 'PUBLISHED', 'Versión de prueba')
                """, formationId, versionNumber);

        return jdbc.queryForObject("""
                SELECT id
                FROM formation_versions
                WHERE formation_id = ? AND version_number = ?
                """, Long.class, formationId, versionNumber);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de atomicidad', ?)
                """, email);

        long directorId = jdbc.queryForObject("""
                SELECT id FROM directors WHERE email = ?
                """, Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro " + suffix);

        long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs
                WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Coro " + suffix);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación " + suffix);

        long formationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, "Formación " + suffix);

        long versionId = createVersion(formationId, 1);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concierto " + suffix);

        long concertId = jdbc.queryForObject("""
                SELECT id FROM concerts
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Concierto " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Obra " + suffix);

        long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items
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
                formationId,
                versionId,
                concertId,
                repertoireItemId);
    }

    private record Fixture(
            String email,
            long formationId,
            long versionId,
            long concertId,
            long repertoireItemId) {
    }
}
