package com.rubengarcia.choir.transition.domain;

import java.time.OffsetDateTime;

public record TransitionMovementStep(
        long transitionId,
        long transitionVersionId,
        long fromFormationVersionId,
        long toFormationVersionId,
        long singerId,
        int sequenceNumber,
        OffsetDateTime temporalReference,
        String action) {
}
