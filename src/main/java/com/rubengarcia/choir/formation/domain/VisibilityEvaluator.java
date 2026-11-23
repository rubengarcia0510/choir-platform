package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Evaluación pura de restricciones de visibilidad.
 *
 * <p>Para cada restricción activa se evalúa cada posición del coreuta protegido
 * contra cada posición de cada otro coreuta asignado a la formación. Una
 * combinación produce un hallazgo si:</p>
 * <ul>
 *   <li>|X_obstructor - X_protegido| &lt;= 2 (tolerancia fija, inclusiva);</li>
 *   <li>Y_obstructor &gt; Y_protegido (delante; y = 100 es el frente);</li>
 *   <li>heightCmSnapshot_obstructor &gt; heightCmSnapshot_protegido (estricto).</li>
 * </ul>
 *
 * <p>Los datos insuficientes producen resultados no evaluables; nunca se asume
 * que una visibilidad está garantizada. No consulta repositorios ni infraestructura,
 * no modifica sus entradas y es determinista.</p>
 */
public final class VisibilityEvaluator {

    /** Tolerancia horizontal fija, en unidades de escala. */
    public static final BigDecimal HORIZONTAL_TOLERANCE = new BigDecimal("2");

    private VisibilityEvaluator() {
    }

    /**
     * @param constraints        restricciones de visibilidad; solo las activas se evalúan
     * @param draftPositions     posiciones del DRAFT de esta formación
     * @param assignedSingerIds  coreutas asignados a la ocurrencia y formación,
     *                           tengan o no posición en el DRAFT
     */
    public static VisibilityEvaluation evaluate(
            List<FormationVisibilityConstraint> constraints,
            List<FormationPosition> draftPositions,
            Set<Long> assignedSingerIds) {

        Objects.requireNonNull(constraints, "constraints es obligatorio");
        Objects.requireNonNull(draftPositions, "draftPositions es obligatorio");
        Objects.requireNonNull(assignedSingerIds, "assignedSingerIds es obligatorio");

        Set<Long> roster = Set.copyOf(assignedSingerIds);

        Map<Long, List<FormationPosition>> bySinger = new TreeMap<>();
        for (FormationPosition position : draftPositions) {
            if (position.getSingerId() != null) {
                bySinger.computeIfAbsent(position.getSingerId(), id -> new ArrayList<>())
                        .add(position);
            }
        }
        for (List<FormationPosition> positions : bySinger.values()) {
            positions.sort(Comparator.comparing(FormationPosition::getId,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }

        List<VisibilityFinding> findings = new ArrayList<>();
        List<VisibilityNotEvaluable> notEvaluable = new ArrayList<>();

        constraints.stream()
                .filter(FormationVisibilityConstraint::active)
                .sorted(Comparator.comparing(FormationVisibilityConstraint::id))
                .forEach(constraint -> evaluateConstraint(
                        constraint, bySinger, roster, findings, notEvaluable));

        return VisibilityEvaluation.of(findings, notEvaluable);
    }

    private static void evaluateConstraint(
            FormationVisibilityConstraint constraint,
            Map<Long, List<FormationPosition>> bySinger,
            Set<Long> roster,
            List<VisibilityFinding> findings,
            List<VisibilityNotEvaluable> notEvaluable) {

        Long protectedId = constraint.protectedSingerId();
        List<FormationPosition> protectedPositions =
                bySinger.getOrDefault(protectedId, List.of());

        if (protectedPositions.isEmpty()) {
            addNotEvaluable(notEvaluable, constraint, protectedId, null, null,
                    VisibilityNotEvaluableReason.MISSING_PROTECTED_DRAFT_POSITION);
            return;
        }
        if (!roster.contains(protectedId)) {
            for (FormationPosition position : protectedPositions) {
                addNotEvaluable(notEvaluable, constraint, protectedId, null,
                        position.getId(),
                        VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER);
            }
            return;
        }

        List<FormationPosition> validProtected = new ArrayList<>();
        for (FormationPosition protectedPosition : protectedPositions) {
            if (protectedPosition.getXCoordinate() == null
                    || protectedPosition.getYCoordinate() == null) {
                addNotEvaluable(notEvaluable, constraint, protectedId, null,
                        protectedPosition.getId(),
                        VisibilityNotEvaluableReason.MISSING_PROTECTED_COORDINATES);
            } else if (protectedPosition.getHeightCmSnapshot() == null) {
                addNotEvaluable(notEvaluable, constraint, protectedId, null,
                        protectedPosition.getId(),
                        VisibilityNotEvaluableReason.MISSING_PROTECTED_HEIGHT);
            } else {
                validProtected.add(protectedPosition);
            }
        }

        TreeSet<Long> others = new TreeSet<>(bySinger.keySet());
        others.addAll(roster);
        others.remove(protectedId);

        for (Long obstructorId : others) {
            List<FormationPosition> candidates = bySinger.getOrDefault(obstructorId, List.of());

            if (!roster.contains(obstructorId)) {
                for (FormationPosition position : candidates) {
                    addNotEvaluable(notEvaluable, constraint, protectedId, obstructorId,
                            position.getId(),
                            VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER);
                }
                continue;
            }
            if (candidates.isEmpty()) {
                addNotEvaluable(notEvaluable, constraint, protectedId, obstructorId, null,
                        VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_DRAFT_POSITION);
                continue;
            }
            for (FormationPosition obstructor : candidates) {
                evaluateObstructorPosition(constraint, validProtected, obstructorId,
                        obstructor, findings, notEvaluable);
            }
        }
    }

    private static void evaluateObstructorPosition(
            FormationVisibilityConstraint constraint,
            List<FormationPosition> validProtected,
            Long obstructorId,
            FormationPosition obstructor,
            List<VisibilityFinding> findings,
            List<VisibilityNotEvaluable> notEvaluable) {

        Long protectedId = constraint.protectedSingerId();

        if (obstructor.getXCoordinate() == null || obstructor.getYCoordinate() == null) {
            addNotEvaluable(notEvaluable, constraint, protectedId, obstructorId,
                    obstructor.getId(),
                    VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_COORDINATES);
            return;
        }

        boolean heightReported = false;
        for (FormationPosition protectedPosition : validProtected) {
            BigDecimal distance = obstructor.getXCoordinate()
                    .subtract(protectedPosition.getXCoordinate()).abs();
            boolean inTolerance = distance.compareTo(HORIZONTAL_TOLERANCE) <= 0;
            boolean inFront = obstructor.getYCoordinate()
                    .compareTo(protectedPosition.getYCoordinate()) > 0;

            // Fuera de la tolerancia o detrás: no puede obstruir, aunque no tenga altura.
            if (!inTolerance || !inFront) {
                continue;
            }

            Integer obstructorHeight = obstructor.getHeightCmSnapshot();
            if (obstructorHeight == null) {
                if (!heightReported) {
                    addNotEvaluable(notEvaluable, constraint, protectedId, obstructorId,
                            obstructor.getId(),
                            VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_HEIGHT);
                    heightReported = true;
                }
                continue;
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

    private static void addNotEvaluable(
            List<VisibilityNotEvaluable> notEvaluable,
            FormationVisibilityConstraint constraint,
            Long protectedId,
            Long obstructorId,
            Long positionId,
            VisibilityNotEvaluableReason reason) {
        notEvaluable.add(new VisibilityNotEvaluable(
                constraint.id(), protectedId, obstructorId, positionId, reason));
    }
}
