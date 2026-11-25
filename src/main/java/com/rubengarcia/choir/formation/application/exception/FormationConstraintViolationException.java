package com.rubengarcia.choir.formation.application.exception;

import com.rubengarcia.choir.formation.domain.FormationConstraintValidationResult;
import com.rubengarcia.choir.formation.domain.FormationConstraintViolation;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Rechazo de una escritura por violaciones de restricciones de formación.
 * Transporta el resultado de dominio completo, con sus violaciones en orden determinista.
 */
public class FormationConstraintViolationException extends RuntimeException {

    private final FormationConstraintValidationResult result;

    public FormationConstraintViolationException(FormationConstraintValidationResult result) {
        super(messageFor(result));
        this.result = result;
    }

    public FormationConstraintValidationResult result() {
        return result;
    }

    private static String messageFor(FormationConstraintValidationResult result) {
        Objects.requireNonNull(result, "result es obligatorio");
        if (result.isValid()) {
            throw new IllegalArgumentException("Un resultado válido no puede rechazar una escritura");
        }
        String codes = result.violations().stream()
                .map(FormationConstraintViolation::type)
                .collect(Collectors.joining(", "));
        return "Escritura rechazada por restricciones de formación: " + codes;
    }
}
