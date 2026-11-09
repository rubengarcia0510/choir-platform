package com.rubengarcia.choir.transition.application.model;

import java.time.OffsetDateTime;

public record TransitionMovementStepInput(
        long singerId,
        int sequenceNumber,
        OffsetDateTime temporalReference,
        String action) {
}
