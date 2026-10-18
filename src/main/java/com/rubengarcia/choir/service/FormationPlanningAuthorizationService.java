package com.rubengarcia.choir.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface FormationPlanningAuthorizationService {

    Flux<FormationPlanningService.PlanningPosition> positionsForFormationVersion(
            String email, long choirId, long formationVersionId);

    Flux<FormationPlanningService.VocalAssignment> vocalAssignmentsForPlanning(
            String email, long choirId, long concertId, long repertoireItemId);

    Flux<FormationPlanningService.TransitionPlanningPosition>
            positionsForTransitionVersion(
                    String email, long choirId, long transitionVersionId);

    Mono<Void> updateDraftPosition(
            String email,
            long choirId,
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate);
}
