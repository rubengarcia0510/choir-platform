package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.application.usecase.FormationPlanningService.PlanningPosition;
import com.rubengarcia.choir.formation.application.usecase.FormationPlanningService.VocalAssignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Puerto de salida para las consultas y modificaciones de persistencia
 * utilizadas por la planificación de formaciones.
 */
public interface FormationPlanningPersistencePort {

    /**
     * Recupera las posiciones con sus datos históricos de una versión.
     */
    Flux<PlanningPosition> positionsForFormationVersion(
            long formationVersionId);

    /**
     * Recupera las asignaciones vocales publicadas de una obra
     * dentro de un concierto.
     */
    Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId,
            long repertoireItemId);

    /**
     * Actualiza las coordenadas y la etiqueta de una posición
     * únicamente cuando su versión continúa en estado DRAFT.
     */
    Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate);
}
