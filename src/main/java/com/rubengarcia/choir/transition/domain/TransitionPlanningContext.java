package com.rubengarcia.choir.transition.domain;

public record TransitionPlanningContext(
        long transitionVersionId,
        long transitionId,
        long fromFormationVersionId,
        long toFormationVersionId,
        long repertoireItemId) {
}
