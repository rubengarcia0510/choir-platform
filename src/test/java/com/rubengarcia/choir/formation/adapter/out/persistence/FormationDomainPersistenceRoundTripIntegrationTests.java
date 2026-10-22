package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationDomainPersistenceRoundTripIntegrationTests {

    private final JdbcTemplate jdbc;
    private final FormationRepository formations;
    private final FormationVersionRepository versions;
    private final FormationPositionRepository positions;

    FormationDomainPersistenceRoundTripIntegrationTests(
            JdbcTemplate jdbc,
            FormationRepository formations,
            FormationVersionRepository versions,
            FormationPositionRepository positions) {
        this.jdbc = jdbc;
        this.formations = formations;
        this.versions = versions;
        this.positions = positions;
    }

    @Test
    void domainModelsSurviveDatabaseRoundTripThroughExplicitMapper() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@domain-round-trip.example";
        Long choirId = null;
        Long formationId = null;
        Long versionId = null;
        Long positionId = null;

        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(3).withNano(0);
        LocalDateTime publishedAt = LocalDateTime.now().minusMinutes(1).withNano(0);

        try {
            jdbc.update(
                    "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                    "Round Trip Director " + suffix, email);

            Long directorId = jdbc.queryForObject(
                    "SELECT id FROM directors WHERE email = ?",
                    Long.class, email);

            jdbc.update(
                    "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                    directorId, "Round Trip Choir " + suffix);

            choirId = jdbc.queryForObject(
                    "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                    Long.class, directorId, "Round Trip Choir " + suffix);

            Formation formation = new Formation();
            formation.setChoirId(choirId);
            formation.setName("Domain Formation " + suffix);
            formation.setDescription("Domain to database to domain");
            formation.setCreatedAt(createdAt);

            FormationEntity savedFormation = formations.save(
                    FormationPersistenceMapper.toEntity(formation)).block();

            assertThat(savedFormation).isNotNull();
            assertThat(savedFormation.getId()).isNotNull();
            formationId = savedFormation.getId();

            Formation loadedFormation = FormationPersistenceMapper.toDomain(
                    formations.findById(formationId).block());

            assertThat(loadedFormation).isNotNull();
            assertThat(loadedFormation.getId()).isEqualTo(formationId);
            assertThat(loadedFormation.getChoirId()).isEqualTo(choirId);
            assertThat(loadedFormation.getName()).isEqualTo("Domain Formation " + suffix);
            assertThat(loadedFormation.getDescription())
                    .isEqualTo("Domain to database to domain");
            assertThat(loadedFormation.getCreatedAt()).isEqualTo(createdAt);

            FormationVersion version = new FormationVersion();
            version.setFormationId(formationId);
            version.setVersionNumber(7);
            version.setStatus("PUBLISHED");
            version.setNotes("Published domain version");
            version.setCreatedAt(createdAt);
            version.setPublishedAt(publishedAt);

            FormationVersionEntity savedVersion = versions.save(
                    FormationPersistenceMapper.toEntity(version)).block();

            assertThat(savedVersion).isNotNull();
            assertThat(savedVersion.getId()).isNotNull();
            versionId = savedVersion.getId();

            Long loadedFormationId = loadedFormation.getId();
            FormationVersion roundTrippedVersion = FormationPersistenceMapper.toDomain(
                    versions.findByFormationIdAndVersionNumber(formationId, 7).block());

            assertThat(roundTrippedVersion).isNotNull();
            assertThat(roundTrippedVersion.getId()).isEqualTo(versionId);
            assertThat(roundTrippedVersion.getFormationId())
                    .isEqualTo(loadedFormationId);
            assertThat(roundTrippedVersion.getVersionNumber()).isEqualTo(7);
            assertThat(roundTrippedVersion.getStatus()).isEqualTo("PUBLISHED");
            assertThat(roundTrippedVersion.getNotes()).isEqualTo("Published domain version");
            assertThat(roundTrippedVersion.getCreatedAt()).isEqualTo(createdAt);
            assertThat(roundTrippedVersion.getPublishedAt()).isEqualTo(publishedAt);

            FormationPosition position = new FormationPosition();
            position.setFormationVersionId(versionId);
            position.setSingerId(null);
            position.setSingerNameSnapshot("Historical Singer");
            position.setVoicePartSnapshot("SOPRANO");
            position.setVoiceSectionIdSnapshot(12L);
            position.setVoiceSectionNameSnapshot("First Soprano");
            position.setDivisionNumberSnapshot(2);
            position.setPositionLabel("Center");
            position.setXCoordinate(new BigDecimal("12.50"));
            position.setYCoordinate(new BigDecimal("34.75"));
            position.setCreatedAt(createdAt);

            FormationPositionEntity savedPosition = positions.save(
                    FormationPersistenceMapper.toEntity(position)).block();

            assertThat(savedPosition).isNotNull();
            assertThat(savedPosition.getId()).isNotNull();
            positionId = savedPosition.getId();

            FormationPosition roundTrippedPosition = FormationPersistenceMapper.toDomain(
                    positions.findById(positionId).block());

            assertThat(roundTrippedPosition).isNotNull();
            assertThat(roundTrippedPosition.getId()).isEqualTo(positionId);
            assertThat(roundTrippedPosition.getFormationVersionId()).isEqualTo(versionId);
            assertThat(roundTrippedPosition.getSingerId()).isNull();
            assertThat(roundTrippedPosition.getSingerNameSnapshot())
                    .isEqualTo("Historical Singer");
            assertThat(roundTrippedPosition.getVoicePartSnapshot()).isEqualTo("SOPRANO");
            assertThat(roundTrippedPosition.getVoiceSectionIdSnapshot()).isEqualTo(12L);
            assertThat(roundTrippedPosition.getVoiceSectionNameSnapshot())
                    .isEqualTo("First Soprano");
            assertThat(roundTrippedPosition.getDivisionNumberSnapshot()).isEqualTo(2);
            assertThat(roundTrippedPosition.getPositionLabel()).isEqualTo("Center");
            assertThat(roundTrippedPosition.getXCoordinate())
                    .isEqualByComparingTo("12.50");
            assertThat(roundTrippedPosition.getYCoordinate())
                    .isEqualByComparingTo("34.75");
            assertThat(roundTrippedPosition.getCreatedAt()).isEqualTo(createdAt);
        } finally {
            if (positionId != null) {
                jdbc.update("DELETE FROM formation_positions WHERE id = ?", positionId);
            }
            if (versionId != null) {
                jdbc.update("DELETE FROM formation_versions WHERE id = ?", versionId);
            }
            if (formationId != null) {
                jdbc.update("DELETE FROM formations WHERE id = ?", formationId);
            }
            if (choirId != null) {
                jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
            }
            jdbc.update("DELETE FROM directors WHERE email = ?", email);
        }
    }
}
