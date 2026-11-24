package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;

/**
 * Restricción de proximidad incumplida por las posiciones propuestas.
 * Se expresa con cuadrados de distancia para evitar raíces aproximadas.
 */
public record ProximityViolation(
        Long constraintId,
        Long singerOneId,
        Long singerTwoId,
        BigDecimal maxDistance,
        BigDecimal distanceSquared,
        BigDecimal maxDistanceSquared) {

    public static final String TYPE = "PROXIMITY_VIOLATION";

    public String type() {
        return TYPE;
    }
}
