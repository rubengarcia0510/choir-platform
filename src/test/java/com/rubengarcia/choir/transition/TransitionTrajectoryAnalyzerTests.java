package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.rubengarcia.choir.transition.domain.TransitionMovementStep;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.GeometryIssueCode;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TemporalConflictStatus;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TrajectoryFinding;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalyzer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitionTrajectoryAnalyzerTests {

    private static final OffsetDateTime FIRST_REFERENCE =
            OffsetDateTime.parse("2026-01-01T10:00:00Z");
    private static final OffsetDateTime SECOND_REFERENCE =
            OffsetDateTime.parse("2026-01-01T10:01:00Z");
    private final TransitionTrajectoryAnalyzer analyzer =
            new TransitionTrajectoryAnalyzer();

    @Test
    void detectsInteriorIntersectionAndConfirmsSameSequenceEvidence() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(step(1, 1, FIRST_REFERENCE), step(2, 1, FIRST_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.CONFIRMED));
        assertThat(result.geometryIssues()).isEmpty();
    }

    @Test
    void returnsNoFindingForSegmentsThatDoNotIntersect() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                positions(
                        point("FROM", 10, 1, "0", "0"),
                        point("TO", 11, 1, "2", "0"),
                        point("FROM", 10, 2, "0", "1"),
                        point("TO", 11, 2, "2", "1")),
                List.of());

        assertThat(result.crossings()).isEmpty();
    }

    @Test
    void includesSharedEndpointsAsGeometricCrossings() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                positions(
                        point("FROM", 10, 1, "0", "0"),
                        point("TO", 11, 1, "1", "1"),
                        point("FROM", 10, 2, "2", "0"),
                        point("TO", 11, 2, "1", "1")),
                List.of());

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void includesPartiallyCoincidentSegments() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                positions(
                        point("FROM", 10, 1, "0", "0"),
                        point("TO", 11, 1, "3", "0"),
                        point("FROM", 10, 2, "1", "0"),
                        point("TO", 11, 2, "4", "0")),
                List.of());

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void doesNotCountParallelDisjointSegmentsAsCrossing() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                positions(
                        point("FROM", 10, 1, "0", "0"),
                        point("TO", 11, 1, "3", "0"),
                        point("FROM", 10, 2, "0", "1"),
                        point("TO", 11, 2, "3", "1")),
                List.of());

        assertThat(result.crossings()).isEmpty();
    }

    @Test
    void ignoresStationaryParticipantsEvenWhenTheyLieOnAnotherPath() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                positions(
                        point("FROM", 10, 1, "0", "0"),
                        point("TO", 11, 1, "2", "0"),
                        point("FROM", 10, 2, "1", "0"),
                        point("TO", 11, 2, "1", "0")),
                List.of());

        assertThat(result.crossings()).isEmpty();
    }

    @Test
    void marksCrossingAsNotConcurrentWhenEvidenceUsesDifferentSequences() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(step(1, 1, FIRST_REFERENCE), step(2, 2, SECOND_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_CONCURRENT));
    }

    @Test
    void marksCrossingNotEvaluableWhenEitherParticipantHasNoEvidence() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(step(1, 1, FIRST_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void marksAllCrossingsNotEvaluableWhenSequenceEvidenceIsInconsistent() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(
                        step(1, 1, FIRST_REFERENCE),
                        step(2, 1, SECOND_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void marksCrossingsNotEvaluableForGappedSequenceNumbers() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(step(1, 1, FIRST_REFERENCE), step(2, 3, SECOND_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void marksCrossingsNotEvaluableWhenSequenceReferencesAreNotOrdered() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(
                        step(1, 1, SECOND_REFERENCE),
                        step(2, 2, FIRST_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void marksCrossingsNotEvaluableForDuplicateSingerInOneSequence() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(
                        step(1, 1, FIRST_REFERENCE),
                        step(1, 1, FIRST_REFERENCE),
                        step(2, 1, FIRST_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void rejectsEvidenceFromDifferentTransitionOrFormationVersions() {
        TransitionMovementStep mismatchedStep = new TransitionMovementStep(
                9, 70, 10, 99, 1, 1, FIRST_REFERENCE, "MOVE");

        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                crossingPositions(),
                List.of(mismatchedStep, step(2, 1, FIRST_REFERENCE)));

        assertThat(result.crossings()).containsExactly(finding(
                1, 2, TemporalConflictStatus.NOT_EVALUABLE));
    }

    @Test
    void reportsInvalidDuplicateAndMissingSnapshotPositions() {
        TransitionTrajectoryAnalysis result = analyzer.analyze(
                context(),
                positions(
                        point("FROM", 10, 1, null, "0"),
                        point("FROM", 10, 2, "0", "0"),
                        point("FROM", 10, 2, "1", "0"),
                        point("TO", 11, 3, "0", "0")),
                List.of());

        assertThat(result.geometryIssues())
                .extracting("code", "stage", "singerId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                GeometryIssueCode.INVALID_COORDINATE, "FROM", 1L),
                        org.assertj.core.groups.Tuple.tuple(
                                GeometryIssueCode.MISSING_COUNTERPART, "TO", 1L),
                        org.assertj.core.groups.Tuple.tuple(
                                GeometryIssueCode.DUPLICATE_SINGER_POSITION, "FROM", 2L),
                        org.assertj.core.groups.Tuple.tuple(
                                GeometryIssueCode.MISSING_COUNTERPART, "TO", 2L),
                        org.assertj.core.groups.Tuple.tuple(
                                GeometryIssueCode.MISSING_COUNTERPART, "FROM", 3L));
        assertThat(result.crossings()).isEmpty();
    }

    @Test
    void producesDeterministicResultsWithoutMutatingInputs() {
        List<TransitionPlanningPosition> positions =
                new ArrayList<>(crossingPositions());
        List<TransitionMovementStep> evidence = new ArrayList<>(
                List.of(step(1, 1, FIRST_REFERENCE), step(2, 1, FIRST_REFERENCE)));

        TransitionTrajectoryAnalysis first =
                analyzer.analyze(context(), positions, evidence);
        TransitionTrajectoryAnalysis second =
                analyzer.analyze(context(), positions, evidence);

        assertThat(second).isEqualTo(first);
        assertThat(positions).hasSize(4);
        assertThat(evidence).hasSize(2);
    }

    @Test
    void rejectsPositionsFromAnotherTransitionVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> analyzer.analyze(
                        context(),
                        List.of(point("FROM", 99, 1, "0", "0")),
                        List.of()));
    }

    private static TransitionTrajectoryAnalysis.TrajectoryFinding finding(
            long firstSingerId,
            long secondSingerId,
            TemporalConflictStatus status) {
        return new TrajectoryFinding(firstSingerId, secondSingerId, status);
    }

    private static TransitionPlanningContext context() {
        return new TransitionPlanningContext(70, 9, 10, 11, 5);
    }

    private static List<TransitionPlanningPosition> crossingPositions() {
        return positions(
                point("FROM", 10, 1, "0", "0"),
                point("TO", 11, 1, "2", "2"),
                point("FROM", 10, 2, "0", "2"),
                point("TO", 11, 2, "2", "0"));
    }

    private static List<TransitionPlanningPosition> positions(
            TransitionPlanningPosition... positions) {
        return List.of(positions);
    }

    private static TransitionPlanningPosition point(
            String stage,
            long formationVersionId,
            long singerId,
            String x,
            String y) {
        return new TransitionPlanningPosition(
                70,
                stage,
                formationVersionId,
                singerId,
                "Singer " + singerId,
                null,
                null,
                null,
                null,
                null,
                decimal(x),
                decimal(y));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static TransitionMovementStep step(
            long singerId,
            int sequenceNumber,
            OffsetDateTime reference) {
        return new TransitionMovementStep(
                9,
                70,
                10,
                11,
                singerId,
                sequenceNumber,
                reference,
                "MOVE");
    }
}
