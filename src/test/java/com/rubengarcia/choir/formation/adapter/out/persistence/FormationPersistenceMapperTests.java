package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FormationPersistenceMapperTests {

@Test
void mapsFormationToEntityAndBack() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 10, 21, 10, 30);

    Formation source = new Formation();
    source.setId(11L);
    source.setChoirId(12L);
    source.setConcertId(13L);
    source.setRepertoireItemId(14L);
    source.setName("Formación");
    source.setDescription("Descripción");
    source.setCreatedAt(createdAt);

    FormationEntity entity = FormationPersistenceMapper.toEntity(source);
    Formation result = FormationPersistenceMapper.toDomain(entity);

    assertThat(result.getId()).isEqualTo(11L);
    assertThat(result.getChoirId()).isEqualTo(12L);
    assertThat(result.getConcertId()).isEqualTo(13L);
    assertThat(result.getRepertoireItemId()).isEqualTo(14L);
    assertThat(result.getName()).isEqualTo("Formación");
    assertThat(result.getDescription()).isEqualTo("Descripción");
    assertThat(result.getCreatedAt()).isEqualTo(createdAt);
}

@Test
void mapsFormationVersionToEntityAndBack() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 10, 21, 11, 0);
    LocalDateTime publishedAt = LocalDateTime.of(2026, 10, 21, 12, 0);

    FormationVersion source = new FormationVersion();
    source.setId(21L);
    source.setFormationId(22L);
    source.setVersionNumber(7);
    source.setStatus("PUBLISHED");
    source.setNotes("Versión publicada");
    source.setCreatedAt(createdAt);
    source.setPublishedAt(publishedAt);

    FormationVersionEntity entity = FormationPersistenceMapper.toEntity(source);
    FormationVersion result = FormationPersistenceMapper.toDomain(entity);

    assertThat(result.getId()).isEqualTo(21L);
    assertThat(result.getFormationId()).isEqualTo(22L);
    assertThat(result.getVersionNumber()).isEqualTo(7);
    assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    assertThat(result.getNotes()).isEqualTo("Versión publicada");
    assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    assertThat(result.getPublishedAt()).isEqualTo(publishedAt);
}

@Test
void mapsFormationPositionAndPreservesVocalSnapshots() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 10, 21, 13, 0);

    FormationPosition source = new FormationPosition();
    source.setId(31L);
    source.setFormationVersionId(32L);
    source.setSingerId(33L);
    source.setSingerNameSnapshot("Cantante histórico");
    source.setVoicePartSnapshot("SOPRANO");
    source.setVoiceSectionIdSnapshot(34L);
    source.setVoiceSectionNameSnapshot("Soprano primera");
    source.setDivisionNumberSnapshot(2);
    source.setPositionLabel("Centro");
    source.setXCoordinate(new BigDecimal("12.50"));
    source.setYCoordinate(new BigDecimal("34.75"));
    source.setCreatedAt(createdAt);

    FormationPositionEntity entity = FormationPersistenceMapper.toEntity(source);
    FormationPosition result = FormationPersistenceMapper.toDomain(entity);

    assertThat(result.getId()).isEqualTo(31L);
    assertThat(result.getFormationVersionId()).isEqualTo(32L);
    assertThat(result.getSingerId()).isEqualTo(33L);
    assertThat(result.getSingerNameSnapshot()).isEqualTo("Cantante histórico");
    assertThat(result.getVoicePartSnapshot()).isEqualTo("SOPRANO");
    assertThat(result.getVoiceSectionIdSnapshot()).isEqualTo(34L);
    assertThat(result.getVoiceSectionNameSnapshot()).isEqualTo("Soprano primera");
    assertThat(result.getDivisionNumberSnapshot()).isEqualTo(2);
    assertThat(result.getPositionLabel()).isEqualTo("Centro");
    assertThat(result.getXCoordinate()).isEqualByComparingTo("12.50");
    assertThat(result.getYCoordinate()).isEqualByComparingTo("34.75");
    assertThat(result.getCreatedAt()).isEqualTo(createdAt);
}

@Test
void returnsNullWhenMappingNullValues() {
    assertThat(FormationPersistenceMapper.toEntity((Formation) null)).isNull();
    assertThat(FormationPersistenceMapper.toDomain((FormationEntity) null)).isNull();

    assertThat(FormationPersistenceMapper.toEntity((FormationVersion) null)).isNull();
    assertThat(FormationPersistenceMapper.toDomain((FormationVersionEntity) null)).isNull();

    assertThat(FormationPersistenceMapper.toEntity((FormationPosition) null)).isNull();
    assertThat(FormationPersistenceMapper.toDomain((FormationPositionEntity) null)).isNull();
}

}
