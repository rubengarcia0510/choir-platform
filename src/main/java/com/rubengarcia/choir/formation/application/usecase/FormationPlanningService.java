package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.model.PlanningPosition;
import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface FormationPlanningService {

    Flux<PlanningPosition> positionsForFormationVersion(
            long formationVersionId);

    Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId,
            long repertoireItemId);

    Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate);
}
