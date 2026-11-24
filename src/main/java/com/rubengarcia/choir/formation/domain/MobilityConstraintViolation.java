package com.rubengarcia.choir.formation.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Violación estructurada de una restricción de movilidad.
 *
 * <p>MOBILITY_CONSTRAINT_VIOLATION lleva los límites incumplidos y no tiene motivo.
 * MOBILITY_NOT_EVALUABLE no lleva límites y conserva el motivo original del evaluador.</p>
 */
public record MobilityConstraintViolation(
        MobilityConstraintViolationCode code,
        Long constraintId,
        Long singerId,
        Long positionId,
        Set<MobilityBound> violatedBounds,
        MobilityNotEvaluableReason notEvaluableReason) {

    public MobilityConstraintViolation {
        Objects.requireNonNull(code, "code es obligatorio");
        Objects.requireNonNull(constraintId, "constraintId es obligatorio");
        Objects.requireNonNull(singerId, "singerId es obligatorio");
        Objects.requireNonNull(violatedBounds, "violatedBounds es obligatorio");

        EnumSet<MobilityBound> bounds = EnumSet.noneOf(MobilityBound.class);
        bounds.addAll(violatedBounds);
        violatedBounds = Collections.unmodifiableSet(bounds);

        if (code == MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION) {
            if (violatedBounds.isEmpty()) {
                throw new IllegalArgumentException("Una violación de límites requiere al menos un límite");
            }
            if (notEvaluableReason != null) {
                throw new IllegalArgumentException("Una violación de límites no lleva motivo de no evaluable");
            }
        } else {
            if (notEvaluableReason == null) {
                throw new IllegalArgumentException("Una comparación no evaluable requiere su motivo");
            }
            if (!violatedBounds.isEmpty()) {
                throw new IllegalArgumentException("Una comparación no evaluable no lleva límites");
            }
        }
    }
}
