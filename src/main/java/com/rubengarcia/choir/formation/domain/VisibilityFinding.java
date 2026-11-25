package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** VISIBILITY_VIOLATION: un coreuta posiblemente obstruye al protegido. */
public record VisibilityFinding(
        Long constraintId,
        Long protectedSingerId,
        Long obstructingSingerId,
        Long protectedPositionId,
        Long obstructingPositionId,
        Integer protectedHeightCm,
        Integer obstructingHeightCm,
        BigDecimal horizontalDistance) {

    public VisibilityFinding {
        Objects.requireNonNull(constraintId, "constraintId es obligatorio");
        Objects.requireNonNull(protectedSingerId, "protectedSingerId es obligatorio");
        Objects.requireNonNull(obstructingSingerId, "obstructingSingerId es obligatorio");
        Objects.requireNonNull(protectedPositionId, "protectedPositionId es obligatorio");
        Objects.requireNonNull(obstructingPositionId, "obstructingPositionId es obligatorio");
        Objects.requireNonNull(protectedHeightCm, "protectedHeightCm es obligatorio");
        Objects.requireNonNull(obstructingHeightCm, "obstructingHeightCm es obligatorio");
        Objects.requireNonNull(horizontalDistance, "horizontalDistance es obligatorio");
    }
}
