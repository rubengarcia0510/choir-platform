package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationPlanningPersistencePort;
import com.rubengarcia.choir.formation.application.model.PlanningPosition;
import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import com.rubengarcia.choir.formation.application.usecase.FormationPositionNotFoundException;
import com.rubengarcia.choir.formation.application.usecase.FormationPositionUpdateException;
import com.rubengarcia.choir.formation.application.usecase.PublishedFormationModificationException;
import java.math.BigDecimal;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationPlanningPersistenceAdapter
        implements FormationPlanningPersistencePort {

    private final DatabaseClient database;

    public FormationPlanningPersistenceAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Flux<PlanningPosition> positionsForFormationVersion(long formationVersionId) {
        return database.sql("""
                SELECT fp.formation_version_id,
                       fp.singer_id,
                       fp.singer_name_snapshot,
                       fp.voice_part_snapshot,
                       fp.voice_section_id_snapshot,
                       fp.voice_section_name_snapshot,
                       fp.division_number_snapshot,
                       fp.position_label,
                       fp.x_coordinate,
                       fp.y_coordinate
                FROM formation_positions fp
                JOIN formation_versions fv ON fv.id = fp.formation_version_id
                WHERE fv.id = :versionId
                ORDER BY fp.id
                """)
                .bind("versionId", formationVersionId)
                .map((row, metadata) -> new PlanningPosition(
                        row.get("formation_version_id", Long.class),
                        row.get("singer_id", Long.class),
                        row.get("singer_name_snapshot", String.class),
                        row.get("voice_part_snapshot", String.class),
                        row.get("voice_section_id_snapshot", Long.class),
                        row.get("voice_section_name_snapshot", String.class),
                        row.get("division_number_snapshot", Integer.class),
                        row.get("position_label", String.class),
                        row.get("x_coordinate", BigDecimal.class),
                        row.get("y_coordinate", BigDecimal.class)))
                .all();
    }

    @Override
    public Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId, long repertoireItemId) {
        return database.sql("""
                SELECT s.concert_id,
                       s.repertoire_item_id,
                       s.source_singer_id,
                       s.singer_name_snapshot,
                       s.voice_part_snapshot,
                       s.voice_section_id_snapshot,
                       s.voice_section_name_snapshot,
                       s.division_number_snapshot
                FROM concert_vocal_assignment_snapshots s
                JOIN concerts c ON c.id = s.concert_id
                WHERE s.concert_id = :concertId
                  AND s.repertoire_item_id = :repertoireItemId
                  AND c.status = 'PUBLISHED'
                ORDER BY s.voice_section_name_snapshot,
                         s.division_number_snapshot,
                         s.singer_name_snapshot
                """)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> new VocalAssignment(
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("source_singer_id", Long.class),
                        row.get("singer_name_snapshot", String.class),
                        row.get("voice_part_snapshot", String.class),
                        row.get("voice_section_id_snapshot", Long.class),
                        row.get("voice_section_name_snapshot", String.class),
                        row.get("division_number_snapshot", Integer.class)))
                .all();
    }

    @Override
    public Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {

        DatabaseClient.GenericExecuteSpec update = database.sql("""
                UPDATE formation_positions
                SET position_label = :label,
                    x_coordinate = :x,
                    y_coordinate = :y
                WHERE id = :positionId
                  AND EXISTS (
                      SELECT 1
                      FROM formation_versions fv
                      WHERE fv.id = formation_positions.formation_version_id
                        AND fv.status = 'DRAFT'
                  )
                """);

        update = positionLabel == null
                ? update.bindNull("label", String.class)
                : update.bind("label", positionLabel);

        return update
                .bind("x", xCoordinate)
                .bind("y", yCoordinate)
                .bind("positionId", positionId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> {
                    if (updated > 0) {
                        return Mono.<Void>empty();
                    }
                    return resolveUpdateFailure(positionId);
                });
    }

    private Mono<Void> resolveUpdateFailure(long positionId) {
        return database.sql("""
                SELECT fv.status
                FROM formation_positions fp
                JOIN formation_versions fv ON fv.id = fp.formation_version_id
                WHERE fp.id = :positionId
                """)
                .bind("positionId", positionId)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .<Void>flatMap(status -> {
                    if ("PUBLISHED".equals(status)) {
                        return Mono.error(
                                new PublishedFormationModificationException());
                    }
                    return Mono.error(new FormationPositionUpdateException());
                })
                .switchIfEmpty(Mono.error(
                        new FormationPositionNotFoundException(positionId)));
    }
}
