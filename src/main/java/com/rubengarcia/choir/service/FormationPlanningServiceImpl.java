package com.rubengarcia.choir.service;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

class FormationPlanningServiceImpl implements FormationPlanningService {

    private final DatabaseClient database;

    public FormationPlanningServiceImpl(DatabaseClient database) {
        this.database = database;
    }


    /**
     * Datos históricos de una versión concreta.
     * Las divisiones provienen del snapshot, no de los datos actuales del cantante.
     * Para una transición, consultar las versiones de origen y destino por separado.
     */
    public Flux<PlanningPosition> positionsForFormationVersion(
            long formationVersionId) {
        return database.sql("""
                SELECT
                    fp.formation_version_id,
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
                JOIN formation_versions fv
                  ON fv.id = fp.formation_version_id
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

    /**
     * Asignaciones vocales históricas de una obra en un concierto publicado.
     */
    public Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId,
            long repertoireItemId) {
        return database.sql("""
                SELECT
                    s.concert_id,
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

    /**
     * Datos vocales históricos de las formaciones de origen y destino
     * de una versión de transición.
     */
    public Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId) {
        return database.sql("""
                SELECT
                    tv.id AS transition_version_id,
                    'FROM' AS stage,
                    fp.formation_version_id,
                    fp.singer_id,
                    fp.singer_name_snapshot,
                    fp.voice_part_snapshot,
                    fp.voice_section_id_snapshot,
                    fp.voice_section_name_snapshot,
                    fp.division_number_snapshot,
                    fp.position_label,
                    fp.x_coordinate,
                    fp.y_coordinate
                FROM transition_versions tv
                JOIN formation_positions fp
                  ON fp.formation_version_id = tv.from_formation_version_id
                WHERE tv.id = :transitionVersionId

                UNION ALL

                SELECT
                    tv.id AS transition_version_id,
                    'TO' AS stage,
                    fp.formation_version_id,
                    fp.singer_id,
                    fp.singer_name_snapshot,
                    fp.voice_part_snapshot,
                    fp.voice_section_id_snapshot,
                    fp.voice_section_name_snapshot,
                    fp.division_number_snapshot,
                    fp.position_label,
                    fp.x_coordinate,
                    fp.y_coordinate
                FROM transition_versions tv
                JOIN formation_positions fp
                  ON fp.formation_version_id = tv.to_formation_version_id
                WHERE tv.id = :transitionVersionId

                ORDER BY stage, formation_version_id, singer_name_snapshot
                """)
                .bind("transitionVersionId", transitionVersionId)
                .map((row, metadata) -> new TransitionPlanningPosition(
                        row.get("transition_version_id", Long.class),
                        row.get("stage", String.class),
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

    /**
     * Actualiza una posición solo si su versión todavía es un borrador.
     * Las columnas vocales históricas no se modifican.
     */
    public Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {
        return database.sql("""
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
                """)
                .bind("label", positionLabel)
                .bind("x", xCoordinate)
                .bind("y", yCoordinate)
                .bind("positionId", positionId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> {
                    if (updated > 0) {
                        return Mono.<Void>empty();
                    }

                    return database.sql("""
                            SELECT fv.status
                            FROM formation_positions fp
                            JOIN formation_versions fv
                              ON fv.id = fp.formation_version_id
                            WHERE fp.id = :positionId
                            """)
                            .bind("positionId", positionId)
                            .map((row, metadata) ->
                                    row.get("status", String.class))
                            .one()
                            .flatMap(status -> {
                                if ("PUBLISHED".equals(status)) {
                                    return Mono.<Void>error(
                                            new ResponseStatusException(
                                                    HttpStatus.CONFLICT,
                                                    "No se puede modificar una formación publicada"));
                                }
                                return Mono.<Void>error(
                                        new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "La posición no se pudo actualizar"));
                            })
                            .switchIfEmpty(Mono.<Void>error(
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "No existe la posición de formación")));
                });
    }
}
