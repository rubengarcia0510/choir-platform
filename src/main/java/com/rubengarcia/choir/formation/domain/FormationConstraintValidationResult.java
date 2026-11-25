package com.rubengarcia.choir.formation.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado inmutable del composite de validación de formación.
 * Válido solo sin violaciones. Si hay violaciones, no expone posiciones.
 */
public record FormationConstraintValidationResult(
        List<FormationConstraintViolation> violations,
        List<FormationPosition> positions) {

    public FormationConstraintValidationResult {
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
