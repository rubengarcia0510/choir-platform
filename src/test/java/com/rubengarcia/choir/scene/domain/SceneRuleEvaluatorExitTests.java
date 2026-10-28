package com.rubengarcia.choir.scene.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantRef;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantType;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Relation;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.SpatialConstraint;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Stage;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Evaluation;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Input;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Position;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneRuleEvaluatorExitTests {

    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    @Test
    void confirmsExitWhenSelectedSingerWasInFromAndIsAbsentFromTo() {
        Evaluation result = evaluate(
                condition(null, List.of(singer(7))),
                positions(position(1, "0.10", "0.10"),
                        position(7, "0.10", "0.20")),
                positions(position(1, "0.10", "0.10")));

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.sceneRuleId()).isEqualTo(9L);
        assertThat(result.ruleRevisionId()).isEqualTo(99L);
        assertThat(result.ruleType()).isEqualTo("EXIT");
    }

    @Test
    void violatesExitWhenSelectedSingerRemainsInTo() {
        Evaluation result = evaluate(
                condition(null, List.of(singer(7))),
                positions(position(1, "0.10", "0.10"),
                        position(7, "0.10", "0.20")),
                positions(position(1, "0.10", "0.10"),
                        position(7, "0.10", "0.20")));

        assertThat(result.status()).isEqualTo(Status.VIOLATED);
        assertThat(result.code()).isEqualTo("RULE_VIOLATED");
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_PARTICIPANT_PRESENT_TO");
    }

    @Test
    void isNotEvaluableWhenSelectedSingerWasNotPresentInFrom() {
        Evaluation result = evaluate(
                condition(null, List.of(singer(8))),
                positions(position(1, "0.10", "0.10")),
                positions(position(1, "0.10", "0.10")));

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.code()).isEqualTo("RULE_NOT_EVALUABLE");
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_PARTICIPANT_MISSING_FROM");
    }

    @Test
    void requiresAnExplicitNonEmptyExitParticipantList() {
        Evaluation missingList = evaluate(
                condition(null, null),
                positions(position(1, "0.10", "0.10")),
                positions(position(1, "0.10", "0.10")));

        Evaluation emptyList = evaluate(
                condition(null, List.of()),
                positions(position(1, "0.10", "0.10")),
                positions(position(1, "0.10", "0.10")));

        assertThat(missingList.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(missingList.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_CONDITION_INCOMPLETE");

        assertThat(emptyList.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(emptyList.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_CONDITION_INCOMPLETE");
    }

    @Test
    void ignoresSpatialConstraintsWhenEvaluatingExit() {
        SpatialConstraint unrelatedConstraint = new SpatialConstraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                new BigDecimal("0.01"),
                null);

        Evaluation result = evaluate(
                condition(List.of(unrelatedConstraint), List.of(singer(7))),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.80", "0.10"),
                        position(7, "0.10", "0.20")),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.80", "0.10")));

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
    }

    @Test
    void rejectsMissingStructuredCondition() {
        Evaluation result = evaluate(
                null,
                positions(position(1, "0.10", "0.10")),
                positions(position(1, "0.10", "0.10")));

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.code()).isEqualTo("STRUCTURED_CONDITION_MISSING");
    }

    private Evaluation evaluate(
            SceneRuleCondition condition,
            List<Position> fromPositions,
            List<Position> toPositions) {
        SceneRuleRevisionSnapshot revision = new SceneRuleRevisionSnapshot(
                99,
                9,
                3,
                5,
                0,
                "EXIT",
                "texto informativo que no debe evaluarse",
                true,
                "READY",
                "UPDATE",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                List.of(),
                condition);

        return evaluator.evaluate(new Input(
                revision,
                100,
                fromPositions,
                200,
                toPositions,
                null,
                null));
    }

    private SceneRuleCondition condition(
            List<SpatialConstraint> constraints,
            List<ParticipantRef> exitParticipants) {
        return new SceneRuleCondition(null, constraints, exitParticipants);
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
