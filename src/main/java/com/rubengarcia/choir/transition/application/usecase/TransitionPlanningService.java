package com.rubengarcia.choir.transition.application.usecase;

import reactor.core.publisher.Flux;

import java.math.BigDecimal;

public interface TransitionPlanningService {

    record TransitionPlanningPosition(
            long transitionVersionId,
            String stage,
            long formationVersionId,
            Long singerId,
            String singerName,
            String voicePart,
            Long voiceSectionId,
            String voiceSectionName,
            Integer divisionNumber,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {}

    Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId);
}
