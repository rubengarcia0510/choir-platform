package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Valida que las posiciones propuestas respeten las restricciones de proximidad activas.
 *
 * <p>Regla: distanceSquared = (x1 - x2)^2 + (y1 - y2)^2, con BigDecimal exacto. Hay
 * violación solo si distanceSquared &gt; maxDistance^2; la distancia igual al límite se acepta.
 * Las coordenadas están en el sistema normalizado del escenario, no en metros.</p>
 *
 * <p>Si falta la posición de alguno de los dos coreutas, la restricción se omite sin violación.
 * Si una posición presente tiene coordenadas nulas, la comparación no puede hacerse y se
 * informa como inconsistencia (IllegalArgumentException). Un coreuta repetido en la propuesta
 * conserva la última posición, como la validación actual de persistencia. No modifica
 * sus entradas.</p>
 */
public final class ProximityConstraintValidator {

    private ProximityConstraintValidator() {
    }

    public static ProximityValidationResult validate(
            List<FormationProximityConstraint> constraints,
            List<FormationPosition> proposedPositions) {
        Objects.requireNonNull(constraints, "constraints es obligatorio");
        Objects.requireNonNull(proposedPositions, "proposedPositions es obligatorio");

        Map<Long, FormationPosition> bySinger = new LinkedHashMap<>();
        for (FormationPosition position : proposedPositions) {
            if (position.getSingerId() != null) {
                bySinger.put(position.getSingerId(), position);
            }
        }

        List<ProximityViolation> violations = new ArrayList<>();
        for (FormationProximityConstraint constraint : constraints) {
            if (!constraint.active()) {
                continue;
            }
            FormationPosition first = bySinger.get(constraint.singerOneId());
            FormationPosition second = bySinger.get(constraint.singerTwoId());
            if (first == null || second == null) {
                continue;
            }
            evaluate(constraint, first, second, violations);
        }
        return new ProximityValidationResult(violations);
    }

    private static void evaluate(
            FormationProximityConstraint constraint,
            FormationPosition first,
            FormationPosition second,
            List<ProximityViolation> violations) {
        requireCoordinates(first);
        requireCoordinates(second);

        BigDecimal deltaX = first.getXCoordinate().subtract(second.getXCoordinate());
        BigDecimal deltaY = first.getYCoordinate().subtract(second.getYCoordinate());
        BigDecimal distanceSquared = deltaX.multiply(deltaX).add(deltaY.multiply(deltaY));
        BigDecimal maxDistanceSquared = constraint.maxDistance().multiply(constraint.maxDistance());

        if (distanceSquared.compareTo(maxDistanceSquared) > 0) {
            violations.add(new ProximityViolation(
                    constraint.id(),
                    constraint.singerOneId(),
                    constraint.singerTwoId(),
                    constraint.maxDistance(),
                    distanceSquared,
                    maxDistanceSquared));
        }
    }

    private static void requireCoordinates(FormationPosition position) {
        if (position.getXCoordinate() == null || position.getYCoordinate() == null) {
            throw new IllegalArgumentException(
                    "La posición del coreuta " + position.getSingerId()
                            + " no tiene coordenadas para evaluar proximidad");
        }
    }
}
