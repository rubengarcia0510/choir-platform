package com.rubengarcia.choir.scene.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Direction;
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
import java.util.Map;
import org.junit.jupiter.api.Test;

class SceneRuleEvaluatorTests {

    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    @Test
    void evaluatesTogetherAsAConnectedNetworkRatherThanAllPairs() {
        SpatialConstraint together = constraint(
                Relation.TOGETHER,
                Stage.TO,
                List.of(singer(1), singer(2), singer(3)),
                null,
                "0.11",
                null);

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(together), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10"),
                        position(3, "0.30", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
    }

    @Test
    void togetherConnectsParticipantsAtExactThreshold() {
        SpatialConstraint together = constraint(
                Relation.TOGETHER,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.25",
                null);

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(together), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.35", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
    }

    @Test
    void nearAtExactMaximumThresholdIsSatisfied() {
        SpatialConstraint near = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.25",
                null);

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(near), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.35", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void evaluatesRelativeOrderOnHorizontalAndVerticalAxes() {
        SpatialConstraint left = constraint(
                Relation.RELATIVE_ORDER, Stage.TO, List.of(singer(1), singer(2)),
                null, null, Direction.LEFT);
        SpatialConstraint front = constraint(
                Relation.RELATIVE_ORDER, Stage.TO, List.of(singer(1), singer(2)),
                null, null, Direction.FRONT);
        SpatialConstraint back = constraint(
                Relation.RELATIVE_ORDER, Stage.TO, List.of(singer(1), singer(2)),
                null, null, Direction.BACK);

        List<Position> firstInFront = positions(
                position(1, "0.20", "0.70"), position(2, "0.80", "0.20"));
        List<Position> firstBehind = positions(
                position(1, "0.20", "0.20"), position(2, "0.80", "0.70"));
        List<Position> verticallyAligned = positions(
                position(1, "0.20", "0.50"), position(2, "0.80", "0.50"));

        assertThat(evaluate("CAST_DIVISION", condition(List.of(left), null),
                firstInFront, null, null, null).status()).isEqualTo(Status.SATISFIED);
        assertThat(evaluate("CAST_DIVISION", condition(List.of(front), null),
                firstInFront, null, null, null).status()).isEqualTo(Status.SATISFIED);
        assertThat(evaluate("CAST_DIVISION", condition(List.of(back), null),
                firstInFront, null, null, null).status()).isEqualTo(Status.VIOLATED);
        assertThat(evaluate("CAST_DIVISION", condition(List.of(back), null),
                firstBehind, null, null, null).status()).isEqualTo(Status.SATISFIED);
        assertThat(evaluate("CAST_DIVISION", condition(List.of(front), null),
                firstBehind, null, null, null).status()).isEqualTo(Status.VIOLATED);

        Evaluation aligned = evaluate(
                "CAST_DIVISION", condition(List.of(front), null),
                verticallyAligned, null, null, null);
        assertThat(aligned.status()).isEqualTo(Status.VIOLATED);
        assertThat(aligned.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("RELATIVE_ORDER_ALIGNED_VERTICAL");
    }

    @Test
    void relativeOrderUsesTheGeometricCenterOfIdentifiedGroups() {
        SpatialConstraint relativeOrder = constraint(
                Relation.RELATIVE_ORDER,
                Stage.TO,
                List.of(group(0), group(1)),
                null,
                null,
                Direction.LEFT);
        List<SceneRuleRevisionSnapshot.GroupSnapshot> groups = List.of(
                groupSnapshot(0, "IDENTIFIED", 2, List.of(1L, 2L)),
                groupSnapshot(1, "IDENTIFIED", 1, List.of(3L)));

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(relativeOrder), null),
                positions(position(1, "0.10", "0.20"),
                        position(2, "0.30", "0.20"),
                        position(3, "0.80", "0.20")),
                groups,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
    }

