package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import reactor.core.publisher.Flux;

public interface TransitionPlanningPositionRepository {

    Flux<TransitionPlanningPosition> findByTransitionVersionId(
            long transitionVersionId);
}
