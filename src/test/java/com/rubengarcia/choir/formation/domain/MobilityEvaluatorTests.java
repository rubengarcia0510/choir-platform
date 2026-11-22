package com.rubengarcia.choir.formation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MobilityEvaluatorTests {

    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1.000"), new BigDecimal("8.000"),
            new BigDecimal("0.000"), new BigDecimal("10.000"));

    private static final long SINGER = 10L;

    @Test
    void positionsExactlyOnEachBoundaryAreAllowed() {
        assertNoFinding(position(1L, SINGER, "1.000", "0.000"));
        assertNoFinding(position(2L, SINGER, "8.000", "10.000"));
        assertNoFinding(position(3L, SINGER, "1.000", "10.000"));
        assertNoFinding(position(4L, SINGER, "8.000", "0.000"));
    }

    @Test
    void positionInsideTheRegionIsAllowed() {
        assertNoFinding(position(1L, SINGER, "4.500", "5.000"));
    }

    @Test
    void xBelowMinIsAFinding() {
        assertSingleBound(position(1L, SINGER, "0.999", "5.000"), MobilityBound.X_BELOW_MIN);
    }

    @Test
    void xAboveMaxIsAFinding() {
        assertSingleBound(position(1L, SINGER, "8.001", "5.000"), MobilityBound.X_ABOVE_MAX);
    }

    @Test
    void yBelowMinIsAFinding() {
        assertSingleBound(position(1L, SINGER, "4.000", "-0.001"), MobilityBound.Y_BELOW_MIN);
    }

    @Test
    void yAboveMaxIsAFinding() {
        assertSingleBound(position(1L, SINGER, "4.000", "10.001"), MobilityBound.Y_ABOVE_MAX);
    }

    @Test
    void everyViolatedBoundIsReportedInOneFinding() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)),
                List.of(position(7L, SINGER, "9.000", "-1.000")));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).hasSize(1);
        assertThat(evaluation.findings().get(0).violatedBounds())
                .containsExactlyInAnyOrder(MobilityBound.X_ABOVE_MAX, MobilityBound.Y_BELOW_MIN);
    }

    @Test
    void inactiveConstraintsDoNotProduceFindingsOrNotEvaluableEntries() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, false)),
                List.of(position(7L, SINGER, "99.000", "99.000")));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).isEmpty();
        assertThat(evaluation.notEvaluable()).isEmpty();
    }

    @Test
    void positionOfSingerWithoutConstraintIsIgnored() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)),
                List.of(position(7L, 99L, "99.000", "99.000"),
                        position(8L, SINGER, "4.000", "5.000")));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).isEmpty();
        assertThat(evaluation.notEvaluable()).isEmpty();
    }

    @Test
    void constraintWithoutDraftPositionReportsMissingDraftPosition() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, 99L, true)),
                List.of(position(7L, SINGER, "4.000", "5.000")));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(evaluation.findings()).isEmpty();
        assertThat(evaluation.notEvaluable()).singleElement()
                .extracting(MobilityNotEvaluable::reason)
                .isEqualTo(MobilityNotEvaluableReason.MISSING_DRAFT_POSITION);
    }

    @Test
    void severalConstraintsInTheSameFormationAreEvaluatedIndependently() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, 10L, true), constraint(2L, 20L, true)),
                List.of(
                        position(100L, 10L, "0.000", "5.000"),
                        position(200L, 20L, "4.000", "5.000")));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.constraintId()).isEqualTo(1L);
                    assertThat(finding.positionId()).isEqualTo(100L);
                });
    }

    @Test
    void emptyFindingsWithNotEvaluableEntriesIsNotASatisfactoryEvaluation() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)), List.of());

        assertThat(evaluation.findings()).isEmpty();
        assertThat(evaluation.status()).isNotEqualTo(MobilityEvaluationStatus.EVALUABLE);
    }

    @Test
    void duplicateDraftPositionsForOneSingerAreNotEvaluable() {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)),
                List.of(position(1L, SINGER, "4.000", "5.000"),
                        position(2L, SINGER, "4.000", "5.000")));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(evaluation.notEvaluable()).singleElement()
                .extracting(MobilityNotEvaluable::reason)
                .isEqualTo(MobilityNotEvaluableReason.AMBIGUOUS_DRAFT_POSITION);
    }

    @Test
    void missingCoordinateIsNotEvaluable() {
        FormationPosition position = position(1L, SINGER, "4.000", "5.000");
        position.setXCoordinate(null);

        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)), List.of(position));

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(evaluation.findings()).isEmpty();
        assertThat(evaluation.notEvaluable()).singleElement()
                .extracting(MobilityNotEvaluable::reason)
                .isEqualTo(MobilityNotEvaluableReason.MISSING_COORDINATES);
    }

    @Test
    void noConstraintsIsFullyEvaluableWithoutFindings() {
        MobilityEvaluation evaluation = evaluate(List.of(), List.of());

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).isEmpty();
    }

    @Test
    void evaluationDoesNotModifyPositionsOrConstraints() {
        FormationPosition position = position(1L, SINGER, "9.000", "5.000");
        FormationMobilityConstraint constraint = constraint(1L, SINGER, true);

        evaluate(List.of(constraint), List.of(position));

        assertThat(position.getXCoordinate()).isEqualByComparingTo("9.000");
        assertThat(position.getYCoordinate()).isEqualByComparingTo("5.000");
        assertThat(constraint.active()).isTrue();
        assertThat(constraint.region()).isEqualTo(REGION);
    }

    private static void assertNoFinding(FormationPosition position) {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)), List.of(position));
        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).isEmpty();
    }

    private static void assertSingleBound(FormationPosition position, MobilityBound expected) {
        MobilityEvaluation evaluation = evaluate(
                List.of(constraint(1L, SINGER, true)), List.of(position));
        assertThat(evaluation.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.violatedBounds())
                        .containsExactly(expected));
    }

    private static MobilityEvaluation evaluate(
            List<FormationMobilityConstraint> constraints,
            List<FormationPosition> positions) {
        return MobilityEvaluator.evaluate(constraints, positions);
    }

    private static FormationMobilityConstraint constraint(long id, long singerId, boolean active) {
        return new FormationMobilityConstraint(
                id, 1L, singerId, REGION, active,
                LocalDateTime.of(2026, 11, 22, 10, 0));
    }

    private static FormationPosition position(
            long id, long singerId, String x, String y) {
        FormationPosition position = new FormationPosition();
        position.setId(id);
        position.setSingerId(singerId);
        position.setXCoordinate(new BigDecimal(x));
        position.setYCoordinate(new BigDecimal(y));
        return position;
    }
}
