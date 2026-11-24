package com.rubengarcia.choir.formation.application.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rubengarcia.choir.formation.domain.FormationConstraintValidationResult;
import com.rubengarcia.choir.formation.domain.FormationConstraintViolation;
import com.rubengarcia.choir.formation.domain.LockedPositionViolation;
import com.rubengarcia.choir.formation.domain.ProximityViolation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormationConstraintViolationExceptionTests {

    @Test
    void messageListsEveryViolationTypeInOrder() {
        FormationConstraintValidationResult result = result(locked(), proximity());

        FormationConstraintViolationException exception = new FormationConstraintViolationException(result);

        assertEquals("Escritura rechazada por restricciones de formación: "
                + "LOCKED_POSITION_MOVED, PROXIMITY_VIOLATION", exception.getMessage());
    }

    @Test
    void exceptionKeepsTheCompleteDomainResult() {
        FormationConstraintValidationResult result = result(locked(), proximity());

        FormationConstraintViolationException exception = new FormationConstraintViolationException(result);

        assertSame(result, exception.result());
        assertEquals(2, exception.result().violations().size());
    }

    @Test
    void validResultCannotBeRejected() {
        FormationConstraintValidationResult valid = new FormationConstraintValidationResult(List.of(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new FormationConstraintViolationException(valid));
    }

    @Test
    void nullResultIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new FormationConstraintViolationException(null));
    }

    private static FormationConstraintValidationResult result(FormationConstraintViolation... violations) {
        return new FormationConstraintValidationResult(List.of(violations), List.of());
    }

    private static LockedPositionViolation locked() {
        return new LockedPositionViolation(10L,
                new BigDecimal("5"), new BigDecimal("20"),
                new BigDecimal("6"), new BigDecimal("20"));
    }

    private static ProximityViolation proximity() {
        return new ProximityViolation(1L, 10L, 20L,
                new BigDecimal("1"), new BigDecimal("436"), new BigDecimal("1"));
    }
}