    @Test
    void separatedUsesEveryCrossPairAndTheLargerThreshold() {
        SpatialConstraint separated = constraint(
                Relation.SEPARATED,
                Stage.TO,
                List.of(group(0)),
                List.of(group(1)),
                "0.20",
                null);
        List<SceneRuleRevisionSnapshot.GroupSnapshot> groups = List.of(
                groupSnapshot(0, "IDENTIFIED", 2, List.of(1L, 2L)),
                groupSnapshot(1, "IDENTIFIED", 1, List.of(3L)));

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(separated), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.40", "0.10"),
                        position(3, "0.60", "0.10")),
                groups,
                null,
                decimal("0.25"));

        assertThat(result.status()).isEqualTo(Status.VIOLATED);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("SEPARATION_MINIMUM_NOT_MET");
    }

    @Test
    void separatedAcceptsPairsAtExactEffectiveMinimum() {
        SpatialConstraint separated = constraint(
                Relation.SEPARATED,
                Stage.TO,
                List.of(singer(1)),
                List.of(singer(3)),
                "0.20",
                null);

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(separated), null),
                positions(position(1, "0.10", "0.10"),
                        position(3, "0.35", "0.10")),
                null,
                null,
                decimal("0.25"));

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
    }

    @Test
    void countGroupsNeedAnExplicitCompletePlanningAssignment() {
        SpatialConstraint separated = constraint(
                Relation.SEPARATED,
                Stage.TO,
                List.of(group(0)),
                List.of(singer(3)),
                "0.10",
                null);
        List<SceneRuleRevisionSnapshot.GroupSnapshot> groups = List.of(
                groupSnapshot(0, "COUNT", 2, List.of()));

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(separated), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.15", "0.10"),
                        position(3, "0.80", "0.10")),
                groups,
                null,
                decimal("0.05"));

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("COUNT_GROUP_ASSIGNMENT_UNAVAILABLE");
    }

    @Test
    void evaluatesCountGroupsWhenThePlanProvidesEveryMemberExplicitly() {
        SpatialConstraint separated = constraint(
                Relation.SEPARATED,
                Stage.TO,
                List.of(group(0)),
                List.of(singer(3)),
                "0.10",
                null);
        List<SceneRuleRevisionSnapshot.GroupSnapshot> groups = List.of(
                groupSnapshot(0, "COUNT", 2, List.of()));

        Evaluation result = evaluate(
                "CAST_DIVISION",
                condition(List.of(separated), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10"),
                        position(3, "0.80", "0.10")),
                groups,
                Map.of(0, List.of(1L, 2L)),
                decimal("0.05"));

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
    }

    @Test
    void exitRequiresExplicitParticipantsAndEvaluatesTheEffectiveDeparture() {
        Evaluation incomplete = evaluate(
                "EXIT",
                condition(null, null),
                positions(position(1, "0.10", "0.10")),
                null,
                null,
                null);

        assertThat(incomplete.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(incomplete.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_CONDITION_INCOMPLETE");

        SpatialConstraint violatedSpatialConstraint = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.05",
                null);

        Evaluation departed = evaluate(
                "EXIT",
                condition(List.of(violatedSpatialConstraint), List.of(singer(7))),
                positions(
                        position(1, "0.10", "0.10"),
                        position(2, "0.80", "0.10")),
                null,
                null,
                null);

        assertThat(departed.status()).isEqualTo(Status.SATISFIED);
        assertThat(departed.code()).isEqualTo("RULE_SATISFIED");
        assertThat(departed.sceneRuleId()).isEqualTo(9L);
        assertThat(departed.ruleRevisionId()).isEqualTo(99L);
        assertThat(departed.ruleType()).isEqualTo("EXIT");

        Evaluation stillPresent = evaluate(
                "EXIT",
                condition(null, List.of(singer(7))),
                positions(
                        position(1, "0.10", "0.10"),
                        position(7, "0.10", "0.20")),
                null,
                null,
                null);

        assertThat(stillPresent.status()).isEqualTo(Status.VIOLATED);
        assertThat(stillPresent.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("EXIT_PARTICIPANT_PRESENT_TO");

        Evaluation missingCondition = evaluate(
                "EXIT",
                null,
                List.of(),
                null,
                null,
                null);

        assertThat(missingCondition.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(missingCondition.code()).isEqualTo("STRUCTURED_CONDITION_MISSING");
    }

    @Test
    void sequentialMovementIsNotEvaluableWithoutTemporalData() {
        SpatialConstraint near = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.25",
                null);
        SceneRuleCondition sequential = new SceneRuleCondition(
                OrderingMode.SEQUENTIAL, List.of(near), null);

        Evaluation result = evaluate(
                "MOVEMENT",
                sequential,
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("TEMPORAL_SEQUENCE_UNAVAILABLE");
    }

    @Test
    void movementMustDeclareCriteriaForBothExactSnapshots() {
        SpatialConstraint onlyTo = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.40",
                null);
        Evaluation result = evaluate(
                "MOVEMENT",
                condition(List.of(onlyTo), null),
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("MOVEMENT_SNAPSHOTS_INCOMPLETE");
    }

    @Test
    void evaluatesSimultaneousRulesWithoutRequiringTemporalSequence() {
        SpatialConstraint near = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.25",
                null);
        SceneRuleCondition simultaneous = new SceneRuleCondition(
                OrderingMode.SIMULTANEOUS, List.of(near), null);

        Evaluation result = evaluate(
                "CAST_DIVISION",
                simultaneous,
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
    }


    @Test
    void draftRulePreservesRevisionIdentityWhenNotEvaluable() {
        SceneRuleRevisionSnapshot draftRevision = new SceneRuleRevisionSnapshot(
                99,
                9,
                3,
                5,
                0,
                "CAST_DIVISION",
                "texto informativo que no debe evaluarse",
                true,
                "DRAFT",
                "UPDATE",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                List.of(),
                condition(List.of(), null));

        Evaluation result = evaluator.evaluate(new Input(
                draftRevision,
                100,
                positions(position(1, "0.10", "0.10")),
                200,
                positions(position(1, "0.10", "0.10")),
                null,
                null));

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.code()).isEqualTo("RULE_DEFINITION_DRAFT");
        assertThat(result.sceneRuleId()).isEqualTo(9L);
        assertThat(result.ruleRevisionId()).isEqualTo(99L);
        assertThat(result.ruleType()).isEqualTo("CAST_DIVISION");
    }


    @Test
    void inactiveRuleIsNotEvaluatedEvenWhenSpatialConditionIsSatisfied() {
        SpatialConstraint near = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.25",
                null);
        Evaluation result = evaluator.evaluate(new Input(
                revision("CAST_DIVISION", condition(List.of(near), null), null, false),
                100,
                positions(position(1, "0.10", "0.10"), position(2, "0.20", "0.10")),
                200,
                positions(position(1, "0.10", "0.10"), position(2, "0.20", "0.10")),
                null,
                null));

        assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(result.code()).isEqualTo("RULE_INACTIVE");
        assertThat(result.sceneRuleId()).isEqualTo(9L);
        assertThat(result.ruleRevisionId()).isEqualTo(99L);
        assertThat(result.ruleType()).isEqualTo("CAST_DIVISION");
        assertThat(result.warnings())
                .extracting(SceneRuleEvaluator.Warning::code)
                .containsExactly("RULE_INACTIVE");
    }


    @Test
    void unsupportedAndNullRuleTypesAreNotEvaluated() {
        for (String ruleType : java.util.Arrays.asList("UNKNOWN", null)) {
            Evaluation result = evaluator.evaluate(new Input(
                    revision(ruleType, condition(List.of(constraint(
                            Relation.NEAR, Stage.TO, List.of(singer(1), singer(2)),
                            null, "0.25", null)), null), null),
                    100,
                    positions(position(1, "0.10", "0.10"), position(2, "0.20", "0.10")),
                    200,
                    positions(position(1, "0.10", "0.10"), position(2, "0.20", "0.10")),
                    null,
                    null));

            assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
            assertThat(result.code()).isEqualTo("RULE_TYPE_UNSUPPORTED");
            assertThat(result.sceneRuleId()).isEqualTo(9L);
            assertThat(result.ruleRevisionId()).isEqualTo(99L);
            assertThat(result.ruleType()).isEqualTo(ruleType);
            assertThat(result.warnings())
                    .extracting(SceneRuleEvaluator.Warning::code)
                    .containsExactly("RULE_TYPE_UNSUPPORTED");
        }
    }

    @Test
    void sequentialCastDivisionIsEvaluableWithoutTemporalData() {
        SpatialConstraint near = constraint(
                Relation.NEAR,
                Stage.TO,
                List.of(singer(1), singer(2)),
                null,
                "0.25",
                null);
        SceneRuleCondition sequential = new SceneRuleCondition(
                OrderingMode.SEQUENTIAL, List.of(near), null);

        Evaluation result = evaluate(
                "CAST_DIVISION",
                sequential,
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10")),
                null,
                null,
                null);

        assertThat(result.status()).isEqualTo(Status.SATISFIED);
        assertThat(result.code()).isEqualTo("RULE_SATISFIED");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.sceneRuleId()).isEqualTo(9L);
        assertThat(result.ruleRevisionId()).isEqualTo(99L);
        assertThat(result.ruleType()).isEqualTo("CAST_DIVISION");
    }

    private Evaluation evaluate(
            String ruleType,
            SceneRuleCondition condition,
            List<Position> toPositions,
            List<SceneRuleRevisionSnapshot.GroupSnapshot> groups,
            Map<Integer, List<Long>> resolvedGroups,
            BigDecimal globalThreshold) {
        return evaluator.evaluate(new Input(
                revision(ruleType, condition, groups),
                100,
                positions(position(1, "0.10", "0.10"),
                        position(2, "0.20", "0.10"),
                        position(3, "0.30", "0.10"),
                        position(7, "0.10", "0.20")),
                200,
                toPositions,
                resolvedGroups,
                globalThreshold));
    }

    private SceneRuleRevisionSnapshot revision(
            String ruleType,
            SceneRuleCondition condition,
            List<SceneRuleRevisionSnapshot.GroupSnapshot> groups) {
        return revision(ruleType, condition, groups, true);
    }

    private SceneRuleRevisionSnapshot revision(
            String ruleType,
            SceneRuleCondition condition,
            List<SceneRuleRevisionSnapshot.GroupSnapshot> groups,
            boolean active) {
        return new SceneRuleRevisionSnapshot(
                99,
                9,
                3,
                5,
                0,
                ruleType,
                "texto informativo que no debe evaluarse",
                active,
                "READY",
                "UPDATE",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                groups == null ? List.of() : groups,
                condition);
    }

    private SceneRuleCondition condition(
            List<SpatialConstraint> constraints,
            List<ParticipantRef> exitParticipants) {
        return new SceneRuleCondition(null, constraints, exitParticipants);
    }

    private SpatialConstraint constraint(
            Relation relation,
            Stage stage,
            List<ParticipantRef> participants,
            List<ParticipantRef> compared,
            String threshold,
            Direction direction) {
        return new SpatialConstraint(
                relation,
                stage,
                participants,
                compared,
                threshold == null ? null : decimal(threshold),
                direction);
    }

    private ParticipantRef singer(long id) {
        return new ParticipantRef(ParticipantType.SINGER, id, null);
    }

    private ParticipantRef group(int order) {
        return new ParticipantRef(ParticipantType.GROUP, null, order);
    }

    private SceneRuleRevisionSnapshot.GroupSnapshot groupSnapshot(
            int order, String mode, int memberCount, List<Long> singerIds) {
        return new SceneRuleRevisionSnapshot.GroupSnapshot(
                order, "Grupo " + order, memberCount, mode, singerIds);
    }

    private Position position(long singerId, String x, String y) {
        return new Position(singerId, decimal(x), decimal(y));
    }

    private List<Position> positions(Position... positions) {
        return List.of(positions);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
