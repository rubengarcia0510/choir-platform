package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FormationDraftPositionBoundsValidationTests {

    @Autowired
    private Validator validator;

    @Test
    void acceptsCoordinatesAtBothLimits() {
        var request = new FormationDraftController.SaveFormationDraftPositionsRequest(
                List.of(new FormationDraftController.PositionUpdateRequest(
                        1L,
                        "Posición válida",
                        BigDecimal.ZERO,
                        new BigDecimal("100"))));

        Set<ConstraintViolation<FormationDraftController.SaveFormationDraftPositionsRequest>>
                violations = validator.validate(request);

        assertTrue(violations.isEmpty(),
                "Las coordenadas 0 y 100 deben ser válidas");
    }

    @Test
    void rejectsNegativeXCoordinate() {
        var request = new FormationDraftController.SaveFormationDraftPositionsRequest(
                List.of(new FormationDraftController.PositionUpdateRequest(
                        1L,
                        "Posición inválida",
                        new BigDecimal("-0.01"),
                        new BigDecimal("50"))));

        Set<ConstraintViolation<FormationDraftController.SaveFormationDraftPositionsRequest>>
                violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("x")));
    }

    @Test
    void rejectsYCoordinateAboveOneHundred() {
        var request = new FormationDraftController.SaveFormationDraftPositionsRequest(
                List.of(new FormationDraftController.PositionUpdateRequest(
                        1L,
                        "Posición inválida",
                        new BigDecimal("50"),
                        new BigDecimal("100.01"))));

        Set<ConstraintViolation<FormationDraftController.SaveFormationDraftPositionsRequest>>
                violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("y")));
    }

    @Test
    void rejectsPositionLabelLongerThanEightyCharacters() {
        var request = new FormationDraftController.SaveFormationDraftPositionsRequest(
                List.of(new FormationDraftController.PositionUpdateRequest(
                        1L,
                        "x".repeat(81),
                        new BigDecimal("50"),
                        new BigDecimal("50"))));

        Set<ConstraintViolation<FormationDraftController.SaveFormationDraftPositionsRequest>>
                violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("positionLabel")));
    }

    @Test
    void acceptsPositionLabelOfExactlyEightyCharacters() {
        var request = new FormationDraftController.SaveFormationDraftPositionsRequest(
                List.of(new FormationDraftController.PositionUpdateRequest(
                        1L,
                        "x".repeat(80),
                        new BigDecimal("50"),
                        new BigDecimal("50"))));

        Set<ConstraintViolation<FormationDraftController.SaveFormationDraftPositionsRequest>>
                violations = validator.validate(request);

        assertTrue(violations.isEmpty(),
                "Una etiqueta de 80 caracteres debe ser válida");
    }
}
