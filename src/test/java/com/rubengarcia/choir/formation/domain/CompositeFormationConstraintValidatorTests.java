package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompositeFormationConstraintValidatorTests {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1"), new BigDecimal("8"),
            new BigDecimal("0"), new BigDecimal("10"));

    @Test
    void satisfiedConstraintsProduceAValidResultWithTheProposedPositions() {
        FormationConstraintValidationResult result = validate(
                List.of(position(10L, "5", "5", false)),
                List.of(position(10L, "5", "5", false), position(20L, "4", "5", false)),
                List.of(proximity(1L, 10L, 20L, "100")),
                List.of(mobility(1L, 10L)));

        assertTrue(result.isValid());
        assertEquals(2, result.positions().size());
    }

    @Test
    void validatedLockedPositionsAreTheFinalPositions() {
        FormationConstraintValidationResult result = validate(
                List.of(position(10L, "5", "20", true)),
                List.of(position(10L, "5", "20", false)),
                List.of(),
                List.of());

        assertTrue(result.isValid());
        assertEquals(10L, result.positions().get(0).getSingerId());
        assertTrue(result.positions().get(0).isLocked());
    }

    @Test
    void lockViolationIsAccumulatedAndHidesPositions() {
        FormationConstraintValidationResult result = validate(
                List.of(position(10L, "5", "20", true)),
                List.of(position(10L, "6", "20", true)),
                List.of(),
                List.of());

        assertFalse(result.isValid());
        assertEquals(List.of("LOCKED_POSITION_MOVED"), types(result));
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void proximityViolationIsAccumulatedAndHidesPositions() {
        FormationConstraintValidationResult result = validate(
                List.of(),
                List.of(position(10L, "0", "0", false), position(20L, "9", "0", false)),
                List.of(proximity(1L, 10L, 20L, "5")),
                List.of());

        assertEquals(List.of("PROXIMITY_VIOLATION"), types(result));
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void mobilityViolationIsAccumulatedAndHidesPositions() {
        FormationConstraintValidationResult result = validate(
                List.of(),
                List.of(position(10L, "9", "5", false)),
                List.of(),
                List.of(mobility(1L, 10L)));

        assertEquals(List.of("MOBILITY_CONSTRAINT_VIOLATION"), types(result));
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void violationsOfAllThreeKindsAreAccumulatedInFixedOrder() {
        FormationConstraintValidationResult result = validate(
                List.of(position(10L, "5", "20", true)),
                List.of(position(10L, "6", "20", true), position(20L, "0", "0", false)),
                List.of(proximity(1L, 10L, 20L, "1")),
                List.of(mobility(2L, 20L)));

        assertEquals(List.of("LOCKED_POSITION_MOVED", "PROXIMITY_VIOLATION", "MOBILITY_CONSTRAINT_VIOLATION"),
                types(result));
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void mobilityNotEvaluableIsAccumulatedAsAViolation() {
        FormationConstraintValidationResult result = validate(
                List.of(),
                List.of(),
                List.of(),
                List.of(mobility(1L, 10L)));

        assertEquals(List.of("MOBILITY_NOT_EVALUABLE"), types(result));
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void orderIsDeterministicAcrossRepeatedRuns() {
        List<String> first = types(scenarioWithAllThree());
        List<String> second = types(scenarioWithAllThree());

        assertEquals(first, second);
        assertEquals(List.of("LOCKED_POSITION_MOVED", "PROXIMITY_VIOLATION", "MOBILITY_CONSTRAINT_VIOLATION"), first);
    }

    @Test
    void concreteViolationsKeepTheirDetails() {
        FormationConstraintValidationResult result = scenarioWithAllThree();

        LockedPositionViolation locked = (LockedPositionViolation) result.violations().get(0);
        assertEquals(10L, locked.singerId());
        assertEquals(0, new BigDecimal("5").compareTo(locked.persistedX()));
        assertEquals(0, new BigDecimal("6").compareTo(locked.proposedX()));

        ProximityViolation proximity = (ProximityViolation) result.violations().get(1);
        assertEquals(1L, proximity.constraintId());
        assertEquals(20L, proximity.singerTwoId());
        assertEquals(0, new BigDecimal("436").compareTo(proximity.distanceSquared()));

        MobilityConstraintViolation mobility = (MobilityConstraintViolation) result.violations().get(2);
        assertEquals(2L, mobility.constraintId());
        assertEquals(Set.of(MobilityBound.X_BELOW_MIN), mobility.violatedBounds());
    }

    @Test
    void inputsAreNotModified() {
        FormationPosition persisted = position(10L, "5", "20", true);
        FormationPosition proposed = position(10L, "6", "20", false);
        List<FormationPosition> persistedList = List.of(persisted);
        List<FormationPosition> proposedList = List.of(proposed);

        validate(persistedList, proposedList, List.of(), List.of(mobility(1L, 10L)));

        assertTrue(persisted.isLocked());
        assertFalse(proposed.isLocked());
        assertEquals(0, new BigDecimal("6").compareTo(proposed.getXCoordinate()));
        assertEquals(1, persistedList.size());
        assertEquals(1, proposedList.size());
    }

    @Test
    void resultListsAreImmutable() {
        FormationConstraintValidationResult result = validate(
                List.of(position(10L, "5", "20", false)),
                List.of(position(10L, "5", "20", false)),
                List.of(),
                List.of());

        assertThrows(UnsupportedOperationException.class, () -> result.violations().add(null));
        assertThrows(UnsupportedOperationException.class, () -> result.positions().add(null));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> CompositeFormationConstraintValidator.validate(
                null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> CompositeFormationConstraintValidator.validate(
                List.of(), null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> CompositeFormationConstraintValidator.validate(
                List.of(), List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> CompositeFormationConstraintValidator.validate(
                List.of(), List.of(), List.of(), null));
        assertThrows(NullPointerException.class,
                () -> new FormationConstraintValidationResult(null, List.of()));
        assertThrows(NullPointerException.class,
                () -> new FormationConstraintValidationResult(List.of(), null));
    }

    @Test
    void inconsistentResultIsRejected() {
        FormationConstraintViolation violation = scenarioWithAllThree().violations().get(0);

        assertThrows(IllegalArgumentException.class, () -> new FormationConstraintValidationResult(
                List.of(violation), List.of(position(10L, "5", "20", false))));
    }

    @Test
    void unexpectedValidatorExceptionsArePropagated() {
        assertThrows(NullPointerException.class, () -> validate(
                List.of(),
                List.of(position(10L, "0", "0", false), position(20L, "1", "1", false)),
                List.of(proximity(1L, 10L, 20L, null)),
                List.of()));
    }

    // ------------------------------------------------------------------

    private static FormationConstraintValidationResult scenarioWithAllThree() {
        return validate(
                List.of(position(10L, "5", "20", true)),
                List.of(position(10L, "6", "20", true), position(20L, "0", "0", false)),
                List.of(proximity(1L, 10L, 20L, "1")),
                List.of(mobility(2L, 20L)));
    }

    private static List<String> types(FormationConstraintValidationResult result) {
        return result.violations().stream().map(FormationConstraintViolation::type).toList();
    }

    private static FormationConstraintValidationResult validate(
            List<FormationPosition> persisted,
            List<FormationPosition> proposed,
            List<FormationProximityConstraint> proximity,
            List<FormationMobilityConstraint> mobility) {
        return CompositeFormationConstraintValidator.validate(persisted, proposed, proximity, mobility);
    }

    private static FormationProximityConstraint proximity(
            long id, long singerOne, long singerTwo, String maxDistance) {
        return new FormationProximityConstraint(
                id, 1L, singerOne, singerTwo,
                maxDistance == null ? null : new BigDecimal(maxDistance), true, CREATED);
    }

    private static FormationMobilityConstraint mobility(long id, long singerId) {
        return new FormationMobilityConstraint(id, 1L, singerId, REGION, true, CREATED);
    }

    private static FormationPosition position(long singerId, String x, String y, boolean locked) {
        FormationPosition position = new FormationPosition();
        position.setId(singerId * 100);
        position.setSingerId(singerId);
        position.setXCoordinate(new BigDecimal(x));
        position.setYCoordinate(new BigDecimal(y));
        position.setLocked(locked);
        return position;
    }
}
