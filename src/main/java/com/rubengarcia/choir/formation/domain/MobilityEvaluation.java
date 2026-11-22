package com.rubengarcia.choir.formation.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de evaluar restricciones de movilidad contra el borrador.
 * Invariante: el estado es EVALUABLE si y solo si no hay restricciones no evaluables.
 */
public record MobilityEvaluation(
        MobilityEvaluationStatus status,
        List<MobilityFinding> findings,
        List<MobilityNotEvaluable> notEvaluable) {

    public MobilityEvaluation {
        Objects.requireNonNull(status, "status es obligatorio");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings es obligatorio"));
        notEvaluable = List.copyOf(Objects.requireNonNull(notEvaluable, "notEvaluable es obligatorio"));
        MobilityEvaluationStatus expected = notEvaluable.isEmpty()
                ? MobilityEvaluationStatus.EVALUABLE
                : MobilityEvaluationStatus.NOT_EVALUABLE;
        if (status != expected) {
            throw new IllegalArgumentException("El estado no coincide con las restricciones no evaluables");
        }
    }

    public static MobilityEvaluation of(
            List<MobilityFinding> findings,
            List<MobilityNotEvaluable> notEvaluable) {
        MobilityEvaluationStatus status = notEvaluable.isEmpty()
                ? MobilityEvaluationStatus.EVALUABLE
                : MobilityEvaluationStatus.NOT_EVALUABLE;
        return new MobilityEvaluation(status, findings, notEvaluable);
    }
}
