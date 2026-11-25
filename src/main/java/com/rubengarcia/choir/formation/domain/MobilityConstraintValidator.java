package com.rubengarcia.choir.formation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Traduce el resultado de {@link MobilityEvaluator} en violaciones estructuradas.
 *
 * <p>No repite cálculos geométricos. Primero emite los hallazgos del evaluador y después
 * las comparaciones no evaluables, ambos en el orden que produce el evaluador. Preserva
 * ids y motivos. No modifica sus entradas.</p>
 */
public final class MobilityConstraintValidator {

    private MobilityConstraintValidator() {
    }

    public static MobilityConstraintValidationResult validate(
            List<FormationMobilityConstraint> constraints,
            List<FormationPosition> candidatePositions) {
        Objects.requireNonNull(constraints, "constraints es obligatorio");
        Objects.requireNonNull(candidatePositions, "candidatePositions es obligatorio");

        MobilityEvaluation evaluation = MobilityEvaluator.evaluate(constraints, candidatePositions);
        List<MobilityConstraintViolation> violations = new ArrayList<>();

        for (MobilityFinding finding : evaluation.findings()) {
            violations.add(new MobilityConstraintViolation(
                    MobilityConstraintViolationCode.MOBILITY_CONSTRAINT_VIOLATION,
                    finding.constraintId(),
                    finding.singerId(),
                    finding.positionId(),
                    finding.violatedBounds(),
                    null));
        }
        for (MobilityNotEvaluable entry : evaluation.notEvaluable()) {
            violations.add(new MobilityConstraintViolation(
                    MobilityConstraintViolationCode.MOBILITY_NOT_EVALUABLE,
                    entry.constraintId(),
                    entry.singerId(),
                    entry.positionId(),
                    Set.of(),
                    entry.reason()));
        }

        return new MobilityConstraintValidationResult(violations);
    }
}
