package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProximityConstraintValidatorTests {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Test
    void positionsWithinTheMaximumDistanceAreAccepted() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "5", true)),
                List.of(position(10L, "0", "0"), position(20L, "1", "1")));

        assertTrue(result.isValid());
    }

    @Test
    void distanceExactlyAtTheLimitIsAccepted() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "5", true)),
                List.of(position(10L, "0", "0"), position(20L, "3", "4")));

        assertTrue(result.isValid());
    }

    @Test
    void positionsBeyondTheMaximumDistanceAreRejected() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "4.9", true)),
                List.of(position(10L, "0", "0"), position(20L, "3", "4")));

        assertFalse(result.isValid());
        assertEquals(1, result.violations().size());
        assertEquals(0, new BigDecimal("25").compareTo(result.violations().get(0).distanceSquared()));
        assertEquals(0, new BigDecimal("24.01").compareTo(result.violations().get(0).maxDistanceSquared()));
    }

    @Test
    void differencesOnlyInXAreEvaluated() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "2", true)),
                List.of(position(10L, "0", "5"), position(20L, "3", "5")));

        assertEquals(1, result.violations().size());
    }

    @Test
    void differencesOnlyInYAreEvaluated() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "2", true)),
                List.of(position(10L, "5", "0"), position(20L, "5", "3")));

        assertEquals(1, result.violations().size());
    }

    @Test
    void severalConstraintsReportEveryViolatedPair() {
        ProximityValidationResult result = validate(
                List.of(
                        constraint(1L, 10L, 20L, "1", true),
                        constraint(2L, 20L, 30L, "1", true),
                        constraint(3L, 10L, 30L, "100", true)),
                List.of(position(10L, "0", "0"), position(20L, "5", "0"), position(30L, "10", "0")));

        assertEquals(2, result.violations().size());
        assertEquals(1L, result.violations().get(0).constraintId());
        assertEquals(20L, result.violations().get(0).singerTwoId());
        assertEquals(2L, result.violations().get(1).constraintId());
        assertEquals(30L, result.violations().get(1).singerTwoId());
    }

    @Test
    void missingFirstPositionProducesNoViolation() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "1", true)),
                List.of(position(20L, "50", "50")));

        assertTrue(result.isValid());
    }

    @Test
    void missingSecondPositionProducesNoViolation() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "1", true)),
                List.of(position(10L, "0", "0")));

        assertTrue(result.isValid());
    }

    @Test
    void decimalCoordinatesWithDifferentScalesCompareExactly() {
        ProximityValidationResult atLimit = validate(
                List.of(constraint(1L, 10L, 20L, "1.0", true)),
                List.of(position(10L, "0.5", "0"), position(20L, "1.500", "0.000")));
        ProximityValidationResult justOver = validate(
                List.of(constraint(1L, 10L, 20L, "1.00", true)),
                List.of(position(10L, "0.5", "0"), position(20L, "1.5001", "0")));

        assertTrue(atLimit.isValid());
        assertEquals(1, justOver.violations().size());
    }

    @Test
    void inactiveConstraintsAreIgnored() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "1", false)),
                List.of(position(10L, "0", "0"), position(20L, "50", "50")));

        assertTrue(result.isValid());
    }

    @Test
    void presentPositionWithoutCoordinatesIsAnInconsistency() {
        FormationPosition missing = position(20L, "1", "1");
        missing.setXCoordinate(null);

        assertThrows(IllegalArgumentException.class, () -> validate(
                List.of(constraint(1L, 10L, 20L, "5", true)),
                List.of(position(10L, "0", "0"), missing)));
    }

    @Test
    void repeatedSingerKeepsTheLastProposedPosition() {
        ProximityValidationResult result = validate(
                List.of(constraint(1L, 10L, 20L, "1", true)),
                List.of(position(10L, "0", "0"), position(20L, "50", "50"), position(20L, "0", "0")));

        assertTrue(result.isValid());
    }

    @Test
    void inputsAreNotModified() {
        FormationProximityConstraint constraint = constraint(1L, 10L, 20L, "1", true);
        FormationPosition first = position(10L, "0", "0");
        FormationPosition second = position(20L, "50", "50");

        validate(List.of(constraint), List.of(first, second));

        assertEquals(0, new BigDecimal("50").compareTo(second.getXCoordinate()));
        assertEquals(0, new BigDecimal("1").compareTo(constraint.maxDistance()));
        assertTrue(constraint.active());
        assertEquals(10L, first.getSingerId());
    }

    // ------------------------------------------------------------------

    private static ProximityValidationResult validate(
            List<FormationProximityConstraint> constraints,
            List<FormationPosition> positions) {
        return ProximityConstraintValidator.validate(constraints, positions);
    }

    private static FormationProximityConstraint constraint(
            long id, long singerOne, long singerTwo, String maxDistance, boolean active) {
        return new FormationProximityConstraint(
                id, 1L, singerOne, singerTwo, new BigDecimal(maxDistance), active, CREATED);
    }

    private static FormationPosition position(long singerId, String x, String y) {
        FormationPosition position = new FormationPosition();
        position.setId(singerId * 100);
        position.setSingerId(singerId);
        position.setXCoordinate(new BigDecimal(x));
        position.setYCoordinate(new BigDecimal(y));
        return position;
    }
}
