package com.rubengarcia.choir.transition.domain;

import java.math.BigDecimal;

public record TransitionPlanningPosition(
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
        BigDecimal yCoordinate) {
}
