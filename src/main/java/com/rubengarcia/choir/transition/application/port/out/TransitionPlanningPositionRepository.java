package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface TransitionPlanningPositionRepository {

    Flux<TransitionPlanningPosition> findByTransitionVersionId(
            long transitionVersionId);

    Mono<TransitionPlanningContext> findPlanningContext(
            long transitionVersionId);
}
