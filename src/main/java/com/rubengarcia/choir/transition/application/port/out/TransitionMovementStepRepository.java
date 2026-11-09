package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.transition.domain.TransitionMovementStep;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransitionMovementStepRepository {

    Flux<TransitionMovementStep> findByTransitionVersionId(
            long transitionVersionId);

    Mono<List<TransitionMovementStep>> replaceForDraftVersion(
            TransitionPlanningContext context,
            List<TransitionMovementStep> steps);
}
