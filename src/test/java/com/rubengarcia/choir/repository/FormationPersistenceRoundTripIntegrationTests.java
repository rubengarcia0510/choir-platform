package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.formation.adapter.out.persistence.FormationEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationPositionEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationPositionRepository;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationRepository;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationVersionEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationPersistenceRoundTripIntegrationTests {

    private final JdbcTemplate jdbc;
    private final FormationRepository formations;
    private final FormationVersionRepository formationVersions;
    private final FormationPositionRepository positions;

    FormationPersistenceRoundTripIntegrationTests(
            JdbcTemplate jdbc,
            FormationRepository formations,
            FormationVersionRepository formationVersions,
            FormationPositionRepository positions) {
        this.jdbc = jdbc;
        this.formations = formations;
        this.formationVersions = formationVersions;
        this.positions = positions;
    }

    @Test
    void formationEntitiesCanBePersistedAndReadBackWithoutDomainPersistenceAnnotations() {
        String suffix = UUID.randomUUID().toString();
        Long choirId = null;
        Long formationId = null;
        Long versionId = null;
        Long positionId = null;

        LocalDateTime formationCreatedAt = LocalDateTime.now().minusMinutes(3);
        LocalDateTime versionCreatedAt = LocalDateTime.now().minusMinutes(2);
        LocalDateTime publishedAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime positionCreatedAt = LocalDateTime.now();

        try {
            jdbc.update(
                    "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                    "Formation Test Director " + suffix,
                    suffix + "@formation-test.example");

            Long directorId = jdbc.queryForObject(
                    "SELECT id FROM directors WHERE email = ?",
                    Long.class,
                    suffix + "@formation-test.example");

            jdbc.update(
                    "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                    directorId,
                    "Formation Test Choir " + suffix);

            choirId = jdbc.queryForObject(
                    "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                    Long.class,
                    directorId,
                    "Formation Test Choir " + suffix);

            FormationEntity formation = new FormationEntity();
            formation.setChoirId(choirId);
            formation.setName("Round Trip Formation " + suffix);
            formation.setDescription("Persistence round trip");
            formation.setCreatedAt(formationCreatedAt);

            FormationEntity savedFormation = formations.save(formation).block();

            assertThat(savedFormation).isNotNull();
            assertThat(savedFormation.getId()).isNotNull();

            formationId = savedFormation.getId();

            FormationEntity loadedFormation =
                    formations.findById(formationId).block();

            assertThat(loadedFormation).isNotNull();
            assertThat(loadedFormation.getChoirId()).isEqualTo(choirId);
            assertThat(loadedFormation.getName())
                    .isEqualTo("Round Trip Formation " + suffix);
            assertThat(loadedFormation.getDescription())
                    .isEqualTo("Persistence round trip");
            assertThat(loadedFormation.getCreatedAt())
                    .isEqualTo(formationCreatedAt);

            FormationVersionEntity version = new FormationVersionEntity();
            version.setFormationId(formationId);
            version.setVersionNumber(7);
            version.setStatus("PUBLISHED");
            version.setNotes("Published round trip");
            version.setCreatedAt(versionCreatedAt);
            version.setPublishedAt(publishedAt);

            FormationVersionEntity savedVersion =
                    formationVersions.save(version).block();

            assertThat(savedVersion).isNotNull();
            assertThat(savedVersion.getId()).isNotNull();

            versionId = savedVersion.getId();

            FormationVersionEntity loadedVersion =
                    formationVersions.findByFormationIdAndVersionNumber(
                            formationId,
                            7).block();

            assertThat(loadedVersion).isNotNull();
            assertThat(loadedVersion.getFormationId())
                    .isEqualTo(formationId);
            assertThat(loadedVersion.getVersionNumber()).isEqualTo(7);
            assertThat(loadedVersion.getStatus()).isEqualTo("PUBLISHED");
            assertThat(loadedVersion.getNotes())
                    .isEqualTo("Published round trip");
            assertThat(loadedVersion.getCreatedAt())
                    .isEqualTo(versionCreatedAt);
            assertThat(loadedVersion.getPublishedAt())
                    .isEqualTo(publishedAt);

            FormationPositionEntity position = new FormationPositionEntity();
            position.setFormationVersionId(versionId);
            position.setSingerId(null);
            position.setSingerNameSnapshot("Snapshot Singer");
            position.setVoicePartSnapshot("SOPRANO");
            position.setVoiceSectionIdSnapshot(12L);
            position.setVoiceSectionNameSnapshot("First Soprano");
            position.setDivisionNumberSnapshot(2);
            position.setPositionLabel("Center");
            position.setXCoordinate(java.math.BigDecimal.valueOf(12.50));
            position.setYCoordinate(java.math.BigDecimal.valueOf(34.75));
            position.setCreatedAt(positionCreatedAt);

            FormationPositionEntity savedPosition =
                    positions.save(position).block();

            assertThat(savedPosition).isNotNull();
            assertThat(savedPosition.getId()).isNotNull();

            positionId = savedPosition.getId();

            FormationPositionEntity loadedPosition =
                    positions.findById(positionId).block();

            assertThat(loadedPosition).isNotNull();
            assertThat(loadedPosition.getFormationVersionId())
                    .isEqualTo(versionId);
            assertThat(loadedPosition.getSingerId()).isNull();
            assertThat(loadedPosition.getSingerNameSnapshot())
                    .isEqualTo("Snapshot Singer");
            assertThat(loadedPosition.getVoicePartSnapshot())
                    .isEqualTo("SOPRANO");
            assertThat(loadedPosition.getVoiceSectionIdSnapshot())
                    .isEqualTo(12L);
            assertThat(loadedPosition.getVoiceSectionNameSnapshot())
                    .isEqualTo("First Soprano");
            assertThat(loadedPosition.getDivisionNumberSnapshot())
                    .isEqualTo(2);
            assertThat(loadedPosition.getPositionLabel())
                    .isEqualTo("Center");
            assertThat(loadedPosition.getXCoordinate())
                    .isEqualByComparingTo("12.50");
            assertThat(loadedPosition.getYCoordinate())
                    .isEqualByComparingTo("34.75");
            assertThat(loadedPosition.getCreatedAt())
                    .isEqualTo(positionCreatedAt);

        } finally {
            if (positionId != null) {
                jdbc.update(
                        "DELETE FROM formation_positions WHERE id = ?",
                        positionId);
            }
            if (versionId != null) {
                jdbc.update(
                        "DELETE FROM formation_versions WHERE id = ?",
                        versionId);
            }
            if (formationId != null) {
                jdbc.update(
                        "DELETE FROM formations WHERE id = ?",
                        formationId);
            }
            if (choirId != null) {
                jdbc.update(
                        "DELETE FROM choirs WHERE id = ?",
                        choirId);
            }
            jdbc.update(
                    "DELETE FROM directors WHERE email = ?",
                    suffix + "@formation-test.example");
        }
    }
}
