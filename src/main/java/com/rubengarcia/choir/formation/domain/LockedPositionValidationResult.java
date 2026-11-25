package com.rubengarcia.choir.formation.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de validar una propuesta de posiciones contra los bloqueos persistidos.
 * Las posiciones solo se exponen cuando no hay violaciones; si las hay, la lista es vacía.
 */
public record LockedPositionValidationResult(
        List<LockedPositionViolation> violations,
        List<FormationPosition> positions) {

    public LockedPositionValidationResult {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations es obligatorio"));
        positions = List.copyOf(Objects.requireNonNull(positions, "positions es obligatorio"));
        if (!violations.isEmpty() && !positions.isEmpty()) {
            throw new IllegalArgumentException("Un resultado con violaciones no expone posiciones");
        }
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
