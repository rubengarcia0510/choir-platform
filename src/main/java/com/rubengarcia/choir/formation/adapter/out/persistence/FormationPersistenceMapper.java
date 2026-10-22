package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;

public final class FormationPersistenceMapper {

    private FormationPersistenceMapper() {
    }

    public static Formation toDomain(FormationEntity entity) {
        if (entity == null) {
            return null;
        }

        Formation domain = new Formation();
        domain.setId(entity.getId());
        domain.setChoirId(entity.getChoirId());
        domain.setConcertId(entity.getConcertId());
        domain.setRepertoireItemId(entity.getRepertoireItemId());
        domain.setName(entity.getName());
        domain.setDescription(entity.getDescription());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public static FormationEntity toEntity(Formation domain) {
        if (domain == null) {
            return null;
        }

        FormationEntity entity = new FormationEntity();
        entity.setId(domain.getId());
        entity.setChoirId(domain.getChoirId());
        entity.setConcertId(domain.getConcertId());
        entity.setRepertoireItemId(domain.getRepertoireItemId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    public static FormationVersion toDomain(FormationVersionEntity entity) {
        if (entity == null) {
            return null;
        }

        FormationVersion domain = new FormationVersion();
        domain.setId(entity.getId());
        domain.setFormationId(entity.getFormationId());
        domain.setVersionNumber(entity.getVersionNumber());
        domain.setStatus(entity.getStatus());
        domain.setNotes(entity.getNotes());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setPublishedAt(entity.getPublishedAt());
        return domain;
    }

    public static FormationVersionEntity toEntity(FormationVersion domain) {
        if (domain == null) {
            return null;
        }

        FormationVersionEntity entity = new FormationVersionEntity();
        entity.setId(domain.getId());
        entity.setFormationId(domain.getFormationId());
        entity.setVersionNumber(domain.getVersionNumber());
        entity.setStatus(domain.getStatus());
        entity.setNotes(domain.getNotes());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setPublishedAt(domain.getPublishedAt());
        return entity;
    }

    public static FormationPosition toDomain(FormationPositionEntity entity) {
        if (entity == null) {
            return null;
        }

        FormationPosition domain = new FormationPosition();
        domain.setId(entity.getId());
        domain.setFormationVersionId(entity.getFormationVersionId());
        domain.setSingerId(entity.getSingerId());
        domain.setSingerNameSnapshot(entity.getSingerNameSnapshot());
        domain.setVoicePartSnapshot(entity.getVoicePartSnapshot());
        domain.setVoiceSectionIdSnapshot(entity.getVoiceSectionIdSnapshot());
        domain.setVoiceSectionNameSnapshot(entity.getVoiceSectionNameSnapshot());
        domain.setDivisionNumberSnapshot(entity.getDivisionNumberSnapshot());
        domain.setPositionLabel(entity.getPositionLabel());
        domain.setXCoordinate(entity.getXCoordinate());
        domain.setYCoordinate(entity.getYCoordinate());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public static FormationPositionEntity toEntity(FormationPosition domain) {
        if (domain == null) {
            return null;
        }

        FormationPositionEntity entity = new FormationPositionEntity();
        entity.setId(domain.getId());
        entity.setFormationVersionId(domain.getFormationVersionId());
        entity.setSingerId(domain.getSingerId());
        entity.setSingerNameSnapshot(domain.getSingerNameSnapshot());
        entity.setVoicePartSnapshot(domain.getVoicePartSnapshot());
        entity.setVoiceSectionIdSnapshot(domain.getVoiceSectionIdSnapshot());
        entity.setVoiceSectionNameSnapshot(domain.getVoiceSectionNameSnapshot());
        entity.setDivisionNumberSnapshot(domain.getDivisionNumberSnapshot());
        entity.setPositionLabel(domain.getPositionLabel());
        entity.setXCoordinate(domain.getXCoordinate());
        entity.setYCoordinate(domain.getYCoordinate());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
