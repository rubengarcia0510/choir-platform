package com.rubengarcia.choir.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
class FormationPlanningServiceImpl implements FormationPlanningService {

    private final FormationPlanningDataRepository repository;

    FormationPlanningServiceImpl(
            FormationPlanningDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<PlanningPosition> positionsForFormationVersion(
            long formationVersionId) {
        return repository.positionsForFormationVersion(formationVersionId);
    }

    @Override
    public Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId, long repertoireItemId) {
        return repository.vocalAssignmentsForPlanning(
                concertId, repertoireItemId);
    }

    @Override
    public Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId) {
        return repository.positionsForTransitionVersion(transitionVersionId);
    }

    @Override
    public Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {
        return repository.updateDraftPosition(
                positionId, positionLabel, xCoordinate, yCoordinate);
    }
}
