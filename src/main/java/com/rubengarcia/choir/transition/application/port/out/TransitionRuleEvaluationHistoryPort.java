package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransitionRuleEvaluationHistoryPort {

    Mono<TransitionRuleEvaluationSnapshot> save(
            TransitionRuleEvaluationSnapshot snapshot);

    Mono<TransitionRuleEvaluationSnapshot> findById(long evaluationId);

    Flux<TransitionRuleEvaluationSnapshot> findAllByTransitionVersionId(
            long transitionVersionId);
}
