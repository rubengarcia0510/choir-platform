package com.rubengarcia.choir.formation.domain;

import java.util.Objects;

/** Comparación de visibilidad que no pudo realizarse por datos faltantes. */
public record VisibilityNotEvaluable(
        Long constraintId,
        Long protectedSingerId,
        Long obstructingSingerId,
        Long positionId,
        VisibilityNotEvaluableReason reason) {

    public VisibilityNotEvaluable {
        Objects.requireNonNull(constraintId, "constraintId es obligatorio");
        Objects.requireNonNull(protectedSingerId, "protectedSingerId es obligatorio");
        Objects.requireNonNull(reason, "reason es obligatorio");
    }
}
