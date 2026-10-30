package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import com.rubengarcia.choir.formation.application.usecase.FormationVersionLifecycleUseCase;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FormationDuplicationIndependenceTests {

    @Autowired
    private FormationDuplicationPort duplicationPort;

    @Autowired
    private FormationVersionLifecycleUseCase versionLifecycleUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long directorId;
    private String email;
    private Long choirId;
    private Long sectionId;
    private Long singerId;
    private Long concertId;
    private Long repertoireItemId;
    private Long formationId;
    private Long versionId;

    @AfterEach
    void cleanUp() {
        if (choirId != null) {
            jdbcTemplate.update("""
                    UPDATE concert_repertoire
                    SET selected_formation_id = NULL,
                        selected_formation_version_id = NULL
                    WHERE concert_id = ?
                    """, concertId);
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
            jdbcTemplate.update(
                    "DELETE FROM concert_repertoire WHERE concert_id = ?", concertId);
            jdbcTemplate.update(
                    "DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM choirs WHERE id = ?", choirId);
        }

        if (directorId != null) {
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?", directorId);
        }
    }

    @Test
    void changesToEitherFormationDoNotMutateTheOther() {
        createFixture();

        FormationDuplicationPort.CreatedFormation duplicate =
                duplicationPort.duplicate(
                                choirId,
                                concertId,
                                repertoireItemId,
                                formationId,
                                null,
                                "Copia " + UUID.randomUUID(),
                                "Prueba de independencia")
                        .block(java.time.Duration.ofSeconds(10));

        assertThat(duplicate).isNotNull();

        Long duplicateFormationId = duplicate.formation().getId();
        Long duplicateVersionId = duplicate.draftVersion().getId();

        Long originalPositionId = positionId(versionId);
        Long duplicatePositionId = positionId(duplicateVersionId);

        assertThat(duplicatePositionId).isNotEqualTo(originalPositionId);

        jdbcTemplate.update("""
                UPDATE formation_positions
                SET position_label = ?, x_coordinate = ?
                WHERE id = ?
                """, "Cambio en copia", new BigDecimal("70.000"), duplicatePositionId);

        assertPosition(originalPositionId, "Posición original", "10.000");
        assertPosition(duplicatePositionId, "Cambio en copia", "70.000");

        jdbcTemplate.update("""
                UPDATE formation_positions
                SET position_label = ?, x_coordinate = ?
                WHERE id = ?
                """, "Cambio en original", new BigDecimal("30.000"), originalPositionId);

        assertPosition(originalPositionId, "Cambio en original", "30.000");
        assertPosition(duplicatePositionId, "Cambio en copia", "70.000");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT formation_id FROM formation_versions WHERE id = ?",
                Long.class, duplicateVersionId))
                .isEqualTo(duplicateFormationId);
    }

    @Test
    void publishingDuplicatePreservesOriginalSnapshotsAndSelectedVersion() {
        createFixture();

        jdbcTemplate.update("""
                UPDATE concert_repertoire
                SET selected_formation_id = ?,
                    selected_formation_version_id = ?
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, formationId, versionId, concertId, repertoireItemId);

        FormationDuplicationPort.CreatedFormation duplicate =
                duplicationPort.duplicate(
                                choirId,
                                concertId,
                                repertoireItemId,
                                formationId,
                                null,
                                "Copia publicada " + UUID.randomUUID(),
                                "Copia independiente")
                        .block(java.time.Duration.ofSeconds(10));

        assertThat(duplicate).isNotNull();
        assertThat(duplicate.formation().getId()).isNotEqualTo(formationId);
        assertThat(duplicate.draftVersion().getId()).isNotEqualTo(versionId);
        assertThat(duplicate.draftVersion().getVersionNumber()).isEqualTo(1);
        assertThat(duplicate.draftVersion().getStatus()).isEqualTo("DRAFT");

        Long copiedPositionId = positionId(duplicate.draftVersion().getId());
        assertThat(copiedPositionId).isNotEqualTo(positionId(versionId));
        assertThat(jdbcTemplate.queryForMap("""
                SELECT singer_id, singer_name_snapshot, voice_part_snapshot,
                       voice_section_id_snapshot, voice_section_name_snapshot,
                       division_number_snapshot, position_label,
                       x_coordinate, y_coordinate
                FROM formation_positions
                WHERE id = ?
                """, copiedPositionId))
                .containsEntry("singer_id", singerId)
                .containsEntry("singer_name_snapshot", "Snapshot original")
                .containsEntry("voice_part_snapshot", "SOPRANO")
                .containsEntry("voice_section_id_snapshot", sectionId)
                .containsEntry("voice_section_name_snapshot", "Sopranos snapshot")
                .containsEntry("division_number_snapshot", 2)
                .containsEntry("position_label", "Posición original")
                .containsEntry("x_coordinate", new BigDecimal("10.000"))
                .containsEntry("y_coordinate", new BigDecimal("20.000"));

        jdbcTemplate.update("""
                UPDATE formation_positions
                SET position_label = ?, x_coordinate = ?
                WHERE id = ?
                """, "Posición editada en copia",
                new BigDecimal("70.000"), copiedPositionId);

        var publishedCopy = versionLifecycleUseCase.publish(
                        email,
                        choirId,
                        duplicate.formation().getId(),
                        duplicate.draftVersion().getId())
                .block(java.time.Duration.ofSeconds(10));

        assertThat(publishedCopy).isNotNull();
        assertThat(publishedCopy.getStatus()).isEqualTo("PUBLISHED");
        assertPosition(positionId(versionId), "Posición original", "10.000");
        assertPosition(copiedPositionId, "Posición editada en copia", "70.000");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM formation_versions WHERE id = ?
                """, String.class, versionId))
                .isEqualTo("PUBLISHED");

        assertThat(jdbcTemplate.queryForMap("""
                SELECT selected_formation_id, selected_formation_version_id
                FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, concertId, repertoireItemId))
                .containsEntry("selected_formation_id", formationId)
                .containsEntry("selected_formation_version_id", versionId);
    }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString();
        email = "dup-independence-" + suffix + "@example.test";
        String choirName = "Coro " + suffix;
        String concertName = "Concierto " + suffix;
        String repertoireName = "Obra " + suffix;
        String formationName = "Formación original " + suffix;

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director " + suffix, email);
        directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);
        choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, choirName);

        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name)
                VALUES (?, 'Sopranos snapshot')
                """, choirId);
        sectionId = jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_sections WHERE choir_id = ?
                """, Long.class, choirId);

        jdbcTemplate.update("""
                INSERT INTO singers (
                    choir_id, display_name, voice_part, voice_section_id, active
                ) VALUES (?, ?, 'SOPRANO', ?, TRUE)
                """, choirId, "Cantante " + suffix, sectionId);
        singerId = jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, "Cantante " + suffix);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, concertName);
        concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, concertName);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, repertoireName);
        repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, repertoireName);

        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, ?)
                """, choirId, concertId, repertoireItemId,
                formationName, "Formación de origen");
        formationId = jdbcTemplate.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """, Long.class, choirId, concertId, repertoireItemId, formationName);

        jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED')
                """, formationId);
        versionId = jdbcTemplate.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot,
                     position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, ?, 'SOPRANO', ?, 'Sopranos snapshot', 2, ?, ?, ?)
                """, versionId, singerId, "Snapshot original", sectionId,
                "Posición original", new BigDecimal("10.000"), new BigDecimal("20.000"));
    }

    private Long positionId(Long targetVersionId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM formation_positions WHERE formation_version_id = ?
                """, Long.class, targetVersionId);
    }

    private void assertPosition(Long positionId, String expectedLabel, String expectedX) {
        var position = jdbcTemplate.queryForMap("""
                SELECT position_label, x_coordinate
                FROM formation_positions WHERE id = ?
                """, positionId);

        assertThat(position.get("position_label")).isEqualTo(expectedLabel);
        assertThat(new BigDecimal(position.get("x_coordinate").toString()))
                .isEqualByComparingTo(expectedX);
    }
}
