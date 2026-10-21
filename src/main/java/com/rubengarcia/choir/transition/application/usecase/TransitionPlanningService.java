package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import reactor.core.publisher.Flux;

public interface TransitionPlanningService {

    Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId);
}
