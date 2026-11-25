package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class VisibilityEvaluatorTests {

    private static final long PROTECTED = 10L;
    private static final long OBSTRUCTOR = 20L;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 22, 10, 0);

    // ------------------------------------------------------------------
    // Regla geométrica y altura
    // ------------------------------------------------------------------

    @Test
    void alignedFrontAndTallerObstructorIsAFinding() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertEquals(1, result.findings().size());
        VisibilityFinding finding = result.findings().get(0);
        assertEquals(OBSTRUCTOR, finding.obstructingSingerId());
        assertEquals(PROTECTED, finding.protectedSingerId());
        assertEquals(2L, finding.obstructingPositionId());
        assertEquals(1L, finding.protectedPositionId());
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

    // ------------------------------------------------------------------
    // Múltiples posiciones
    // ------------------------------------------------------------------

    @Test
    void everyProtectedPositionIsEvaluatedNotDeclaredAmbiguous() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        protectedAt(3L, 6, 21, 160),
                        obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertEquals(2, result.findings().size());
        assertTrue(result.notEvaluable().isEmpty());
    }

    @Test
    void everyCombinationOfProtectedAndObstructorPositionsIsEvaluated() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        protectedAt(3L, 6, 21, 160),
                        obstructorAt(2L, OBSTRUCTOR, 5, 40, 180),
                        obstructorAt(4L, OBSTRUCTOR, 6, 50, 200)));

        assertEquals(4, result.findings().size());
        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
    }

    @Test
    void severalPositionsOfOneObstructorProduceOneFindingEachMatchingPosition() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        obstructorAt(2L, OBSTRUCTOR, 5, 40, 180),
                        obstructorAt(4L, OBSTRUCTOR, 7, 50, 170),
                        obstructorAt(5L, OBSTRUCTOR, 9, 50, 200)));

        assertEquals(2, result.findings().size());
        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
    }

    @Test
    void severalPositionsOfOneObstructorDoNotCreateAFalseAmbiguityState() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        obstructorAt(2L, OBSTRUCTOR, 5, 40, 180),
                        obstructorAt(4L, OBSTRUCTOR, 5, 60, 150)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertEquals(1, result.findings().size());
    }

    @Test
    void validProtectedPositionIsKeptWhenAnotherOneLacksCoordinates() {
        FormationPosition missing = protectedAt(3L, 6, 21, 160);
        missing.setXCoordinate(null);

        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), missing,
                        obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(1, result.findings().size());
        assertEquals(VisibilityNotEvaluableReason.MISSING_PROTECTED_COORDINATES,
                result.notEvaluable().get(0).reason());
        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
    }

    @Test
    void validObstructorPositionIsKeptWhenAnotherLacksCoordinates() {
        FormationPosition missing = obstructorAt(4L, OBSTRUCTOR, 5, 50, 200);
        missing.setYCoordinate(null);

        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160),
                        obstructorAt(2L, OBSTRUCTOR, 5, 40, 180), missing));

        assertEquals(1, result.findings().size());
        assertEquals(VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_COORDINATES,
                result.notEvaluable().get(0).reason());
        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
    }

    // ------------------------------------------------------------------
    // Datos faltantes del protegido y del obstructor
    // ------------------------------------------------------------------

    @Test
    void missingProtectedDraftPositionIsNotEvaluable() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.MISSING_PROTECTED_DRAFT_POSITION,
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
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, null)));

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

    // ------------------------------------------------------------------
    // Roster asignado
    // ------------------------------------------------------------------

    @Test
    void assignedSingerWithoutDraftPositionIsNotEvaluable() {
        VisibilityEvaluation result = VisibilityEvaluator.evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)),
                Set.of(PROTECTED, OBSTRUCTOR, 30L));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(1, result.findings().size());
        VisibilityNotEvaluable entry = result.notEvaluable().get(0);
        assertEquals(30L, entry.obstructingSingerId());
        assertEquals(VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_DRAFT_POSITION, entry.reason());
    }

    @Test
    void assignedProtectedSingerWithoutDraftPositionIsNotEvaluable() {
        VisibilityEvaluation result = VisibilityEvaluator.evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)),
                Set.of(PROTECTED, OBSTRUCTOR));

        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.MISSING_PROTECTED_DRAFT_POSITION,
                result.notEvaluable().get(0).reason());
    }

    @Test
    void unassignedSingerWithoutDraftPositionDoesNotAffectEvaluation() {
        VisibilityEvaluation result = VisibilityEvaluator.evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)),
                Set.of(PROTECTED, OBSTRUCTOR));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertEquals(1, result.findings().size());
        assertTrue(result.notEvaluable().isEmpty());
    }

    @Test
    void positionOfAnUnassignedSingerIsNotUsedForFindings() {
        VisibilityEvaluation result = VisibilityEvaluator.evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)),
                Set.of(PROTECTED));

        assertTrue(result.findings().isEmpty());
        assertEquals(VisibilityEvaluationStatus.NOT_EVALUABLE, result.status());
        assertEquals(VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER,
                result.notEvaluable().get(0).reason());
    }

    // ------------------------------------------------------------------
    // Entradas y resultado
    // ------------------------------------------------------------------

    @Test
    void evaluationDoesNotModifyInputs() {
        FormationPosition protectedPosition = protectedAt(1L, 5, 20, 160);
        FormationPosition obstructor = obstructorAt(2L, OBSTRUCTOR, 5, 40, 180);
        FormationVisibilityConstraint constraint = constraint(1L, PROTECTED, true);
        List<FormationPosition> positions = List.of(protectedPosition, obstructor);
        List<FormationVisibilityConstraint> constraints = List.of(constraint);

        VisibilityEvaluator.evaluate(constraints, positions, Set.of(PROTECTED, OBSTRUCTOR));

        assertEquals(0, new BigDecimal("5").compareTo(protectedPosition.getXCoordinate()));
        assertEquals(0, new BigDecimal("20").compareTo(protectedPosition.getYCoordinate()));
        assertEquals(Integer.valueOf(160), protectedPosition.getHeightCmSnapshot());
        assertEquals(Integer.valueOf(180), obstructor.getHeightCmSnapshot());
        assertTrue(constraint.active());
        assertEquals(2, positions.size());
        assertEquals(1, constraints.size());
    }

    @Test
    void noActiveConstraintsIsEvaluableWithoutFindings() {
        VisibilityEvaluation result = evaluate(
                List.of(), List.of(protectedAt(1L, 5, 20, 160)));

        assertEquals(VisibilityEvaluationStatus.EVALUABLE, result.status());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.notEvaluable().isEmpty());
    }

    @Test
    void resultListsAreImmutable() {
        VisibilityEvaluation result = evaluate(
                List.of(constraint(1L, PROTECTED, true)),
                List.of(protectedAt(1L, 5, 20, 160), obstructorAt(2L, OBSTRUCTOR, 5, 40, 180)));

        assertThrows(UnsupportedOperationException.class,
                () -> result.findings().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> result.notEvaluable().add(null));
    }

    // ------------------------------------------------------------------

    /** Roster derivado: todos los coreutas con posición más el protegido. */
    private static VisibilityEvaluation evaluate(
            List<FormationVisibilityConstraint> constraints,
            List<FormationPosition> positions) {
        Set<Long> roster = Stream.concat(
                        Stream.of(PROTECTED),
                        positions.stream().map(FormationPosition::getSingerId))
                .collect(Collectors.toSet());
        return VisibilityEvaluator.evaluate(constraints, positions, roster);
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
