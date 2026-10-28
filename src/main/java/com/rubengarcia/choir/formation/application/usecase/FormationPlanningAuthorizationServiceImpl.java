package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.model.PlanningPosition;
import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import com.rubengarcia.choir.formation.application.port.out.FormationPlanningResourceAuthorizationPort;
import com.rubengarcia.choir.transition.application.model.CountGroupAssignment;
import com.rubengarcia.choir.transition.application.usecase.TransitionPlanningService;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId) {
        return authorizationPort
                .requireOwnedTransitionVersion(email, choirId, transitionVersionId)
                .then(Mono.defer(() -> transitionPlanningService
                        .evaluateTransitionVersion(
                                transitionId, transitionVersionId)));
    }

    @Override
    public Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId,
            List<CountGroupAssignment> assignments) {
        return authorizationPort
                .requireOwnedTransitionVersion(email, choirId, transitionVersionId)
                .then(Mono.defer(() -> transitionPlanningService
                        .evaluateTransitionVersion(
                                transitionId,
                                transitionVersionId,
                                assignments)));
    }

    @Override
    public Flux<TransitionRuleEvaluationSnapshot> transitionRuleEvaluationHistory(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId) {
        return authorizationPort
                .requireOwnedTransitionVersion(email, choirId, transitionVersionId)
                .thenMany(Flux.defer(() -> transitionPlanningService
                        .evaluationHistory(transitionId, transitionVersionId)));
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
