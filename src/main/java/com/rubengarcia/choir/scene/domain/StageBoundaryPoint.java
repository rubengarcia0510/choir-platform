package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Vértice del contorno en centímetros. Origen en la esquina delantera izquierda, mirando desde el
 * público: X crece de izquierda a derecha; Y crece del frente hacia el fondo.
 */
public record StageBoundaryPoint(BigDecimal x, BigDecimal y) {

    public StageBoundaryPoint {
        Objects.requireNonNull(x, "x es obligatorio");
        Objects.requireNonNull(y, "y es obligatorio");
    }

    /** Igualdad numérica: 1.0 y 1.00 representan el mismo punto. */
    public boolean sameAs(StageBoundaryPoint other) {
        return x.compareTo(other.x) == 0 && y.compareTo(other.y) == 0;
    }
}
