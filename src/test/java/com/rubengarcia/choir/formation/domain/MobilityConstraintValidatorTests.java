package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MobilityConstraintValidatorTests {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1"), new BigDecimal("8"),
            new BigDecimal("0"), new BigDecimal("10"));

    @Test
    void satisfiedConstraintIsValid() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "4", "5")));

        assertTrue(result.isValid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void xBelowMinimumIsAConstraintViolation() {
        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "0.5", "5"))));

        assertEquals(MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION, violation.code());
        assertEquals(Set.of(MobilityBound.X_BELOW_MIN), violation.violatedBounds());
    }

    @Test
    void xAboveMaximumIsAConstraintViolation() {
        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "9", "5"))));

        assertEquals(Set.of(MobilityBound.X_ABOVE_MAX), violation.violatedBounds());
    }

    @Test
    void yBelowMinimumIsAConstraintViolation() {
        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "4", "-1"))));

        assertEquals(Set.of(MobilityBound.Y_BELOW_MIN), violation.violatedBounds());
    }

    @Test
    void yAboveMaximumIsAConstraintViolation() {
        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "4", "11"))));

        assertEquals(Set.of(MobilityBound.Y_ABOVE_MAX), violation.violatedBounds());
    }

    @Test
    void simultaneousBreachesAreAllReportedInOneViolation() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "9", "-1")));

        assertEquals(1, result.violations().size());
        assertEquals(Set.of(MobilityBound.X_ABOVE_MAX, MobilityBound.Y_BELOW_MIN),
                result.violations().get(0).violatedBounds());
    }

    @Test
    void missingPositionIsNotEvaluable() {
        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of()));

        assertEquals(MobilityConstraintViolationCode.MOBILITY_NOT_EVALUABLE, violation.code());
        assertEquals(MobilityNotEvaluableReason.MISSING_DRAFT_POSITION, violation.notEvaluableReason());
    }

    @Test
    void duplicatedPositionsAreNotEvaluable() {
        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "4", "5"), position(101L, 10L, "4", "6"))));

        assertEquals(MobilityConstraintViolationCode.MOBILITY_NOT_EVALUABLE, violation.code());
        assertEquals(MobilityNotEvaluableReason.AMBIGUOUS_DRAFT_POSITION, violation.notEvaluableReason());
        assertEquals(null, violation.positionId());
    }

    @Test
    void incompleteCoordinatesAreNotEvaluableAndKeepThePositionId() {
        FormationPosition incomplete = position(100L, 10L, "4", "5");
        incomplete.setYCoordinate(null);

        MobilityConstraintViolation violation = single(validate(
                List.of(constraint(1L, 10L, true)),
                List.of(incomplete)));

        assertEquals(MobilityNotEvaluableReason.MISSING_COORDINATES, violation.notEvaluableReason());
        assertEquals(100L, violation.positionId());
    }

    @Test
    void inactiveConstraintsAreIgnored() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(1L, 10L, false)),
                List.of(position(100L, 10L, "99", "99")));

        assertTrue(result.isValid());
    }

    @Test
    void findingsAndNotEvaluableEntriesAccumulateInOneResult() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(1L, 10L, true), constraint(2L, 20L, true)),
                List.of(position(100L, 10L, "9", "5")));

        assertFalse(result.isValid());
        assertEquals(2, result.violations().size());
        assertEquals(MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION,
                result.violations().get(0).code());
        assertEquals(MobilityConstraintViolationCode.MOBILITY_NOT_EVALUABLE,
                result.violations().get(1).code());
    }

    @Test
    void identifiersAndReasonsArePreserved() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(7L, 10L, true), constraint(8L, 20L, true)),
                List.of(position(100L, 10L, "9", "5")));

        MobilityConstraintViolation finding = result.violations().get(0);
        assertEquals(7L, finding.constraintId());
        assertEquals(10L, finding.singerId());
        assertEquals(100L, finding.positionId());

        MobilityConstraintViolation notEvaluable = result.violations().get(1);
        assertEquals(8L, notEvaluable.constraintId());
        assertEquals(20L, notEvaluable.singerId());
        assertEquals(MobilityNotEvaluableReason.MISSING_DRAFT_POSITION, notEvaluable.notEvaluableReason());
    }

    @Test
    void resultAndViolationsAreImmutable() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(1L, 10L, true)),
                List.of(position(100L, 10L, "9", "5")));

        assertThrows(UnsupportedOperationException.class, () -> result.violations().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> result.violations().get(0).violatedBounds().add(MobilityBound.Y_ABOVE_MAX));
    }

    @Test
    void inputsAreNotModified() {
        FormationMobilityConstraint constraint = constraint(1L, 10L, true);
        FormationPosition position = position(100L, 10L, "9", "5");
        List<FormationMobilityConstraint> constraints = new ArrayList<>(List.of(constraint));
        List<FormationPosition> positions = new ArrayList<>(List.of(position));

        validate(constraints, positions);

        assertEquals(1, constraints.size());
        assertEquals(1, positions.size());
        assertTrue(constraint.active());
        assertEquals(0, new BigDecimal("9").compareTo(position.getXCoordinate()));
    }

    @Test
    void violationsFollowTheEvaluatorDeterministicOrder() {
        MobilityConstraintValidationResult result = validate(
                List.of(constraint(3L, 30L, true), constraint(1L, 10L, true)),
                List.of(position(300L, 30L, "9", "5"), position(100L, 10L, "9", "5")));

        assertEquals(List.of(1L, 3L),
                result.violations().stream().map(MobilityConstraintViolation::constraintId).toList());
    }

    @Test
    void constraintViolationWithoutBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MobilityConstraintViolation(
                MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION,
                1L, 10L, 100L, Set.of(), null));
    }

    @Test
    void notEvaluableViolationWithoutReasonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MobilityConstraintViolation(
                MobilityConstraintViolationCode.MOBILITY_NOT_EVALUABLE,
                1L, 10L, null, Set.of(), null));
    }

    // ------------------------------------------------------------------

    private static MobilityConstraintValidationResult validate(
            List<FormationMobilityConstraint> constraints,
            List<FormationPosition> positions) {
        return MobilityConstraintValidator.validate(constraints, positions);
    }

    private static MobilityConstraintViolation single(MobilityConstraintValidationResult result) {
        assertEquals(1, result.violations().size());
        return result.violations().get(0);
    }

    private static FormationMobilityConstraint constraint(long id, long singerId, boolean active) {
        return new FormationMobilityConstraint(id, 1L, singerId, REGION, active, CREATED);
    }

    private static FormationPosition position(long id, long singerId, String x, String y) {
        FormationPosition position = new FormationPosition();
        position.setId(id);
        position.setSingerId(singerId);
        position.setXCoordinate(new BigDecimal(x));
        position.setYCoordinate(new BigDecimal(y));
        return position;
    }
}
