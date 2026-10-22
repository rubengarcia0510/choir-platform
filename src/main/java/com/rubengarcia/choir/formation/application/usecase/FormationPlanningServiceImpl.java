package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.model.VocalAssignment;

import com.rubengarcia.choir.formation.application.model.PlanningPosition;

import com.rubengarcia.choir.formation.application.port.out.FormationPlanningPersistencePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
class FormationPlanningServiceImpl implements FormationPlanningService {

    private final FormationPlanningPersistencePort persistencePort;

    public FormationPlanningServiceImpl(
            FormationPlanningPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    /**
     * Obtiene los datos históricos de una versión concreta.
     * Para una transición, consultar las versiones de origen y destino
     * mediante el servicio específico de planificación de transiciones.
     */
    @Override
    public Flux<PlanningPosition> positionsForFormationVersion(
            long formationVersionId) {
        return persistencePort.positionsForFormationVersion(formationVersionId);
    }

    /**
     * Obtiene las asignaciones vocales históricas de una obra
     * en un concierto publicado.
     */
    @Override
    public Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId,
            long repertoireItemId) {
        return persistencePort.vocalAssignmentsForPlanning(
                concertId, repertoireItemId);
    }

    /**
     * Delega la actualización de una posición al puerto de persistencia.
     * La persistencia debe impedir modificaciones de versiones publicadas
     * y preservar los datos vocales históricos.
     */
    @Override
    public Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {
        return persistencePort.updateDraftPosition(
                positionId, positionLabel, xCoordinate, yCoordinate);
    }
}
