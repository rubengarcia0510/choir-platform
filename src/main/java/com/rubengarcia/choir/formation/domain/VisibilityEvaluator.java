package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Evaluación pura de restricciones de visibilidad.
 *
 * <p>Para cada restricción activa, compara al coreuta protegido con el resto de
 * los coreutas del mismo borrador. Un coreuta es posible obstructor si:
 * |X_obstructor - X_protegido| &lt;= 2, Y_obstructor &gt; Y_protegido (está delante,
 * con y = 100 en el frente) y heightCmSnapshot_obstructor &gt; heightCmSnapshot_protegido
 * (estricto). La altura usada es siempre el snapshot, nunca la altura del perfil.</p>
 *
 * <p>No modifica entradas, no asume garantías con datos faltantes y no realiza
 * simulación de línea visual ni geometría corporal.</p>
 */
public final class VisibilityEvaluator {

    /** Tolerancia horizontal fija, en unidades de escala. */
    public static final BigDecimal HORIZONTAL_TOLERANCE = new BigDecimal("2");

    private VisibilityEvaluator() {
    }

    public static VisibilityEvaluation evaluate(
            List<FormationVisibilityConstraint> constraints,
            List<FormationPosition> draftPositions) {

        Map<Long, List<FormationPosition>> bySinger = new TreeMap<>();
        for (FormationPosition position : draftPositions) {
            if (position.getSingerId() != null) {
                bySinger.computeIfAbsent(position.getSingerId(), id -> new ArrayList<>())
                        .add(position);
            }
        }

        List<VisibilityFinding> findings = new ArrayList<>();
        List<VisibilityNotEvaluable> notEvaluable = new ArrayList<>();

        constraints.stream()
                .filter(FormationVisibilityConstraint::active)
                .sorted(Comparator.comparing(FormationVisibilityConstraint::id))
                .forEach(constraint -> evaluateOne(
                        constraint, bySinger, findings, notEvaluable));

        return VisibilityEvaluation.of(findings, notEvaluable);
    }

    private static void evaluateOne(
            FormationVisibilityConstraint constraint,
            Map<Long, List<FormationPosition>> bySinger,
            List<VisibilityFinding> findings,
            List<VisibilityNotEvaluable> notEvaluable) {

        Long protectedId = constraint.protectedSingerId();
        List<FormationPosition> protectedCandidates =
                bySinger.getOrDefault(protectedId, List.of());

        if (protectedCandidates.isEmpty()) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, null, null,
                    VisibilityNotEvaluableReason.MISSING_PROTECTED_DRAFT_POSITION));
            return;
        }
        if (protectedCandidates.size() > 1) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, null, null,
                    VisibilityNotEvaluableReason.AMBIGUOUS_PROTECTED_DRAFT_POSITION));
            return;
        }

        FormationPosition protectedPosition = protectedCandidates.get(0);
        if (protectedPosition.getXCoordinate() == null || protectedPosition.getYCoordinate() == null) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, null, protectedPosition.getId(),
                    VisibilityNotEvaluableReason.MISSING_PROTECTED_COORDINATES));
            return;
        }
        if (protectedPosition.getHeightCmSnapshot() == null) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, null, protectedPosition.getId(),
                    VisibilityNotEvaluableReason.MISSING_PROTECTED_HEIGHT));
            return;
        }

        for (Map.Entry<Long, List<FormationPosition>> entry : bySinger.entrySet()) {
            Long obstructorId = entry.getKey();
            if (obstructorId.equals(protectedId)) {
                continue;
            }
            evaluateObstructor(constraint, protectedPosition, obstructorId,
                    entry.getValue(), findings, notEvaluable);
        }
    }

    private static void evaluateObstructor(
            FormationVisibilityConstraint constraint,
            FormationPosition protectedPosition,
            Long obstructorId,
            List<FormationPosition> candidates,
            List<VisibilityFinding> findings,
            List<VisibilityNotEvaluable> notEvaluable) {

        Long protectedId = constraint.protectedSingerId();

        if (candidates.size() > 1) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, obstructorId, null,
                    VisibilityNotEvaluableReason.AMBIGUOUS_OBSTRUCTOR_DRAFT_POSITION));
            return;
        }

        FormationPosition obstructor = candidates.get(0);
        if (obstructor.getXCoordinate() == null || obstructor.getYCoordinate() == null) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, obstructorId, obstructor.getId(),
                    VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_COORDINATES));
            return;
        }

        BigDecimal distance = obstructor.getXCoordinate()
                .subtract(protectedPosition.getXCoordinate()).abs();
        boolean inTolerance = distance.compareTo(HORIZONTAL_TOLERANCE) <= 0;
        boolean inFront = obstructor.getYCoordinate()
                .compareTo(protectedPosition.getYCoordinate()) > 0;

        // Fuera de la tolerancia o detrás: no puede obstruir, aunque no tenga altura.
        if (!inTolerance || !inFront) {
            return;
        }

        Integer obstructorHeight = obstructor.getHeightCmSnapshot();
        if (obstructorHeight == null) {
            notEvaluable.add(new VisibilityNotEvaluable(
                    constraint.id(), protectedId, obstructorId, obstructor.getId(),
                    VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_HEIGHT));
            return;
        }

        if (obstructorHeight > protectedPosition.getHeightCmSnapshot()) {
            findings.add(new VisibilityFinding(
                    constraint.id(),
                    protectedId,
                    obstructorId,
                    protectedPosition.getId(),
                    obstructor.getId(),
                    protectedPosition.getHeightCmSnapshot(),
                    obstructorHeight,
                    distance));
        }
    }
}
