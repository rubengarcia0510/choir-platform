package com.rubengarcia.choir.transition.domain;

import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        List<Warning> warnings) {

    public TransitionRuleEvaluationSnapshot {
        results = List.copyOf(results);
        warnings = List.copyOf(warnings);
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
            Status status) {
    }
}
