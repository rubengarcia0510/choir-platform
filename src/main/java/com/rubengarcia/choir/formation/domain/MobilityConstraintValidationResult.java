package com.rubengarcia.choir.formation.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado inmutable de validar restricciones de movilidad.
 * Válido solo si no hay violaciones: las comparaciones no evaluables cuentan como violación.
 */
public record MobilityConstraintValidationResult(List<MobilityConstraintViolation> violations) {

    public MobilityConstraintValidationResult {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations es obligatorio"));
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
