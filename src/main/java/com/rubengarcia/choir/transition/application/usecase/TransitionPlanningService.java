package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransitionPlanningService {

    Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId);

    Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            long transitionId, long transitionVersionId);

    Flux<TransitionRuleEvaluationSnapshot> evaluationHistory(
            long transitionId, long transitionVersionId);
}
