package com.rubengarcia.choir.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class FormationPlanningAuthorizationServiceImpl
        implements FormationPlanningAuthorizationService {

    private final FormationPlanningDataRepository planningService;
    private final ResourceAuthorizationService authorizationService;

    public FormationPlanningAuthorizationServiceImpl(
            FormationPlanningDataRepository planningService,
            ResourceAuthorizationService authorizationService) {
        this.planningService = planningService;
        this.authorizationService = authorizationService;
    }

    @Override
    public Flux<FormationPlanningService.PlanningPosition>
            positionsForFormationVersion(
                    String email, long choirId, long formationVersionId) {
        return authorizationService
                .requireOwnedFormationVersion(email, choirId, formationVersionId)
                .thenMany(Flux.defer(() ->
                        planningService.positionsForFormationVersion(
                                formationVersionId)));
    }

    @Override
    public Flux<FormationPlanningService.VocalAssignment>
            vocalAssignmentsForPlanning(
                    String email, long choirId, long concertId,
                    long repertoireItemId) {
        return authorizationService
                .requireOwnedConcertWork(
                        email, choirId, concertId, repertoireItemId)
                .thenMany(Flux.defer(() ->
                        planningService.vocalAssignmentsForPlanning(
                                concertId, repertoireItemId)));
    }

    @Override
    public Flux<FormationPlanningService.TransitionPlanningPosition>
            positionsForTransitionVersion(
                    String email, long choirId, long transitionVersionId) {
        return authorizationService
                .requireOwnedTransitionVersion(
                        email, choirId, transitionVersionId)
                .thenMany(Flux.defer(() ->
                        planningService.positionsForTransitionVersion(
                                transitionVersionId)));
    }

    @Override
    public Mono<Void> updateDraftPosition(
            String email, long choirId, long positionId,
            String positionLabel, BigDecimal xCoordinate,
            BigDecimal yCoordinate) {
        return authorizationService
                .requireOwnedPosition(email, choirId, positionId)
                .then(Mono.defer(() -> planningService.updateDraftPosition(
                        positionId, positionLabel, xCoordinate, yCoordinate)));
    }
}
