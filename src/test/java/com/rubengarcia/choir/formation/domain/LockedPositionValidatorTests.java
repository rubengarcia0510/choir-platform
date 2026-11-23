package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LockedPositionValidatorTests {

    private static final long LOCKED_SINGER = 10L;
    private static final long FREE_SINGER = 20L;

    @Test
    void lockedPositionWithoutChangesIsAccepted() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(LOCKED_SINGER, "5", "20", true)));

        assertTrue(result.isValid());
        assertEquals(1, result.positions().size());
    }

    @Test
    void lockedPositionWhoseXChangesIsRejected() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(LOCKED_SINGER, "6", "20", true)));

        assertFalse(result.isValid());
        assertEquals(1, result.violations().size());
        assertEquals(LOCKED_SINGER, result.violations().get(0).singerId());
        assertEquals("LOCKED_POSITION_MOVED", result.violations().get(0).type());
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void lockedPositionWhoseYChangesIsRejected() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(LOCKED_SINGER, "5", "21", true)));

        assertFalse(result.isValid());
        assertEquals(1, result.violations().size());
    }

    @Test
    void lockedPositionWhoseXAndYChangeIsRejectedOnce() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(LOCKED_SINGER, "6", "21", true)));

        assertFalse(result.isValid());
        assertEquals(1, result.violations().size());
        LockedPositionViolation violation = result.violations().get(0);
        assertEquals(0, new BigDecimal("5").compareTo(violation.persistedX()));
        assertEquals(0, new BigDecimal("21").compareTo(violation.proposedY()));
    }

    @Test
    void unlockedPositionMayChangeCoordinates() {
        LockedPositionValidationResult result = validate(
                List.of(position(FREE_SINGER, "5", "20", false)),
                List.of(position(FREE_SINGER, "9", "30", false)));

        assertTrue(result.isValid());
        assertEquals(0, new BigDecimal("9").compareTo(result.positions().get(0).getXCoordinate()));
    }

    @Test
    void acceptedLockedPositionKeepsLockedTrue() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(LOCKED_SINGER, "5", "20", false)));

        assertTrue(result.isValid());
        assertTrue(result.positions().get(0).isLocked());
    }

    @Test
    void omittedLockedPositionIsAcceptedBecauseItWasRemoved() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(FREE_SINGER, "7", "7", false)));

        assertTrue(result.isValid());
        assertEquals(1, result.positions().size());
        assertEquals(FREE_SINGER, result.positions().get(0).getSingerId());
    }

    @Test
    void proposedLockedPositionWithoutCoordinatesIsRejected() {
        FormationPosition missing = position(LOCKED_SINGER, "5", "20", true);
        missing.setXCoordinate(null);

        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(missing));

        assertFalse(result.isValid());
        assertEquals(null, result.violations().get(0).proposedX());
    }

    @Test
    void persistedLockedPositionWithoutCoordinatesIsAnInconsistency() {
        FormationPosition corrupted = position(LOCKED_SINGER, "5", "20", true);
        corrupted.setYCoordinate(null);

        assertThrows(IllegalArgumentException.class, () -> validate(
                List.of(corrupted),
                List.of(position(LOCKED_SINGER, "5", "20", true))));
    }

    @Test
    void coordinatesAreComparedNumericallyNotByScale() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5.0", "20", true)),
                List.of(position(LOCKED_SINGER, "5.000", "20.00", true)));

        assertTrue(result.isValid());
    }

    @Test
    void newSingerNotPersistedIsAcceptedWithItsOwnLockState() {
        LockedPositionValidationResult result = validate(
                List.of(position(LOCKED_SINGER, "5", "20", true)),
                List.of(position(99L, "1", "1", false), position(LOCKED_SINGER, "5", "20", true)));

        assertTrue(result.isValid());
        assertEquals(2, result.positions().size());
        assertFalse(result.positions().get(0).isLocked());
    }

    @Test
    void duplicatePersistedSingerIsAnInconsistency() {
        assertThrows(IllegalStateException.class, () -> validate(
                List.of(position(LOCKED_SINGER, "5", "20", true),
                        position(LOCKED_SINGER, "6", "21", true)),
                List.of(position(LOCKED_SINGER, "5", "20", true))));
    }

    @Test
    void inputsAreNotModified() {
        FormationPosition persisted = position(LOCKED_SINGER, "5", "20", true);
        FormationPosition proposed = position(LOCKED_SINGER, "5", "20", false);

        validate(List.of(persisted), List.of(proposed));

        assertTrue(persisted.isLocked());
        assertFalse(proposed.isLocked());
        assertEquals(0, new BigDecimal("20").compareTo(persisted.getYCoordinate()));
    }

    // ------------------------------------------------------------------

    private static LockedPositionValidationResult validate(
            List<FormationPosition> persisted, List<FormationPosition> proposed) {
        return LockedPositionValidator.validate(persisted, proposed);
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
