package com.rubengarcia.choir.formation.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de evaluar restricciones de visibilidad.
 * Invariante: EVALUABLE si y solo si no hay comparaciones no evaluables.
 */
public record VisibilityEvaluation(
        VisibilityEvaluationStatus status,
        List<VisibilityFinding> findings,
        List<VisibilityNotEvaluable> notEvaluable) {

    public VisibilityEvaluation {
        Objects.requireNonNull(status, "status es obligatorio");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings es obligatorio"));
        notEvaluable = List.copyOf(Objects.requireNonNull(notEvaluable, "notEvaluable es obligatorio"));
        VisibilityEvaluationStatus expected = notEvaluable.isEmpty()
                ? VisibilityEvaluationStatus.EVALUABLE
                : VisibilityEvaluationStatus.NOT_EVALUABLE;
        if (status != expected) {
            throw new IllegalArgumentException(
                    "El estado no coincide con las comparaciones no evaluables");
        }
    }

    public static VisibilityEvaluation of(
            List<VisibilityFinding> findings,
            List<VisibilityNotEvaluable> notEvaluable) {
        VisibilityEvaluationStatus status = notEvaluable.isEmpty()
                ? VisibilityEvaluationStatus.EVALUABLE
                : VisibilityEvaluationStatus.NOT_EVALUABLE;
        return new VisibilityEvaluation(status, findings, notEvaluable);
    }
}
