package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.math.BigDecimal;
import java.util.ArrayList;
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
                        choirId,
                        concertId,
                        repertoireItemId);
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
                        savedFormation,
                        choirId,
                        concertId,
                        repertoireItemId));
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
                    CreatedFormation created =
                            new CreatedFormation(formation, savedDraft);

                    return findInitialAssignments(
                                    choirId,
                                    concertId,
                                    repertoireItemId)
                            .collectList()
                            .flatMap(assignments -> {
                                if (assignments.isEmpty()) {
                                    return Mono.just(created);
                                }

                                List<FormationPosition> positions =
                                        createPositions(
                                                savedDraft.getId(),
                                                assignments);

                                return formationPositionRepository
                                        .saveAll(positions)
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
        List<FormationPosition> positions =
                new ArrayList<>(assignments.size());

        for (int index = 0; index < assignments.size(); index++) {
            InitialPosition assignment = assignments.get(index);

            FormationPosition position = new FormationPosition();
            position.setFormationVersionId(formationVersionId);
            position.setSingerId(assignment.singerId());
            position.setSingerNameSnapshot(assignment.singerName());
            position.setVoicePartSnapshot(assignment.voicePart());
            position.setVoiceSectionIdSnapshot(assignment.voiceSectionId());
            position.setVoiceSectionNameSnapshot(
                    assignment.voiceSectionName());
            position.setDivisionNumberSnapshot(assignment.divisionNumber());
            position.setXCoordinate(xCoordinate(index));
            position.setYCoordinate(yCoordinate(index, rowCount));

            positions.add(position);
        }

        return positions;
    }

    private BigDecimal xCoordinate(int index) {
        int column = index % GRID_COLUMNS;

        return GRID_START.add(
                GRID_STEP.multiply(BigDecimal.valueOf(column)));
    }

    private BigDecimal yCoordinate(int index, int rowCount) {
        if (rowCount == 1) {
            return GRID_CENTER;
        }

        int row = index / GRID_COLUMNS;
        BigDecimal availableHeight = GRID_END.subtract(GRID_START);

        return GRID_START.add(
                availableHeight.multiply(BigDecimal.valueOf(row))
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
            Integer divisionNumber) {
    }
}
