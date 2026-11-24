package com.rubengarcia.choir.formation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orquesta los validadores de bloqueo, proximidad y movilidad, en ese orden fijo.
 *
 * <p>Ejecuta los tres aunque alguno tenga violaciones, para acumular todos los problemas.
 * Proximidad y movilidad evalúan las posiciones propuestas originales. Las posiciones del
 * resultado son las validadas por bloqueo solo si no hay ninguna violación. No captura
 * excepciones: una inconsistencia interna se propaga.</p>
 */
public final class CompositeFormationConstraintValidator {

    private CompositeFormationConstraintValidator() {
    }

    public static FormationConstraintValidationResult validate(
            List<FormationPosition> persistedPositions,
            List<FormationPosition> proposedPositions,
            List<FormationProximityConstraint> proximityConstraints,
            List<FormationMobilityConstraint> mobilityConstraints) {
        Objects.requireNonNull(persistedPositions, "persistedPositions es obligatorio");
        Objects.requireNonNull(proposedPositions, "proposedPositions es obligatorio");
        Objects.requireNonNull(proximityConstraints, "proximityConstraints es obligatorio");
        Objects.requireNonNull(mobilityConstraints, "mobilityConstraints es obligatorio");

        LockedPositionValidationResult locked =
                LockedPositionValidator.validate(persistedPositions, proposedPositions);
        ProximityValidationResult proximity =
                ProximityConstraintValidator.validate(proximityConstraints, proposedPositions);
        MobilityConstraintValidationResult mobility =
                MobilityConstraintValidator.validate(mobilityConstraints, proposedPositions);

        List<FormationConstraintViolation> violations = new ArrayList<>();
        violations.addAll(locked.violations());
        violations.addAll(proximity.violations());
        violations.addAll(mobility.violations());

        List<FormationPosition> positions = violations.isEmpty()
                ? locked.positions()
                : List.of();
        return new FormationConstraintValidationResult(violations, positions);
    }
}
