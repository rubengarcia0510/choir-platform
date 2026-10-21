package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.transition.application.port.out.TransitionPlanningPositionRepository;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class TransitionPlanningServiceImpl
        implements TransitionPlanningService {

    private final TransitionPlanningPositionRepository positionRepository;

    public TransitionPlanningServiceImpl(
            TransitionPlanningPositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Override
    public Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId) {
        return positionRepository.findByTransitionVersionId(transitionVersionId);
    }
}
