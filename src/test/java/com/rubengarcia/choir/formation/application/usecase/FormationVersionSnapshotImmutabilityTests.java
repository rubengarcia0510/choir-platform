package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FormationVersionSnapshotImmutabilityTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionLifecycleUseCase useCase;

    @Test
    void preservesHistoricalSnapshotsWhenSingerDataChanges() {
        Fixture fixture = fixture();

        FormationVersion initialDraft = useCase.createDraft(
                fixture.email(), fixture.choirId(),
                fixture.formationId(), "Primera versión")
                .block(TIMEOUT);

        assertThat(initialDraft).isNotNull();

        jdbc.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot,
                     position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, 'Nombre histórico', 'SOPRANO', ?,
                        'Sopranos históricos', 2, 'Posición histórica',
                        1.000, 2.000)
                """,
                initialDraft.getId(), fixture.singerId(), fixture.sectionId());

        useCase.publish(
                fixture.email(), fixture.choirId(),
                fixture.formationId(), initialDraft.getId())
                .block(TIMEOUT);

        jdbc.update("""
                UPDATE singers
                SET display_name = 'Nombre actualizado',
                    voice_part = 'ALTO'
                WHERE id = ?
                """, fixture.singerId());

        FormationVersion nextDraft = useCase.createDraft(
                fixture.email(), fixture.choirId(),
                fixture.formationId(), "Segunda versión")
                .block(TIMEOUT);

        assertThat(nextDraft).isNotNull();
        assertThat(nextDraft.getVersionNumber())
                .isEqualTo(initialDraft.getVersionNumber() + 1);

        Map<String, Object> originalSnapshot = jdbc.queryForMap("""
                SELECT singer_name_snapshot, voice_part_snapshot,
                       voice_section_name_snapshot, division_number_snapshot,
                       position_label, x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """, initialDraft.getId());

        Map<String, Object> copiedSnapshot = jdbc.queryForMap("""
                SELECT singer_name_snapshot, voice_part_snapshot,
                       voice_section_name_snapshot, division_number_snapshot,
                       position_label, x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """, nextDraft.getId());

        assertThat(originalSnapshot)
                .containsEntry("SINGER_NAME_SNAPSHOT", "Nombre histórico")
                .containsEntry("VOICE_PART_SNAPSHOT", "SOPRANO")
                .containsEntry("VOICE_SECTION_NAME_SNAPSHOT", "Sopranos históricos")
                .containsEntry("DIVISION_NUMBER_SNAPSHOT", 2)
                .containsEntry("POSITION_LABEL", "Posición histórica");

        assertThat(copiedSnapshot)
                .containsEntry("SINGER_NAME_SNAPSHOT", "Nombre histórico")
                .containsEntry("VOICE_PART_SNAPSHOT", "SOPRANO")
                .containsEntry("VOICE_SECTION_NAME_SNAPSHOT", "Sopranos históricos")
                .containsEntry("DIVISION_NUMBER_SNAPSHOT", 2)
                .containsEntry("POSITION_LABEL", "Posición histórica");

        assertThat(jdbc.queryForObject("""
                SELECT display_name FROM singers WHERE id = ?
                """, String.class, fixture.singerId()))
                .isEqualTo("Nombre actualizado");

        assertThat(jdbc.queryForObject("""
                SELECT voice_part FROM singers WHERE id = ?
                """, String.class, fixture.singerId()))
                .isEqualTo("ALTO");
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        String choirName = "Coro " + suffix;
        String formationName = "Formación " + suffix;

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, choirName);

        Long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs
                WHERE director_id = ? AND name = ?
                """, Long.class, directorId, choirName);

        jdbc.update("""
                INSERT INTO vocal_sections (choir_id, name)
                VALUES (?, 'Sopranos')
                """, choirId);

        Long sectionId = jdbc.queryForObject("""
                SELECT id FROM vocal_sections WHERE choir_id = ?
                """, Long.class, choirId);

        jdbc.update("""
                INSERT INTO singers
                    (choir_id, display_name, voice_part, voice_section_id)
                VALUES (?, 'Nombre histórico', 'SOPRANO', ?)
                """, choirId, sectionId);

        Long singerId = jdbc.queryForObject("""
                SELECT id FROM singers
                WHERE choir_id = ? AND display_name = 'Nombre histórico'
                """, Long.class, choirId);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, formationName);

        Long formationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, formationName);

        return new Fixture(email, choirId, formationId, sectionId, singerId);
    }

    private record Fixture(
            String email,
            long choirId,
            long formationId,
            long sectionId,
            long singerId) {
    }
}
