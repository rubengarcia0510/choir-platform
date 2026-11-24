package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rubengarcia.choir.formation.domain.FormationConstraintValidationResult;
import com.rubengarcia.choir.formation.domain.FormationConstraintViolation;
import com.rubengarcia.choir.formation.domain.LockedPositionViolation;
import com.rubengarcia.choir.formation.domain.MobilityBound;
import com.rubengarcia.choir.formation.domain.MobilityConstraintViolation;
import com.rubengarcia.choir.formation.domain.MobilityConstraintViolationCode;
import com.rubengarcia.choir.formation.domain.MobilityNotEvaluableReason;
import com.rubengarcia.choir.formation.domain.ProximityViolation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FormationConstraintViolationPresenterTests {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "code", "constraintId", "singerId", "singerOneId", "singerTwoId",
            "positionId", "reason", "message");

    @Test
    void lockedViolationHasCodeAndSingerWithoutConstraintOrPositionId() {
        Map<String, Object> item = single(locked(10L));

        assertEquals("LOCKED_POSITION_MOVED", item.get("code"));
        assertEquals(10L, item.get("singerId"));
        assertFalse(item.containsKey("constraintId"));
        assertFalse(item.containsKey("positionId"));
        assertFalse(item.containsKey("reason"));
    }

    @Test
    void proximityViolationHasConstraintAndBothSingers() {
        Map<String, Object> item = single(proximity(1L, 10L, 20L));

        assertEquals("PROXIMITY_VIOLATION", item.get("code"));
        assertEquals(1L, item.get("constraintId"));
        assertEquals(10L, item.get("singerOneId"));
        assertEquals(20L, item.get("singerTwoId"));
        assertFalse(item.containsKey("singerId"));
    }

    @Test
    void mobilityViolationOmitsPositionIdWhenNotPersisted() {
        Map<String, Object> item = single(mobility(2L, 10L, null));

        assertEquals("MOBILITY_CONSTRAINT_VIOLATION", item.get("code"));
        assertEquals(2L, item.get("constraintId"));
        assertEquals(10L, item.get("singerId"));
        assertFalse(item.containsKey("positionId"));
        assertFalse(item.containsKey("reason"));
    }

    @Test
    void mobilityViolationKeepsPositionIdWhenPresent() {
        Map<String, Object> item = single(mobility(2L, 10L, 100L));

        assertEquals(100L, item.get("positionId"));
    }

    @Test
    void notEvaluableMobilityCarriesItsReason() {
        Map<String, Object> item = single(notEvaluable(3L, 20L));

        assertEquals("MOBILITY_NOT_EVALUABLE", item.get("code"));
        assertEquals("MISSING_DRAFT_POSITION", item.get("reason"));
        assertEquals(20L, item.get("singerId"));
    }

    @Test
    void violationsKeepTheDeterministicOrder() {
        List<Map<String, Object>> items = FormationConstraintViolationPresenter.present(result(
                locked(10L), proximity(1L, 10L, 20L), mobility(2L, 20L, null), notEvaluable(3L, 30L)));

        assertEquals(List.of("LOCKED_POSITION_MOVED", "PROXIMITY_VIOLATION",
                        "MOBILITY_CONSTRAINT_VIOLATION", "MOBILITY_NOT_EVALUABLE"),
                items.stream().map(item -> item.get("code")).toList());
    }

    @Test
    void onlyAllowedKeysAreExposedAndMessagesHaveNoPersistenceDetails() {
        List<Map<String, Object>> items = FormationConstraintViolationPresenter.present(result(
                locked(10L), proximity(1L, 10L, 20L), mobility(2L, 20L, null), notEvaluable(3L, 30L)));

        for (Map<String, Object> item : items) {
            assertTrue(ALLOWED_KEYS.containsAll(item.keySet()), "clave no permitida: " + item.keySet());
            String message = (String) item.get("message");
            assertFalse(message.contains("SQL"));
            assertFalse(message.contains("formation_"));
            assertFalse(message.contains("JDBC"));
        }
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> single(FormationConstraintViolation violation) {
        List<Map<String, Object>> items = FormationConstraintViolationPresenter.present(
                result(violation));
        assertEquals(1, items.size());
        return items.get(0);
    }

    private static FormationConstraintValidationResult result(FormationConstraintViolation... violations) {
        return new FormationConstraintValidationResult(List.of(violations), List.of());
    }

    private static LockedPositionViolation locked(long singerId) {
        return new LockedPositionViolation(singerId,
                new BigDecimal("5"), new BigDecimal("20"),
                new BigDecimal("6"), new BigDecimal("20"));
    }

    private static ProximityViolation proximity(long constraintId, long singerOne, long singerTwo) {
        return new ProximityViolation(constraintId, singerOne, singerTwo,
                new BigDecimal("1"), new BigDecimal("436"), new BigDecimal("1"));
    }

    private static MobilityConstraintViolation mobility(long constraintId, long singerId, Long positionId) {
        return new MobilityConstraintViolation(
                MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION,
                constraintId, singerId, positionId,
                Set.of(MobilityBound.X_BELOW_MIN), null);
    }

    private static MobilityConstraintViolation notEvaluable(long constraintId, long singerId) {
        return new MobilityConstraintViolation(
                MobilityConstraintViolationCode.MOBILITY_NOT_EVALUABLE,
                constraintId, singerId, null, Set.of(),
                MobilityNotEvaluableReason.MISSING_DRAFT_POSITION);
    }
}
