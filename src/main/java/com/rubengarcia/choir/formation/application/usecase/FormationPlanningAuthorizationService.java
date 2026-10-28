package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.model.PlanningPosition;
import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import com.rubengarcia.choir.transition.application.model.CountGroupAssignment;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import java.math.BigDecimal;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationPlanningAuthorizationService {

    Flux<PlanningPosition> positionsForFormationVersion(
            String email, long choirId, long formationVersionId);

    Flux<VocalAssignment> vocalAssignmentsForPlanning(
            String email, long choirId, long concertId, long repertoireItemId);

    Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            String email, long choirId, long transitionVersionId);

    Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId);

    Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId,
            List<CountGroupAssignment> assignments);

    Flux<TransitionRuleEvaluationSnapshot> transitionRuleEvaluationHistory(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId);

    Mono<Void> updateDraftPosition(
            String email,
            long choirId,
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate);
}
