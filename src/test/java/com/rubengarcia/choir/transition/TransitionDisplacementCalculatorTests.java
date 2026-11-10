package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.rubengarcia.choir.transition.domain.TransitionDisplacementCalculator;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.DataIssueCode;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.Stage;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitionDisplacementCalculatorTests {

    private final TransitionDisplacementCalculator calculator =
            new TransitionDisplacementCalculator();

    @Test
    void calculatesAndAggregatesDistancesForExactSnapshots() {
        TransitionDisplacementMetrics result = calculator.calculate(
                context(),
                List.of(
                        position("FROM", 10, 2L, "0", "0"),
                        position("FROM", 10, 1L, "0", "0"),
                        position("TO", 11, 2L, "0", "2"),
                        position("TO", 11, 1L, "3", "4")));

        assertThat(result.transitionVersionId()).isEqualTo(70);
        assertThat(result.fromFormationVersionId()).isEqualTo(10);
        assertThat(result.toFormationVersionId()).isEqualTo(11);
        assertThat(result.singerDistances())
                .extracting("singerId", "distance")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, distance("5.000000")),
                        org.assertj.core.groups.Tuple.tuple(2L, distance("2.000000")));
        assertThat(result.totalDistance()).isEqualTo(distance("7.000000"));
        assertThat(result.maxIndividualDistance()).isEqualTo(distance("5.000000"));
        assertThat(result.movingSingerCount()).isEqualTo(2);
        assertThat(result.dataIssues()).isEmpty();
    }

    @Test
    void normalizesEuclideanDistancesToSixDecimalPlaces() {
        TransitionDisplacementMetrics result = calculator.calculate(
                context(),
                List.of(
                        position("FROM", 10, 1L, "0", "0"),
                        position("FROM", 10, 2L, "0", "0"),
                        position("TO", 11, 1L, "1", "1"),
                        position("TO", 11, 2L, "0", "2")));

        assertThat(result.singerDistances())
                .extracting("distance")
                .containsExactly(distance("1.414214"), distance("2.000000"));
        assertThat(result.totalDistance()).isEqualTo(distance("3.414214"));
    }

    @Test
    void countsGeometricMovementIndependentlyOfDistanceRounding() {
        TransitionDisplacementMetrics result = calculator.calculate(
                context(),
                List.of(
                        position("FROM", 10, 1L, "0", "0"),
                        position("FROM", 10, 2L, "0", "0"),
                        position("FROM", 10, 3L, "0", "0"),
                        position("FROM", 10, 4L, "0", "0"),
                        position("TO", 11, 1L, "0", "0"),
                        position("TO", 11, 2L, "0.0000004", "0"),
                        position("TO", 11, 3L, "0.000001", "0"),
                        position("TO", 11, 4L, "0.0000014", "0")));

        assertThat(result.singerDistances())
                .extracting("singerId", "distance")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, distance("0.000000")),
                        org.assertj.core.groups.Tuple.tuple(2L, distance("0.000000")),
                        org.assertj.core.groups.Tuple.tuple(3L, distance("0.000001")),
                        org.assertj.core.groups.Tuple.tuple(4L, distance("0.000001")));
        assertThat(result.totalDistance()).isEqualTo(distance("0.000002"));
        assertThat(result.maxIndividualDistance()).isEqualTo(distance("0.000001"));
        assertThat(result.movingSingerCount()).isEqualTo(3);
    }

    @Test
    void reportsMissingDuplicateAndInvalidPositionsWithoutCalculatingThem() {
        TransitionDisplacementMetrics result = calculator.calculate(
                context(),
                List.of(
                        position("FROM", 10, 1L, "0", "0"),
                        position("TO", 11, 2L, "1", "0"),
                        position("FROM", 10, 3L, "0", "0"),
                        position("FROM", 10, 3L, "2", "0"),
                        position("TO", 11, 3L, "3", "0"),
                        position("FROM", 10, 4L, null, "0"),
                        position("TO", 11, 4L, "4", "0"),
                        position("FROM", 10, null, "0", "0")));

        assertThat(result.singerDistances()).isEmpty();
        assertThat(result.dataIssues())
                .extracting("code", "stage", "singerId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.MISSING_COUNTERPART, Stage.TO, 1L),
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.MISSING_COUNTERPART, Stage.FROM, 2L),
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.DUPLICATE_SINGER_POSITION, Stage.FROM, 3L),
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.INVALID_COORDINATE, Stage.FROM, 4L),
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.INVALID_SINGER_ID, Stage.FROM, null));
    }

    @Test
    void reportsMissingCounterpartAlongsideInvalidCoordinate() {
        TransitionDisplacementMetrics result = calculator.calculate(
                context(),
                List.of(position("FROM", 10, 6L, null, "0")));

        assertThat(result.dataIssues())
                .extracting("code", "stage", "singerId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.INVALID_COORDINATE, Stage.FROM, 6L),
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.MISSING_COUNTERPART, Stage.TO, 6L));
        assertThat(result.singerDistances()).isEmpty();
    }

    @Test
    void reportsMissingCounterpartAlongsideDuplicatePositions() {
        TransitionDisplacementMetrics result = calculator.calculate(
                context(),
                List.of(
                        position("FROM", 10, 7L, "0", "0"),
                        position("FROM", 10, 7L, "1", "0")));

        assertThat(result.dataIssues())
                .extracting("code", "stage", "singerId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.DUPLICATE_SINGER_POSITION, Stage.FROM, 7L),
                        org.assertj.core.groups.Tuple.tuple(
                                DataIssueCode.MISSING_COUNTERPART, Stage.TO, 7L));
        assertThat(result.singerDistances()).isEmpty();
    }

    @Test
    void preservesSnapshotInputsAndProducesIdenticalResultsForRepeatedCalls() {
        List<TransitionPlanningPosition> positions = new ArrayList<>(List.of(
                position("FROM", 10, 5L, "1", "2"),
                position("TO", 11, 5L, "4", "6")));

        TransitionDisplacementMetrics first =
                calculator.calculate(context(), positions);
        TransitionDisplacementMetrics second =
                calculator.calculate(context(), positions);

        assertThat(second).isEqualTo(first);
        assertThat(positions).hasSize(2);
    }

    @Test
    void rejectsPositionsFromVersionsOutsideTheTransitionContext() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        context(),
                        List.of(position("FROM", 99, 1L, "0", "0"))));
    }

    @Test
    void returnsNormalizedZeroMetricsForEmptySnapshots() {
        TransitionDisplacementMetrics result =
                calculator.calculate(context(), List.of());

        assertThat(result.totalDistance()).isEqualTo(distance("0.000000"));
        assertThat(result.maxIndividualDistance()).isEqualTo(distance("0.000000"));
        assertThat(result.movingSingerCount()).isZero();
    }

    private static TransitionPlanningContext context() {
        return new TransitionPlanningContext(70, 9, 10, 11, 5);
    }

    private static TransitionPlanningPosition position(
            String stage,
            long formationVersionId,
            Long singerId,
            String x,
            String y) {
        return new TransitionPlanningPosition(
                70,
                stage,
                formationVersionId,
                singerId,
                "Singer",
                "SOPRANO",
                null,
                null,
                null,
                null,
                coordinate(x),
                coordinate(y));
    }

    private static BigDecimal coordinate(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static BigDecimal distance(String value) {
        return new BigDecimal(value);
    }
}
