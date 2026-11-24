package com.rubengarcia.choir.formation.domain;

import java.util.List;
import java.util.Objects;

/** Resultado de validar posiciones propuestas contra restricciones de proximidad. */
public record ProximityValidationResult(List<ProximityViolation> violations) {

    public ProximityValidationResult {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations es obligatorio"));
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
