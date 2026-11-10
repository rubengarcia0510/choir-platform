package com.rubengarcia.choir.transition.domain;

import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record TransitionRuleEvaluationSnapshot(
        Long id,
        long transitionVersionId,
        long transitionId,
        long fromFormationVersionId,
        long toFormationVersionId,
        long repertoireItemId,
        long separationSettingsRevision,
        BigDecimal globalSeparationThreshold,
        Status status,
        LocalDateTime evaluatedAt,
        List<RuleResult> results,
        List<Warning> warnings,
        List<TransitionMovementStep> movementSteps,
        TransitionDisplacementMetrics displacementMetrics,
        TransitionTrajectoryAnalysis trajectoryAnalysis) {

    public TransitionRuleEvaluationSnapshot {
        results = List.copyOf(results);
        warnings = List.copyOf(warnings);
        movementSteps = List.copyOf(movementSteps);
    }

    public TransitionRuleEvaluationSnapshot(
            Long id,
            long transitionVersionId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            long repertoireItemId,
            long separationSettingsRevision,
            BigDecimal globalSeparationThreshold,
            Status status,
            LocalDateTime evaluatedAt,
            List<RuleResult> results,
            List<Warning> warnings) {
        this(
                id,
                transitionVersionId,
                transitionId,
                fromFormationVersionId,
                toFormationVersionId,
                repertoireItemId,
                separationSettingsRevision,
                globalSeparationThreshold,
                status,
                evaluatedAt,
                results,
                warnings,
                List.of(),
                null,
                null);
    }

    public TransitionRuleEvaluationSnapshot(
            Long id,
            long transitionVersionId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            long repertoireItemId,
            long separationSettingsRevision,
            BigDecimal globalSeparationThreshold,
            Status status,
            LocalDateTime evaluatedAt,
            List<RuleResult> results,
            List<Warning> warnings,
            List<TransitionMovementStep> movementSteps) {
        this(
                id,
                transitionVersionId,
                transitionId,
                fromFormationVersionId,
                toFormationVersionId,
                repertoireItemId,
                separationSettingsRevision,
                globalSeparationThreshold,
                status,
                evaluatedAt,
                results,
                warnings,
                movementSteps,
                null,
                null);
    }

    public record RuleResult(
            int order,
            long sceneRuleId,
            long ruleRevisionId,
            String ruleType,
            Status status,
            String code,
            String explanation,
            List<Warning> warnings) {

        public RuleResult {
            warnings = List.copyOf(warnings);
        }
    }

    public record Warning(
            Integer resultOrder,
            int warningOrder,
            String code,
            String explanation,
            Status status,
            PublicationImpact publicationImpact) {

        private static final String DIRECTOR_VISIBILITY_WARNING =
                "DIRECTOR_VISIBILITY_NOT_EVALUABLE";

        public Warning {
            Objects.requireNonNull(publicationImpact, "publicationImpact");

            if (publicationImpact == PublicationImpact.INFORMATIONAL
                    && (!DIRECTOR_VISIBILITY_WARNING.equals(code)
                    || status != Status.NOT_EVALUABLE)) {
                throw new IllegalArgumentException(
                        "INFORMATIONAL solo es válido para "
                                + DIRECTOR_VISIBILITY_WARNING
                                + " con estado NOT_EVALUABLE");
            }
        }

        public Warning(
                Integer resultOrder,
                int warningOrder,
                String code,
                String explanation,
                Status status) {
            this(
                    resultOrder,
                    warningOrder,
                    code,
                    explanation,
                    status,
                    PublicationImpact.BLOCKING);
        }

        public enum PublicationImpact {
            BLOCKING,
            INFORMATIONAL
        }
    }
}
