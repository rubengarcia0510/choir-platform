package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Elemento físico como superficie rectangular 2D. (x, y) es la esquina inferior izquierda del rectángulo
 * en el plano del escenario. La elevación es la altura respecto del nivel de referencia del escenario:
 * obligatoria y positiva para escalones y plataformas, y cero para el resto.
 */
public record StageElement(
        String name,
        StageElementType type,
        BigDecimal x,
        BigDecimal y,
        BigDecimal widthCm,
        BigDecimal depthCm,
        BigDecimal elevationCm) {

    public StageElement {
        Objects.requireNonNull(name, "name es obligatorio");
        if (name.isBlank()) {
            throw new StageGeometryException("El elemento requiere un nombre");
        }
        Objects.requireNonNull(type, "type es obligatorio");
        Objects.requireNonNull(x, "x es obligatorio");
        Objects.requireNonNull(y, "y es obligatorio");
        Objects.requireNonNull(widthCm, "widthCm es obligatorio");
        Objects.requireNonNull(depthCm, "depthCm es obligatorio");
        Objects.requireNonNull(elevationCm, "elevationCm es obligatorio");
        if (widthCm.signum() <= 0 || depthCm.signum() <= 0) {
            throw new StageGeometryException("Las dimensiones del elemento deben ser positivas");
        }
        if (elevationCm.signum() < 0) {
            throw new StageGeometryException("La elevación no puede ser negativa");
        }
        if (type.allowsElevation() && elevationCm.signum() == 0) {
            throw new StageGeometryException(
                    "Escalones y plataformas requieren una elevación positiva respecto del nivel de referencia");
        }
        if (!type.allowsElevation() && elevationCm.signum() != 0) {
            throw new StageGeometryException("Solo escalones y plataformas admiten elevación");
        }
    }
}
