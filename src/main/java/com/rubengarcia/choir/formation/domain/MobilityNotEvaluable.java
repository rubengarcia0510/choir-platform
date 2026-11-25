package com.rubengarcia.choir.formation.domain;

import java.util.Objects;

/** Restricción activa que no pudo compararse con una posición. */
public record MobilityNotEvaluable(
        Long constraintId,
        Long singerId,
        Long positionId,
        MobilityNotEvaluableReason reason) {

    public MobilityNotEvaluable {
        Objects.requireNonNull(constraintId, "constraintId es obligatorio");
        Objects.requireNonNull(singerId, "singerId es obligatorio");
        Objects.requireNonNull(reason, "reason es obligatorio");
    }
}
