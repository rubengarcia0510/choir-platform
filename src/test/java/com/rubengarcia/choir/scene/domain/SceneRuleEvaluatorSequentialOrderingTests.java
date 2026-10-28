package com.rubengarcia.choir.scene.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    void sequentialMovementRemainsNotEvaluableWithoutTemporalData() {
        Evaluation result = evaluate("MOVEMENT");

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("TEMPORAL_SEQUENCE_UNAVAILABLE");
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
