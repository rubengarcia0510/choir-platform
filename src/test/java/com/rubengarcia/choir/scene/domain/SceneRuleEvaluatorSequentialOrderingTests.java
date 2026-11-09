package com.rubengarcia.choir.scene.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ExpectedMovementStep;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.OrderingMode;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantRef;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantType;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Relation;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.SpatialConstraint;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Stage;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Evaluation;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Input;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Position;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.TemporalMovementStep;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneRuleEvaluatorSequentialOrderingTests {

    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    @Test
    void sequentialCastDivisionEvaluatesItsSpatialConstraintWithoutTemporalData() {
        Evaluation result = evaluate("CAST_DIVISION");

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.sceneRuleId()).isEqualTo(9L);
        assertThat(result.ruleRevisionId()).isEqualTo(99L);
        assertThat(result.ruleType()).isEqualTo("CAST_DIVISION");
    }

    @Test
    void completeValidSequenceIsSatisfiedWithoutUsingFormationPositions() {
        Evaluation result = evaluateMovement(List.of(
                movement(1, 1, "2026-01-01T10:00:00", "ENTER"),
                movement(2, 2, "2026-01-01T10:00:01", "TURN")));

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void completeSequenceWithWrongActionsIsViolated() {
        Evaluation result = evaluateMovement(List.of(
                movement(1, 1, "2026-01-01T10:00:00", "TURN"),
                movement(2, 2, "2026-01-01T10:00:01", "ENTER")));

        assertThat(result.status()).isEqualTo(Status.VIOLATED);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_SEQUENCE_VIOLATED");
    }

    @Test
    void missingAndIncompleteMovementStepsAreNotEvaluable() {
        Evaluation missing = evaluateMovement(List.of());
        Evaluation incomplete = evaluateMovement(List.of(
                movement(1, 1, "2026-01-01T10:00:00", "ENTER")));

        assertThat(missing.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(missing.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_STEPS_MISSING");
        assertThat(incomplete.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(incomplete.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_SEQUENCE_INCOMPLETE");
    }

    @Test
    void unknownParticipantsAndDuplicateStepsAreNotEvaluable() {
        Evaluation unknown = evaluateMovement(List.of(
                movement(1, 3, "2026-01-01T10:00:00", "ENTER"),
                movement(2, 2, "2026-01-01T10:00:01", "TURN")));
        Evaluation duplicate = evaluateMovement(List.of(
                movement(1, 1, "2026-01-01T10:00:00", "ENTER"),
                movement(1, 1, "2026-01-01T10:00:00", "ENTER"),
                movement(2, 2, "2026-01-01T10:00:01", "TURN")));

        assertThat(unknown.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(unknown.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_PARTICIPANT_UNKNOWN");
        assertThat(duplicate.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(duplicate.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_STEP_DUPLICATED");
    }

    @Test
    void ambiguousTimeAndForeignTransitionVersionsAreNotEvaluable() {
        Evaluation ambiguous = evaluateMovement(List.of(
                movement(1, 1, "2026-01-01T10:00:00", "ENTER"),
                movement(2, 2, "2026-01-01T10:00:00", "TURN")));
        Evaluation foreignVersion = evaluateMovement(List.of(
                new TemporalMovementStep(
                        300, 999, 100, 200, 1, 1,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"), "ENTER"),
                movement(2, 2, "2026-01-01T10:00:01", "TURN")));
        Evaluation foreignTransition = evaluateMovement(List.of(
                new TemporalMovementStep(
                        301, 300, 100, 200, 1, 1,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"), "ENTER"),
                movement(2, 2, "2026-01-01T10:00:01", "TURN")));
        Evaluation foreignFormationSnapshot = evaluateMovement(List.of(
                new TemporalMovementStep(
                        300, 400, 999, 200, 1, 1,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"), "ENTER"),
                movement(2, 2, "2026-01-01T10:00:01", "TURN")));

        assertThat(ambiguous.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(ambiguous.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_ORDER_AMBIGUOUS");
        assertThat(foreignVersion.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(foreignVersion.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_VERSION_MISMATCH");
        assertThat(foreignTransition.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(foreignTransition.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_VERSION_MISMATCH");
        assertThat(foreignFormationSnapshot.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(foreignFormationSnapshot.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("TEMPORAL_MOVEMENT_VERSION_MISMATCH");
    }

    @Test
    void formationPositionsDoNotSubstituteForMissingTemporalSteps() {
        Evaluation result = evaluate("MOVEMENT");

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .contains("EXPECTED_MOVEMENT_SEQUENCE_MISSING");
    }

    private Evaluation evaluate(String ruleType) {
        ParticipantRef first = singer(1);
        ParticipantRef second = singer(2);

        SpatialConstraint near = new SpatialConstraint(
                Relation.NEAR,
                Stage.TO,
                List.of(first, second),
                null,
                new BigDecimal("0.25"),
                null);

        SceneRuleRevisionSnapshot revision = new SceneRuleRevisionSnapshot(
                99,
                9,
                3,
                5,
                0,
                ruleType,
                "Regla de prueba",
                true,
                "READY",
                "UPDATE",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                List.of(),
                new SceneRuleCondition(
                        OrderingMode.SEQUENTIAL,
                        List.of(near),
                        null));

        return evaluator.evaluate(new Input(
                revision,
                100,
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                200,
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                null,
                null));
    }

    private Evaluation evaluateMovement(List<TemporalMovementStep> steps) {
        SceneRuleRevisionSnapshot revision = revision(
                "MOVEMENT",
                new SceneRuleCondition(
                        OrderingMode.SEQUENTIAL,
                        null,
                        null,
                        List.of(
                                new ExpectedMovementStep(1, List.of(singer(1)), "ENTER"),
                                new ExpectedMovementStep(2, List.of(singer(2)), "TURN"))));
        return evaluator.evaluate(new Input(
                revision,
                100,
                null,
                200,
                null,
                null,
                null,
                300,
                400,
                steps));
    }

    private SceneRuleRevisionSnapshot revision(
            String ruleType, SceneRuleCondition condition) {
        return new SceneRuleRevisionSnapshot(
                99,
                9,
                3,
                5,
                0,
                ruleType,
                "Regla de prueba",
                true,
                "READY",
                "UPDATE",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                List.of(),
                condition);
    }

    private TemporalMovementStep movement(
            int sequenceNumber,
            long singerId,
            String temporalReference,
            String action) {
        return new TemporalMovementStep(
                300,
                400,
                100,
                200,
                singerId,
                sequenceNumber,
                OffsetDateTime.parse(temporalReference + "Z"),
                action);
    }

    private ParticipantRef singer(long id) {
        return new ParticipantRef(ParticipantType.SINGER, id, null);
    }

    private Position position(long singerId, String x, String y) {
        return new Position(
                singerId,
                new BigDecimal(x),
                new BigDecimal(y));
    }

    private List<Position> positions(Position... positions) {
        return List.of(positions);
    }
}
