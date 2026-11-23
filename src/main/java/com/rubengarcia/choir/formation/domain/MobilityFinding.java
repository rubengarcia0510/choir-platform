package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Un hallazgo por posición y restricción, con todos los límites incumplidos. */
public record MobilityFinding(
        Long constraintId,
        Long singerId,
        Long positionId,
        BigDecimal x,
        BigDecimal y,
        Set<MobilityBound> violatedBounds) {

    public MobilityFinding {
        Objects.requireNonNull(constraintId, "constraintId es obligatorio");
        Objects.requireNonNull(singerId, "singerId es obligatorio");
        Objects.requireNonNull(positionId, "positionId es obligatorio");
        Objects.requireNonNull(x, "x es obligatorio");
        Objects.requireNonNull(y, "y es obligatorio");
        if (violatedBounds == null || violatedBounds.isEmpty()) {
            throw new IllegalArgumentException("Un hallazgo requiere al menos un límite incumplido");
        }
        violatedBounds = Collections.unmodifiableSet(EnumSet.copyOf(violatedBounds));
    }
}
