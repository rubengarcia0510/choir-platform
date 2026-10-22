package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Component
public class FormationDuplicationPersistenceAdapter
        implements FormationDuplicationPort {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";

    private final FormationRepository formationRepository;
    private final FormationVersionRepository versionRepository;
    private final FormationPositionRepository positionRepository;

    public FormationDuplicationPersistenceAdapter(
            FormationRepository formationRepository,
            FormationVersionRepository versionRepository,
            FormationPositionRepository positionRepository) {
        this.formationRepository = formationRepository;
        this.versionRepository = versionRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationDuplicationPort.CreatedFormation> duplicate(
            long choirId,
            long concertId,
            long repertoireItemId,
            long sourceFormationId,
            Long sourceVersionId,
            String name,
            String description) {

        return formationRepository
                .findByChoirIdAndConcertIdAndRepertoireItemId(
                        choirId, concertId, repertoireItemId)
                .map(FormationPersistenceMapper::toDomain)
                .filter(formation -> formation.getId().equals(sourceFormationId))
                .next()
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "La formación de origen no pertenece a esta inclusión de repertorio")))
                .flatMap(sourceFormation ->
                        versionRepository.findByFormationId(sourceFormationId)
                                .map(FormationPersistenceMapper::toDomain)
                                .collectList()
                                .flatMap(versions -> selectSourceVersion(
                                        versions, sourceVersionId))
                                .flatMap(sourceVersion ->
                                        copyFormation(
                                                sourceVersion,
                                                choirId,
                                                concertId,
                                                repertoireItemId,
                                                name,
                                                description)));
    }

    private Mono<FormationVersion> selectSourceVersion(
            List<FormationVersion> versions,
            Long sourceVersionId) {

        if (sourceVersionId != null) {
            return versions.stream()
                    .filter(version -> sourceVersionId.equals(version.getId()))
                    .filter(version -> PUBLISHED.equals(version.getStatus()))
                    .findFirst()
                    .map(Mono::just)
                    .orElseGet(() -> Mono.error(new IllegalArgumentException(
                            "La versión indicada no existe o no está publicada")));
        }

        return versions.stream()
                .filter(version -> DRAFT.equals(version.getStatus()))
                .max(Comparator.comparingInt(FormationVersion::getVersionNumber))
                .map(Mono::just)
                .orElseGet(() -> versions.stream()
                        .filter(version -> PUBLISHED.equals(version.getStatus()))
                        .max(Comparator.comparingInt(
                                FormationVersion::getVersionNumber))
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new IllegalStateException(
                                "La formación no tiene una versión duplicable"))));
    }

    private Mono<FormationDuplicationPort.CreatedFormation> copyFormation(
            FormationVersion sourceVersion,
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description) {

        return positionRepository
                .findByFormationVersionId(sourceVersion.getId())
                .map(FormationPersistenceMapper::toDomain)
                .collectList()
                .flatMap(sourcePositions -> {
                    Formation duplicate = new Formation();
                    duplicate.setChoirId(choirId);
                    duplicate.setConcertId(concertId);
                    duplicate.setRepertoireItemId(repertoireItemId);
                    duplicate.setName(name);
                    duplicate.setDescription(description);

                    return formationRepository.save(
                                    FormationPersistenceMapper.toEntity(duplicate))
                            .map(FormationPersistenceMapper::toDomain)
                            .flatMap(savedFormation -> createCopiedDraft(
                                    savedFormation, sourcePositions));
                });
    }

    private Mono<FormationDuplicationPort.CreatedFormation> createCopiedDraft(
            Formation duplicate,
            List<FormationPosition> sourcePositions) {

        FormationVersion draft = new FormationVersion();
        draft.setFormationId(duplicate.getId());
        draft.setVersionNumber(1);
        draft.setStatus(DRAFT);

        return versionRepository.save(FormationPersistenceMapper.toEntity(draft))
                .map(FormationPersistenceMapper::toDomain)
                .flatMap(savedDraft -> {
                    FormationDuplicationPort.CreatedFormation result =
                            new FormationDuplicationPort.CreatedFormation(
                                    duplicate, savedDraft);

                    if (sourcePositions.isEmpty()) {
                        return Mono.just(result);
                    }

                    List<FormationPosition> copiedPositions = sourcePositions.stream()
                            .map(source -> copyPosition(source, savedDraft.getId()))
                            .toList();

                    return positionRepository.saveAll(
                                    copiedPositions.stream()
                                            .map(FormationPersistenceMapper::toEntity)
                                            .toList())
                            .then(Mono.just(result));
                });
    }

    private FormationPosition copyPosition(
            FormationPosition source,
            Long targetVersionId) {
        FormationPosition copy = new FormationPosition();
        copy.setFormationVersionId(targetVersionId);
        copy.setSingerId(source.getSingerId());
        copy.setSingerNameSnapshot(source.getSingerNameSnapshot());
        copy.setVoicePartSnapshot(source.getVoicePartSnapshot());
        copy.setVoiceSectionIdSnapshot(source.getVoiceSectionIdSnapshot());
        copy.setVoiceSectionNameSnapshot(source.getVoiceSectionNameSnapshot());
        copy.setDivisionNumberSnapshot(source.getDivisionNumberSnapshot());
        copy.setPositionLabel(source.getPositionLabel());
        copy.setXCoordinate(source.getXCoordinate());
        copy.setYCoordinate(source.getYCoordinate());
        return copy;
    }
}
