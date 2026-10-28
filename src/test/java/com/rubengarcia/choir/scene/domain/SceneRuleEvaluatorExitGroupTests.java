package com.rubengarcia.choir.scene.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantRef;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantType;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Evaluation;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Input;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Position;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot.GroupSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SceneRuleEvaluatorExitGroupTests {

    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    @Test
    void confirmsExitForEveryMemberOfAnIdentifiedGroup() {
        Evaluation result = evaluate(
                List.of(groupSnapshot(0, "IDENTIFIED", 2, List.of(1L, 2L))),
                group(0),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10"),
                        position(3, "0.30", "0.10")),
                positions(position(3, "0.30", "0.10")),
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void violatesExitWhenOneIdentifiedGroupMemberRemainsInTo() {
        Evaluation result = evaluate(
                List.of(groupSnapshot(0, "IDENTIFIED", 2, List.of(1L, 2L))),
                group(0),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                positions(position(1, "0.10", "0.10")),
                null);

        assertThat(result.status()).isEqualTo(Status.VIOLATED);
        assertThat(result.code()).isEqualTo("RULE_VIOLATED");
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_PARTICIPANT_PRESENT_TO");
    }

    @Test
    void confirmsExitForCountGroupWithCompleteResolvedAssignment() {
        Evaluation result = evaluate(
                List.of(groupSnapshot(0, "COUNT", 2, List.of())),
                group(0),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                positions(),
                Map.of(0, List.of(1L, 2L)));

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
    }

    private Evaluation evaluate(
            List<GroupSnapshot> groups,
            ParticipantRef participant,
            List<Position> fromPositions,
            List<Position> toPositions,
            Map<Integer, List<Long>> resolvedGroupMembers) {
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
                groups,
                new SceneRuleCondition(null, null, List.of(participant)));

        return evaluator.evaluate(new Input(
                revision,
                100,
                fromPositions,
                200,
                toPositions,
                resolvedGroupMembers,
                null));
    }

    private GroupSnapshot groupSnapshot(
            int order, String mode, int memberCount, List<Long> singerIds) {
        return new GroupSnapshot(order, "Grupo " + order, memberCount, mode, singerIds);
    }

    private ParticipantRef group(int order) {
        return new ParticipantRef(ParticipantType.GROUP, null, order);
    }

    private Position position(long singerId, String x, String y) {
        return new Position(singerId, new BigDecimal(x), new BigDecimal(y));
    }

    private List<Position> positions(Position... positions) {
        return List.of(positions);
    }
}
