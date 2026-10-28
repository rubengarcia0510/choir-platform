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
import java.util.Map;
import org.junit.jupiter.api.Test;

class SceneRuleEvaluatorCountGroupTests {

    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    @Test
    void evaluatesTwoCountGroupsWithFourExplicitMembersEach() {
        Input input = input(
                List.of(
                        groupSnapshot(0, "COUNT", 4, List.of()),
                        groupSnapshot(1, "COUNT", 4, List.of())),
                Map.of(
                        0, List.of(1L, 2L, 3L, 4L),
                        1, List.of(5L, 6L, 7L, 8L)),
                positions());

        Evaluation result = evaluator.evaluate(input);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void incompleteCountAssignmentRemainsNotEvaluableWithActionableWarning() {
        Input input = input(
                List.of(
                        groupSnapshot(0, "COUNT", 4, List.of()),
                        groupSnapshot(1, "COUNT", 4, List.of())),
                Map.of(
                        0, List.of(1L, 2L, 3L, 4L),
                        1, List.of(5L, 6L, 7L)),
                positions());

        Evaluation result = evaluator.evaluate(input);

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("COUNT_GROUP_ASSIGNMENT_UNAVAILABLE");
    }

    @Test
    void sameSnapshotsRevisionAndAssignmentsProduceSameEvaluation() {
        Input input = input(
                List.of(
                        groupSnapshot(0, "COUNT", 4, List.of()),
                        groupSnapshot(1, "COUNT", 4, List.of())),
                Map.of(
                        0, List.of(1L, 2L, 3L, 4L),
                        1, List.of(5L, 6L, 7L, 8L)),
                positions());

        Evaluation first = evaluator.evaluate(input);
        Evaluation second = evaluator.evaluate(input);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void identifiedGroupsContinueUsingTheirRevisionMembersWithoutCountAssignments() {
        Input input = input(
                List.of(
                        groupSnapshot(0, "IDENTIFIED", 4,
                                List.of(1L, 2L, 3L, 4L)),
                        groupSnapshot(1, "IDENTIFIED", 4,
                                List.of(5L, 6L, 7L, 8L))),
                null,
                positions());

        Evaluation result = evaluator.evaluate(input);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
    }

    private Input input(
            List<SceneRuleRevisionSnapshot.GroupSnapshot> groups,
            Map<Integer, List<Long>> assignments,
            List<Position> positions) {
        SpatialConstraint separated = new SpatialConstraint(
                Relation.SEPARATED,
                Stage.TO,
                List.of(group(0)),
                List.of(group(1)),
                new BigDecimal("0.10"),
                null);

        SceneRuleRevisionSnapshot revision = new SceneRuleRevisionSnapshot(
                99L,
                9L,
                3,
                5L,
                0,
                "CAST_DIVISION",
                "Separación entre dos grupos",
                true,
                "READY",
                "UPDATE",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                groups,
                new SceneRuleCondition(
                        SceneRuleCondition.OrderingMode.SIMULTANEOUS,
                        List.of(separated),
                        null));

        return new Input(
                revision,
                100L,
                positions,
                200L,
                positions,
                assignments,
                new BigDecimal("0.05"));
    }

    private List<Position> positions() {
        return List.of(
                position(1, "0.10", "0.10"),
                position(2, "0.15", "0.10"),
                position(3, "0.20", "0.10"),
                position(4, "0.25", "0.10"),
                position(5, "0.75", "0.10"),
                position(6, "0.80", "0.10"),
                position(7, "0.85", "0.10"),
                position(8, "0.90", "0.10"));
    }

    private SceneRuleRevisionSnapshot.GroupSnapshot groupSnapshot(
            int order, String mode, int memberCount, List<Long> singerIds) {
        return new SceneRuleRevisionSnapshot.GroupSnapshot(
                order, "Grupo " + order, memberCount, mode, singerIds);
    }

    private ParticipantRef group(int order) {
        return new ParticipantRef(ParticipantType.GROUP, null, order);
    }

    private Position position(long singerId, String x, String y) {
        return new Position(
                singerId, new BigDecimal(x), new BigDecimal(y));
    }
}
