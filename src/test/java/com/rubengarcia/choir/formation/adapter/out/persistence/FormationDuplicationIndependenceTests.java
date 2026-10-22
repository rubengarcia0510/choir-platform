package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
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
    private JdbcTemplate jdbcTemplate;

    private Long directorId;
    private Long choirId;
    private Long singerId;
    private Long concertId;
    private Long repertoireItemId;
    private Long formationId;
    private Long versionId;

    @AfterEach
    void cleanUp() {
        if (formationId != null) {
            jdbcTemplate.update("""
                    DELETE FROM formation_positions
                    WHERE formation_version_id IN (
                        SELECT id FROM formation_versions WHERE formation_id = ?
                    )
                    """, formationId);
            jdbcTemplate.update(
                    "DELETE FROM formation_versions WHERE formation_id = ?",
                    formationId);
            jdbcTemplate.update(
                    "DELETE FROM formations WHERE id = ?", formationId);
        }

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
            jdbcTemplate.update(
                    "DELETE FROM concert_repertoire WHERE concert_id = ?", concertId);
            jdbcTemplate.update(
                    "DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?", choirId);
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

        FormationCatalogPort.CreatedFormation duplicate =
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

    private void createFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "dup-independence-" + suffix + "@example.test";
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
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, ?, TRUE)
                """, choirId, "Cantante " + suffix, "SOPRANO");
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
                VALUES (?, 1, 'DRAFT')
                """, formationId);
        versionId = jdbcTemplate.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, versionId, singerId, "Snapshot original", "SOPRANO",
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
