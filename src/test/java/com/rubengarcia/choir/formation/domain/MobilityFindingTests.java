package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MobilityFindingTests {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1"), new BigDecimal("8"),
            new BigDecimal("0"), new BigDecimal("10"));

    @Test
    void findingCanBeBuiltWithoutPositionIdAndKeepsSingerBoundsAndCoordinates() {
        MobilityFinding finding = new MobilityFinding(
                7L, 10L, null,
                new BigDecimal("9"), new BigDecimal("5"),
                Set.of(MobilityBound.X_ABOVE_MAX));

        assertEquals(7L, finding.constraintId());
        assertEquals(10L, finding.singerId());
        assertNull(finding.positionId());
        assertEquals(0, new BigDecimal("9").compareTo(finding.x()));
        assertEquals(0, new BigDecimal("5").compareTo(finding.y()));
        assertEquals(Set.of(MobilityBound.X_ABOVE_MAX), finding.violatedBounds());
    }

    @Test
    void findingKeepsPositionIdWhenPresent() {
        MobilityFinding finding = new MobilityFinding(
                7L, 10L, 100L,
                new BigDecimal("9"), new BigDecimal("5"),
                Set.of(MobilityBound.X_ABOVE_MAX));

        assertEquals(100L, finding.positionId());
    }

    @Test
    void singerIdConstraintIdAndCoordinatesStayMandatory() {
        assertThrows(NullPointerException.class, () -> new MobilityFinding(
                null, 10L, null, BigDecimal.ONE, BigDecimal.ONE, Set.of(MobilityBound.X_BELOW_MIN)));
        assertThrows(NullPointerException.class, () -> new MobilityFinding(
                7L, null, null, BigDecimal.ONE, BigDecimal.ONE, Set.of(MobilityBound.X_BELOW_MIN)));
        assertThrows(NullPointerException.class, () -> new MobilityFinding(
                7L, 10L, null, null, BigDecimal.ONE, Set.of(MobilityBound.X_BELOW_MIN)));
        assertThrows(NullPointerException.class, () -> new MobilityFinding(
                7L, 10L, null, BigDecimal.ONE, null, Set.of(MobilityBound.X_BELOW_MIN)));
    }

    @Test
    void findingStillRequiresAtLeastOneViolatedBound() {
        assertThrows(IllegalArgumentException.class, () -> new MobilityFinding(
                7L, 10L, null, BigDecimal.ONE, BigDecimal.ONE, Set.of()));
    }

    @Test
    void violatedBoundsAreCopiedAndImmutable() {
        java.util.EnumSet<MobilityBound> source = java.util.EnumSet.of(MobilityBound.Y_ABOVE_MAX);
        MobilityFinding finding = new MobilityFinding(
                7L, 10L, null, BigDecimal.ONE, BigDecimal.ONE, source);

        source.add(MobilityBound.X_BELOW_MIN);

        assertEquals(Set.of(MobilityBound.Y_ABOVE_MAX), finding.violatedBounds());
        assertThrows(UnsupportedOperationException.class,
                () -> finding.violatedBounds().add(MobilityBound.X_BELOW_MIN));
    }

    @Test
    void validatorReportsViolationWithoutPositionIdForUnsavedProposedPositions() {
        FormationPosition proposed = new FormationPosition();
        proposed.setSingerId(10L);
        proposed.setXCoordinate(new BigDecimal("9"));
        proposed.setYCoordinate(new BigDecimal("5"));

        MobilityConstraintValidationResult result = MobilityConstraintValidator.validate(
                List.of(new FormationMobilityConstraint(7L, 1L, 10L, REGION, true, CREATED)),
                List.of(proposed));

        assertEquals(1, result.violations().size());
        MobilityConstraintViolation violation = result.violations().get(0);
        assertEquals(MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION, violation.code());
        assertEquals(7L, violation.constraintId());
        assertEquals(10L, violation.singerId());
        assertNull(violation.positionId());
        assertEquals(Set.of(MobilityBound.X_ABOVE_MAX), violation.violatedBounds());
        assertTrue(!result.isValid());
    }
}
