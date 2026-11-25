package com.rubengarcia.choir.formation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.application.exception.FormationConstraintViolationException;
import com.rubengarcia.choir.formation.application.exception.PhysicalConstraintConflictException;
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
import org.springframework.http.ProblemDetail;

class FormationPlanningExceptionHandlerTests {

    private static final String CONFLICT_DETAIL = "La operación no puede completarse en el estado actual";

    private final FormationPlanningExceptionHandler handler = new FormationPlanningExceptionHandler();

    @Test
    void physicalConstraintConflictKeepsItsConflictMapping() {
        ProblemDetail problem = handler.handleConflict(new PhysicalConstraintConflictException());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getDetail()).isEqualTo(CONFLICT_DETAIL);
        assertThat(problem.getProperties() == null || !problem.getProperties().containsKey("violations"))
                .isTrue();
    }

    @Test
    void lockedViolationReturnsConflictWithStableCode() {
        ProblemDetail problem = handler.handleConstraintViolation(exception(locked(10L)));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo(CONFLICT_DETAIL);
        assertThat(violations(problem)).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("code", "LOCKED_POSITION_MOVED");
            assertThat(item).containsEntry("singerId", 10L);
            assertThat(item).doesNotContainKeys("constraintId", "positionId", "reason");
        });
    }

    @Test
    void proximityViolationReturnsConflictWithStableCode() {
        ProblemDetail problem = handler.handleConstraintViolation(exception(proximity(1L, 10L, 20L)));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(violations(problem)).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("code", "PROXIMITY_VIOLATION");
            assertThat(item).containsEntry("constraintId", 1L);
            assertThat(item).containsEntry("singerOneId", 10L);
            assertThat(item).containsEntry("singerTwoId", 20L);
        });
    }

    @Test
    void mobilityViolationReturnsConflictWithStableCode() {
        ProblemDetail problem = handler.handleConstraintViolation(
                exception(mobility(2L, 10L, null)));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(violations(problem)).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("code", "MOBILITY_CONSTRAINT_VIOLATION");
            assertThat(item).containsEntry("constraintId", 2L);
            assertThat(item).containsEntry("singerId", 10L);
            assertThat(item).doesNotContainKey("positionId");
        });
    }

    @Test
    void notEvaluableMobilityReturnsConflictWithCodeAndReason() {
        ProblemDetail problem = handler.handleConstraintViolation(exception(notEvaluable(3L, 20L)));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(violations(problem)).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("code", "MOBILITY_NOT_EVALUABLE");
            assertThat(item).containsEntry("reason", "MISSING_DRAFT_POSITION");
            assertThat(item).containsEntry("singerId", 20L);
        });
    }

    @Test
    void severalViolationsAllAppearInOrder() {
        ProblemDetail problem = handler.handleConstraintViolation(exception(
                locked(10L), proximity(1L, 10L, 20L), mobility(2L, 20L, null), notEvaluable(3L, 30L)));

        assertThat(violations(problem)).extracting(item -> item.get("code")).containsExactly(
                "LOCKED_POSITION_MOVED", "PROXIMITY_VIOLATION",
                "MOBILITY_CONSTRAINT_VIOLATION", "MOBILITY_NOT_EVALUABLE");
    }

    @Test
    void responseDoesNotExposePersistenceDetails() {
        ProblemDetail problem = handler.handleConstraintViolation(exception(
                locked(10L), mobility(2L, 20L, null)));

        assertThat(problem.getDetail()).doesNotContain("SQL", "formation_", "JDBC");
        assertThat(violations(problem)).allSatisfy(item ->
                assertThat(item.get("message").toString()).doesNotContain("SQL", "formation_", "JDBC"));
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> violations(ProblemDetail problem) {
        return (List<Map<String, Object>>) problem.getProperties().get("violations");
    }

    private static FormationConstraintViolationException exception(FormationConstraintViolation... violations) {
        return new FormationConstraintViolationException(
                new FormationConstraintValidationResult(List.of(violations), List.of()));
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
