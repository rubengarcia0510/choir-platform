package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisibilityEvaluatorTests {

    private static final long PROTECTED = 10L;
    private static final long OBSTRUCTOR = 20L;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 22, 10, 0);

    @Test
    void alignedFrontAndTallerObstructorIsAFinding() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertEquals(1, result.findings().size());
        VisibilityFinding finding = result.findings().get(0);
        assertEquals(OBSTRUCTOR, finding.obstructingSingerId());
        assertEquals(0, new BigDecimal("0").compareTo(finding.horizontalDistance()));
    }

    @Test
    void horizontalDifferenceExactlyTwoIsAFinding() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 7, 40, 180)));

        assertEquals(1, result.findings().size());
    }

    @Test
    void horizontalDifferenceGreaterThanTwoIsNotAFinding() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 7.001, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void obstructorBehindOrAtTheSameDepthIsNotAFinding() {
        VisibilityEvaluation behind = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 10, 180)));
        VisibilityEvaluation sameDepth = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 20, 180)));

        assertTrue(behind.findings().isEmpty());
        assertTrue(sameDepth.findings().isEmpty());
        assertEquals(VisibilityEvaluationStatus.EVALUABLE, sameDepth.status());
    }

    @Test
    void equalHeightsAreNotAnObstruction() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 170), obstructorAt(2L, OBSTRUCTOR, 5, 40, 170)));

        assertTrue(result.findings().isEmpty());
        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
    }

    @Test
    void shorterObstructorIsNotAFinding() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 170), obstructorAt(2L, OBSTRUCTOR, 5, 40, 160)));

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void severalObstructorsProduceOneFindingEach() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        obstructorAt(2L, 20L, 5, 40, 180),
                        obstructorAt(3L, 30L, 4, 60, 190)));

        assertEquals(2, result.findings().size());
        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
    }

    @Test
    void inactiveConstraintIsNotEvaluatedAtAll() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, false)),
                List.of(obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.notEvaluable().isEmpty());
    }

    @Test
    void missingProtectedPositionIsNotEvaluable() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.MISSING_PROTECTED_DRAFT_POSITION,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void duplicatedProtectedPositionIsNotEvaluable() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), protectedAt(3L, 6, 21, 160)));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.AMBIGUOUS_PROTECTED_DRAFT_POSITION,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void missingProtectedCoordinatesAreNotEvaluable() {
        FormationPosition protectedPosition = protectedAt(1L, 5, 20, 160);
        protectedPosition.setXCoordinate(null);

        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedPosition));

        assertEquals(VisibilityNotEvaluableReason.MISSING_PROTECTED_COORDINATES,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void missingProtectedHeightIsNotEvaluable() {
        FormationPosition protectedPosition = protectedAt(1L, 5, 20, null);

        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedPosition));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.MISSING_PROTECTED_HEIGHT,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void missingObstructorCoordinatesAreNotEvaluable() {
        FormationPosition obstructor = obstructorAt(2L, OBSTRUCTOR, 5, 40, 180);
        obstructor.setYCoordinate(null);

        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructor));

        assertEquals(VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_COORDINATES,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void missingObstructorHeightAlignedAndInFrontIsNotEvaluable() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, null)));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_HEIGHT,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void obstructorWithoutHeightOutOfToleranceDoesNotPreventCompleteEvaluation() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 50, 40, null)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void obstructorWithoutHeightBehindDoesNotPreventCompleteEvaluation() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 10, null)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void findingsAndNotEvaluableEntriesCoexistInOneResult() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        obstructorAt(2L, 20L, 5, 40, 180),
                        obstructorAt(3L, 30L, 5, 60, null)));

        assertEquals(1, result.findings().size());
        assertEquals(1, result.notEvaluable().size());
        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
    }

    @Test
    void evaluationDoesNotModifyInputs() {
        FormationPosition protectedPosition = protectedAt(1L, 5, 20, 160);
        FormationPosition obstructor = obstructorAt(2L, OBSTRUCTOR, 5, 40, 180);
        FormationVisibilityConstraint constraint = constraint(1L, PROTECTED, true);

        evaluate(List.of(constraint), List.of(protectedPosition, obstructor));

        assertEquals(0, new BigDecimal("5").compareTo(protectedPosition.getXCoordinate()));
        assertEquals(0, new BigDecimal("20").compareTo(protectedPosition.getYCoordinate()));
        assertEquals(Integer.valueOf(160), protectedPosition.getHeightCmSnapshot());
        assertEquals(Integer.valueOf(180), obstructor.getHeightCmSnapshot());
        assertTrue(constraint.active());
    }

    @Test
    void noActiveConstraintsIsEvaluableWithoutFindings() {
        VisibilityEvaluation result = evaluate(
                List.of(), List.of(protectedAt(1L, 5, 20, 160)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertTrue(result.findings().isEmpty());
        assertFalse(result.notEvaluable().size() > 0);
    }

    // ------------------------------------------------------------------

    private static VisibilityEvaluation evaluate(
            List<FormationVisibilityConstraint> constraints,
            List<FormationPosition> positions) {
        return VisibilityEvaluator.evaluate(constraints, positions);
    }

    private static FormationVisibilityConstraint constraint(
            long id, long protectedSingerId, boolean active) {
        return new FormationVisibilityConstraint(id, 1L, protectedSingerId, active, CREATED);
    }

    private static FormationPosition protectedAt(long id, double x, double y, Integer height) {
        return position(id, PROTECTED, x, y, height);
    }

    private static FormationPosition obstructorAt(
            long id, long singerId, double x, double y, Integer height) {
        return position(id, singerId, x, y, height);
    }

    private static FormationPosition position(
            long id, long singerId, double x, double y, Integer height) {
        FormationPosition position = new FormationPosition();
        position.setId(id);
        position.setSingerId(singerId);
        position.setXCoordinate(BigDecimal.valueOf(x));
        position.setYCoordinate(BigDecimal.valueOf(y));
        position.setHeightCmSnapshot(height);
        return position;
    }
}
