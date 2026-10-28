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

class SceneRuleEvaluatorExitValidationTests {

    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    @Test
    void rejectsAnInvalidIndividualParticipantReference() {
        Evaluation result = evaluate(
                List.of(),
                List.of(new ParticipantRef(ParticipantType.SINGER, null, null)),
                positions(position(1, "0.10", "0.10")),
                positions(),
                null);

        assertNotEvaluableWith(result, "PARTICIPANT_REFERENCE_INVALID");
    }

    @Test
    void rejectsDuplicateSingerReferences() {
        Evaluation result = evaluate(
                List.of(),
                List.of(singer(1), singer(1)),
                positions(position(1, "0.10", "0.10")),
                positions(),
                null);

        assertNotEvaluableWith(result, "PARTICIPANT_REFERENCE_INVALID");
    }

    @Test
    void rejectsAGroupMissingFromTheRevision() {
        Evaluation result = evaluate(
                List.of(),
                List.of(group(4)),
                positions(position(1, "0.10", "0.10")),
                positions(),
                null);

        assertNotEvaluableWith(result, "GROUP_REFERENCE_MISSING");
    }

    @Test
    void rejectsAnIncompleteCountGroupAssignment() {
        Evaluation result = evaluate(
                List.of(groupSnapshot(0, "COUNT", 2, List.of())),
                List.of(group(0)),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                positions(),
                Map.of(0, List.of(1L)));

        assertNotEvaluableWith(result, "COUNT_GROUP_ASSIGNMENT_UNAVAILABLE");
    }

    @Test
    void rejectsDuplicatePositionsInTheFromSnapshot() {
        Position duplicatedPosition = position(1, "0.10", "0.10");
        Evaluation result = evaluate(
                List.of(),
                List.of(singer(1)),
                positions(duplicatedPosition, duplicatedPosition),
                positions(),
                null);

        assertNotEvaluableWith(result, "FORMATION_POSITIONS_INVALID");
    }

    private Evaluation evaluate(
            List<GroupSnapshot> groups,
            List<ParticipantRef> participants,
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
                new SceneRuleCondition(null, null, participants));

        return evaluator.evaluate(new Input(
                revision,
                100,
                fromPositions,
                200,
                toPositions,
                resolvedGroupMembers,
                null));
    }

    private void assertNotEvaluableWith(Evaluation result, String expectedCode) {
        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.code()).isEqualTo("RULE_NOT_EVALUABLE");
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly(expectedCode);
    }

    private GroupSnapshot groupSnapshot(
            int order, String mode, int memberCount, List<Long> singerIds) {
        return new GroupSnapshot(order, "Grupo " + order, memberCount, mode, singerIds);
    }

    private ParticipantRef singer(long id) {
        return new ParticipantRef(ParticipantType.SINGER, id, null);
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
