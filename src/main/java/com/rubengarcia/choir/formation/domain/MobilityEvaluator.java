package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluación geométrica pura de restricciones de movilidad.
 *
 * <p>Compara cada restricción activa con la única posición de su coreuta en el
 * borrador. Los límites son inclusivos. No modifica nada, no hace E/S y no
 * asume rangos de coordenadas.</p>
 */
public final class MobilityEvaluator {

    private MobilityEvaluator() {
    }

    public static MobilityEvaluation evaluate(
            List<FormationMobilityConstraint> constraints,
            List<FormationPosition> draftPositions) {

        Map<Long, List<FormationPosition>> positionsBySinger = new HashMap<>();
        for (FormationPosition position : draftPositions) {
            if (position.getSingerId() != null) {
                positionsBySinger
                        .computeIfAbsent(position.getSingerId(), id -> new ArrayList<>())
                        .add(position);
            }
        }

        List<MobilityFinding> findings = new ArrayList<>();
        List<MobilityNotEvaluable> notEvaluable = new ArrayList<>();

        constraints.stream()
                .filter(FormationMobilityConstraint::active)
                .sorted(Comparator.comparing(FormationMobilityConstraint::id))
                .forEach(constraint -> evaluateOne(
                        constraint, positionsBySinger, findings, notEvaluable));

        return MobilityEvaluation.of(findings, notEvaluable);
    }

    private static void evaluateOne(
            FormationMobilityConstraint constraint,
            Map<Long, List<FormationPosition>> positionsBySinger,
            List<MobilityFinding> findings,
            List<MobilityNotEvaluable> notEvaluable) {

        List<FormationPosition> candidates =
                positionsBySinger.getOrDefault(constraint.singerId(), List.of());

        if (candidates.isEmpty()) {
            notEvaluable.add(new MobilityNotEvaluable(
                    constraint.id(), constraint.singerId(), null,
                    MobilityNotEvaluableReason.MISSING_DRAFT_POSITION));
            return;
        }
        if (candidates.size() > 1) {
            notEvaluable.add(new MobilityNotEvaluable(
                    constraint.id(), constraint.singerId(), null,
                    MobilityNotEvaluableReason.AMBIGUOUS_DRAFT_POSITION));
            return;
        }

        FormationPosition position = candidates.get(0);
        BigDecimal x = position.getXCoordinate();
        BigDecimal y = position.getYCoordinate();
        if (x == null || y == null) {
            notEvaluable.add(new MobilityNotEvaluable(
                    constraint.id(), constraint.singerId(), position.getId(),
                    MobilityNotEvaluableReason.MISSING_COORDINATES));
            return;
        }

        Set<MobilityBound> violated = violatedBounds(constraint.region(), x, y);
        if (!violated.isEmpty()) {
            findings.add(new MobilityFinding(
                    constraint.id(), constraint.singerId(), position.getId(),
                    x, y, violated));
        }
    }

    static Set<MobilityBound> violatedBounds(MobilityRegion region, BigDecimal x, BigDecimal y) {
        Set<MobilityBound> violated = EnumSet.noneOf(MobilityBound.class);
        if (x.compareTo(region.minX()) < 0) {
            violated.add(MobilityBound.X_BELOW_MIN);
        }
        if (x.compareTo(region.maxX()) > 0) {
            violated.add(MobilityBound.X_ABOVE_MAX);
        }
        if (y.compareTo(region.minY()) < 0) {
            violated.add(MobilityBound.Y_BELOW_MIN);
        }
        if (y.compareTo(region.maxY()) > 0) {
            violated.add(MobilityBound.Y_ABOVE_MAX);
        }
        return violated;
    }
}
