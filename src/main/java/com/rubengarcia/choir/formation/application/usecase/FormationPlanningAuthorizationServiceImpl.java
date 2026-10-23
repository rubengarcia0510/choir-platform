package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.model.PlanningPosition;
import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import com.rubengarcia.choir.formation.application.port.out.FormationPlanningResourceAuthorizationPort;
import com.rubengarcia.choir.transition.application.usecase.TransitionPlanningService;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class FormationPlanningAuthorizationServiceImpl
        implements FormationPlanningAuthorizationService {

    private final FormationPlanningService planningService;
    private final TransitionPlanningService transitionPlanningService;
    private final FormationPlanningResourceAuthorizationPort authorizationPort;

    public FormationPlanningAuthorizationServiceImpl(
            FormationPlanningService planningService,
            TransitionPlanningService transitionPlanningService,
            FormationPlanningResourceAuthorizationPort authorizationPort) {
        this.planningService = planningService;
        this.transitionPlanningService = transitionPlanningService;
        this.authorizationPort = authorizationPort;
    }

    @Override
    public Flux<PlanningPosition> positionsForFormationVersion(
            String email, long choirId, long formationVersionId) {
        return authorizationPort
                .requireOwnedFormationVersion(email, choirId, formationVersionId)
                .thenMany(Flux.defer(() ->
                        planningService.positionsForFormationVersion(
                                formationVersionId)));
    }

    @Override
    public Flux<VocalAssignment> vocalAssignmentsForPlanning(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId) {
        return authorizationPort
                .requireOwnedConcertWork(
                        email, choirId, concertId, repertoireItemId)
                .thenMany(Flux.defer(() ->
                        planningService.vocalAssignmentsForPlanning(
                                concertId, repertoireItemId)));
    }

    @Override
    public Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            String email, long choirId, long transitionVersionId) {
        return authorizationPort
                .requireOwnedTransitionVersion(email, choirId, transitionVersionId)
                .thenMany(Flux.defer(() ->
                        transitionPlanningService.positionsForTransitionVersion(
                                transitionVersionId)));
    }

    @Override
    public Mono<Void> updateDraftPosition(
            String email,
            long choirId,
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {
        return authorizationPort
                .requireOwnedPosition(email, choirId, positionId)
                .then(Mono.defer(() -> planningService.updateDraftPosition(
                        positionId, positionLabel, xCoordinate, yCoordinate)));
    }
}
