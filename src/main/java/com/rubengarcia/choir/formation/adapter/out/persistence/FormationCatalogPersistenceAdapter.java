package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationCatalogPersistenceAdapter implements FormationCatalogPort {

    private static final int GRID_COLUMNS = 9;
    private static final BigDecimal GRID_START = BigDecimal.TEN;
    private static final BigDecimal GRID_STEP = BigDecimal.TEN;
    private static final BigDecimal GRID_END = BigDecimal.valueOf(90);
    private static final BigDecimal GRID_CENTER = BigDecimal.valueOf(50);

    private final FormationRepository formationRepository;
    private final FormationVersionRepository formationVersionRepository;
    private final FormationPositionRepository formationPositionRepository;
    private final DatabaseClient databaseClient;

    public FormationCatalogPersistenceAdapter(
            FormationRepository formationRepository,
            FormationVersionRepository formationVersionRepository,
            FormationPositionRepository formationPositionRepository,
            DatabaseClient databaseClient) {
        this.formationRepository = formationRepository;
        this.formationVersionRepository = formationVersionRepository;
        this.formationPositionRepository = formationPositionRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<Formation> findForOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId) {
        return formationRepository
                .findByChoirIdAndConcertIdAndRepertoireItemId(
                        choirId, concertId, repertoireItemId);
    }

    @Override
    public Mono<DraftDetails> findDraftForOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return findForOccurrence(choirId, concertId, repertoireItemId)
                .filter(formation -> formation.getId().equals(formationId))
                .next()
                .flatMap(formation ->
                        formationVersionRepository
                                .findByFormationId(formation.getId())
                                .filter(version -> "DRAFT".equals(version.getStatus()))
                                .sort(Comparator.comparingInt(
                                        FormationVersion::getVersionNumber).reversed())
                                .next()
                                .flatMap(version ->
                                        formationPositionRepository
                                                .findByFormationVersionId(version.getId())
                                                .collectList()
                                                .map(positions -> new DraftDetails(
                                                        formation,
                                                        version,
                                                        List.copyOf(positions)))));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<CreatedFormation> createWithInitialDraft(
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description) {
        Formation formation = new Formation();
        formation.setChoirId(choirId);
        formation.setConcertId(concertId);
        formation.setRepertoireItemId(repertoireItemId);
        formation.setName(name);
        formation.setDescription(description);

        return formationRepository.save(formation)
                .flatMap(savedFormation -> createInitialDraft(
                        savedFormation, choirId, concertId, repertoireItemId));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<DraftDetails> replaceDraftPositions(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long versionId,
            List<PositionUpdate> updates) {
        return findForOccurrence(choirId, concertId, repertoireItemId)
                .filter(formation -> formation.getId().equals(formationId))
                .next()
                .flatMap(formation -> lockVersion(formationId, versionId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "La versión no pertenece a la formación")))
                        .flatMap(version -> {
                            if (!"DRAFT".equals(version.getStatus())) {
                                return Mono.error(new IllegalStateException(
                                        "La versión no es editable: "
                                                + version.getStatus()));
                            }

                            return validateAndBuildPositions(
                                    choirId,
                                    concertId,
                                    repertoireItemId,
                                    versionId,
                                    updates)
                                    .flatMap(positions -> replacePositions(
                                            formation, version, positions));
                        }));
    }

    private Mono<FormationVersion> lockVersion(
            long formationId,
            long versionId) {
        return databaseClient.sql("""
                        SELECT
                            id,
                            formation_id,
                            version_number,
                            status,
                            notes,
                            created_at,
                            published_at
                        FROM formation_versions
                        WHERE id = :versionId
                          AND formation_id = :formationId
                        FOR UPDATE
                        """)
                .bind("versionId", versionId)
                .bind("formationId", formationId)
                .map((row, metadata) -> {
                    FormationVersion version = new FormationVersion();
                    version.setId(row.get("id", Long.class));
                    version.setFormationId(row.get("formation_id", Long.class));
                    version.setVersionNumber(row.get("version_number", Integer.class));
                    version.setStatus(row.get("status", String.class));
                    version.setNotes(row.get("notes", String.class));
                    version.setCreatedAt(row.get("created_at", LocalDateTime.class));
                    version.setPublishedAt(row.get("published_at", LocalDateTime.class));
                    return version;
                })
                .one();
    }

    private Mono<List<FormationPosition>> validateAndBuildPositions(
            long choirId,
            long concertId,
            long repertoireItemId,
            long versionId,
            List<PositionUpdate> updates) {
        return Flux.fromIterable(updates)
                .concatMap(update -> findSingerSnapshot(
                                choirId,
                                concertId,
                                repertoireItemId,
                                update.singerId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "El cantante " + update.singerId()
                                        + " no existe, está inactivo o no pertenece al coro")))
                        .map(snapshot -> toFormationPosition(versionId, update, snapshot)))
                .collectList();
    }

    private Mono<InitialPosition> findSingerSnapshot(
            long choirId,
            long concertId,
            long repertoireItemId,
            long singerId) {
        return databaseClient.sql("""
                        SELECT
                            s.id AS singer_id,
                            s.display_name AS singer_name,
                            s.voice_part AS voice_part,
                            vs.id AS voice_section_id,
                            vs.name AS voice_section_name,
                            d.division_number AS division_number
                        FROM singers s
                        LEFT JOIN concert_vocal_assignments a
                            ON a.singer_id = s.id
                           AND a.choir_id = s.choir_id
                           AND a.concert_id = :concertId
                           AND a.repertoire_item_id = :repertoireItemId
                        LEFT JOIN vocal_plan_divisions d
                            ON d.id = a.vocal_plan_division_id
                           AND d.choir_id = a.choir_id
                        LEFT JOIN vocal_sections vs
                            ON vs.id = d.vocal_section_id
                           AND vs.choir_id = d.choir_id
                        WHERE s.id = :singerId
                          AND s.choir_id = :choirId
                          AND s.active = TRUE
                        """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .bind("singerId", singerId)
                .map((row, metadata) -> new InitialPosition(
                        row.get("singer_id", Long.class),
                        row.get("singer_name", String.class),
                        row.get("voice_part", String.class),
                        row.get("voice_section_id", Long.class),
                        row.get("voice_section_name", String.class),
                        row.get("division_number", Integer.class)))
                .one();
    }

    private FormationPosition toFormationPosition(
            Long versionId,
            PositionUpdate update,
            InitialPosition snapshot) {
        FormationPosition position = new FormationPosition();
        position.setFormationVersionId(versionId);
        position.setSingerId(snapshot.singerId());
        position.setSingerNameSnapshot(snapshot.singerName());
        position.setVoicePartSnapshot(snapshot.voicePart());
        position.setVoiceSectionIdSnapshot(snapshot.voiceSectionId());
        position.setVoiceSectionNameSnapshot(snapshot.voiceSectionName());
        position.setDivisionNumberSnapshot(snapshot.divisionNumber());
        position.setPositionLabel(update.positionLabel() == null
                ? null
                : update.positionLabel().trim());
        position.setXCoordinate(update.x());
        position.setYCoordinate(update.y());
        return position;
    }

    private Mono<DraftDetails> replacePositions(
            Formation formation,
            FormationVersion version,
            List<FormationPosition> positions) {
        Mono<Void> savePositions = positions.isEmpty()
                ? Mono.empty()
                : formationPositionRepository.saveAll(positions).then();

        return formationPositionRepository
                .deleteByFormationVersionId(version.getId())
                .then(savePositions)
                .then(formationPositionRepository
                        .findByFormationVersionId(version.getId())
                        .collectList())
                .map(savedPositions -> new DraftDetails(
                        formation, version, List.copyOf(savedPositions)));
    }

    private Mono<CreatedFormation> createInitialDraft(
            Formation formation,
            long choirId,
            long concertId,
            long repertoireItemId) {
        FormationVersion draft = new FormationVersion();
        draft.setFormationId(formation.getId());
        draft.setVersionNumber(1);
        draft.setStatus("DRAFT");

        return formationVersionRepository.save(draft)
                .flatMap(savedDraft -> {
                    CreatedFormation created = new CreatedFormation(formation, savedDraft);
                    return findInitialAssignments(choirId, concertId, repertoireItemId)
                            .collectList()
                            .flatMap(assignments -> {
                                if (assignments.isEmpty()) {
                                    return Mono.just(created);
                                }

                                List<FormationPosition> positions =
                                        createPositions(savedDraft.getId(), assignments);

                                return formationPositionRepository.saveAll(positions)
                                        .then(Mono.just(created));
                            });
                });
    }

    private Flux<InitialPosition> findInitialAssignments(
            long choirId,
            long concertId,
            long repertoireItemId) {
        return databaseClient.sql("""
                        SELECT
                            s.id AS singer_id,
                            s.display_name AS singer_name,
                            s.voice_part AS voice_part,
                            vs.id AS voice_section_id,
                            vs.name AS voice_section_name,
                            d.division_number AS division_number
                        FROM concert_vocal_assignments a
                        JOIN singers s
                            ON s.id = a.singer_id
                           AND s.choir_id = a.choir_id
                           AND s.active = TRUE
                        JOIN vocal_plan_divisions d
                            ON d.id = a.vocal_plan_division_id
                           AND d.choir_id = a.choir_id
                        JOIN vocal_sections vs
                            ON vs.id = d.vocal_section_id
                           AND vs.choir_id = a.choir_id
                        WHERE a.choir_id = :choirId
                          AND a.concert_id = :concertId
                          AND a.repertoire_item_id = :repertoireItemId
                        ORDER BY
                            vs.sort_order,
                            d.division_number,
                            s.display_name,
                            s.id
                        """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> new InitialPosition(
                        row.get("singer_id", Long.class),
                        row.get("singer_name", String.class),
                        row.get("voice_part", String.class),
                        row.get("voice_section_id", Long.class),
                        row.get("voice_section_name", String.class),
                        row.get("division_number", Integer.class)))
                .all();
    }

    private List<FormationPosition> createPositions(
            Long formationVersionId,
            List<InitialPosition> assignments) {
        int rowCount = (assignments.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        List<FormationPosition> positions = new ArrayList<>(assignments.size());

        for (int index = 0; index < assignments.size(); index++) {
            InitialPosition assignment = assignments.get(index);
            FormationPosition position = new FormationPosition();
            position.setFormationVersionId(formationVersionId);
            position.setSingerId(assignment.singerId());
            position.setSingerNameSnapshot(assignment.singerName());
            position.setVoicePartSnapshot(assignment.voicePart());
            position.setVoiceSectionIdSnapshot(assignment.voiceSectionId());
            position.setVoiceSectionNameSnapshot(assignment.voiceSectionName());
            position.setDivisionNumberSnapshot(assignment.divisionNumber());
            position.setXCoordinate(xCoordinate(index));
            position.setYCoordinate(yCoordinate(index, rowCount));
            positions.add(position);
        }

        return positions;
    }

    private BigDecimal xCoordinate(int index) {
        int column = index % GRID_COLUMNS;
        return GRID_START.add(GRID_STEP.multiply(BigDecimal.valueOf(column)));
    }

    private BigDecimal yCoordinate(int index, int rowCount) {
        if (rowCount == 1) {
            return GRID_CENTER;
        }

        int row = index / GRID_COLUMNS;
        BigDecimal availableHeight = GRID_END.subtract(GRID_START);

        return GRID_START.add(availableHeight.multiply(BigDecimal.valueOf(row))
                .divide(
                        BigDecimal.valueOf(rowCount - 1),
                        3,
                        java.math.RoundingMode.HALF_UP));
    }

    private record InitialPosition(
            Long singerId,
            String singerName,
            String voicePart,
            Long voiceSectionId,
            String voiceSectionName,
            Integer divisionNumber) {}
}
