package com.rubengarcia.choir.transition.domain;

import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.DataIssue;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.DataIssueCode;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.SingerDistance;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.Stage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class TransitionDisplacementCalculator {

    private static final int DISTANCE_SCALE = 6;
    private static final MathContext SQRT_CONTEXT =
            new MathContext(40, RoundingMode.HALF_EVEN);
    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(DISTANCE_SCALE);

    public TransitionDisplacementMetrics calculate(
            TransitionPlanningContext context,
            List<TransitionPlanningPosition> positions) {
        validateContext(context, positions);

        Map<Long, List<TransitionPlanningPosition>> fromPositions =
                new TreeMap<>();
        Map<Long, List<TransitionPlanningPosition>> toPositions =
                new TreeMap<>();
        List<DataIssue> issues = new ArrayList<>();
        Set<Long> invalidSingers = new HashSet<>();

        for (TransitionPlanningPosition position : positions) {
            if (position == null) {
                throw new IllegalArgumentException(
                        "Las posiciones de transición no pueden ser nulas");
            }

            Stage stage = stageFor(position, context);
            Long singerId = position.singerId();

            if (singerId == null || singerId <= 0) {
                issues.add(new DataIssue(
                        DataIssueCode.INVALID_SINGER_ID,
                        stage,
                        singerId));
                continue;
            }

            Map<Long, List<TransitionPlanningPosition>> stagePositions =
                    stage == Stage.FROM ? fromPositions : toPositions;
            stagePositions.computeIfAbsent(singerId, ignored -> new ArrayList<>())
                    .add(position);

            if (position.xCoordinate() == null
                    || position.yCoordinate() == null) {
                issues.add(new DataIssue(
                        DataIssueCode.INVALID_COORDINATE,
                        stage,
                        singerId));
                invalidSingers.add(singerId);
            }
        }

        markDuplicates(fromPositions, Stage.FROM, issues, invalidSingers);
        markDuplicates(toPositions, Stage.TO, issues, invalidSingers);

        Set<Long> singerIds = new TreeSet<>();
        singerIds.addAll(fromPositions.keySet());
        singerIds.addAll(toPositions.keySet());

        List<SingerDistance> distances = new ArrayList<>();
        BigDecimal totalDistance = ZERO;
        BigDecimal maxIndividualDistance = ZERO;
        int movingSingerCount = 0;

        for (long singerId : singerIds) {
            List<TransitionPlanningPosition> from =
                    fromPositions.get(singerId);
            List<TransitionPlanningPosition> to =
                    toPositions.get(singerId);

            if (from == null || to == null) {
                issues.add(new DataIssue(
                        DataIssueCode.MISSING_COUNTERPART,
                        from == null ? Stage.FROM : Stage.TO,
                        singerId));
                continue;
            }

            if (invalidSingers.contains(singerId)) {
                continue;
            }

            BigDecimal distance = distance(
                    from.getFirst(),
                    to.getFirst());
            distances.add(new SingerDistance(singerId, distance));
            totalDistance = totalDistance.add(distance);
            if (distance.compareTo(maxIndividualDistance) > 0) {
                maxIndividualDistance = distance;
            }
            if (hasGeometricMovement(from.getFirst(), to.getFirst())) {
                movingSingerCount++;
            }
        }

        issues.sort(Comparator
                .comparing(
                        DataIssue::singerId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DataIssue::stage)
                .thenComparing(DataIssue::code));

        return new TransitionDisplacementMetrics(
                context.transitionVersionId(),
                context.fromFormationVersionId(),
                context.toFormationVersionId(),
                distances,
                totalDistance,
                maxIndividualDistance,
                movingSingerCount,
                issues);
    }

    private static void validateContext(
            TransitionPlanningContext context,
            List<TransitionPlanningPosition> positions) {
        if (context == null
                || positions == null
                || context.transitionVersionId() <= 0
                || context.fromFormationVersionId() <= 0
                || context.toFormationVersionId() <= 0
                || context.fromFormationVersionId()
                        == context.toFormationVersionId()) {
            throw new IllegalArgumentException(
                    "Se requiere un contexto de transición y una lista de posiciones válidos");
        }
    }

    private static Stage stageFor(
            TransitionPlanningPosition position,
            TransitionPlanningContext context) {
        if (position.transitionVersionId() != context.transitionVersionId()) {
            throw new IllegalArgumentException(
                    "Las posiciones deben pertenecer a la versión exacta de transición");
        }

        Stage stage;
        if ("FROM".equals(position.stage())) {
            stage = Stage.FROM;
        } else if ("TO".equals(position.stage())) {
            stage = Stage.TO;
        } else {
            throw new IllegalArgumentException(
                    "La etapa de posición debe ser FROM o TO");
        }
        long expectedFormationVersionId = stage == Stage.FROM
                ? context.fromFormationVersionId()
                : context.toFormationVersionId();
        if (position.formationVersionId() != expectedFormationVersionId) {
            throw new IllegalArgumentException(
                    "Las posiciones deben pertenecer a los snapshots exactos de transición");
        }
        return stage;
    }

    private static void markDuplicates(
            Map<Long, List<TransitionPlanningPosition>> positions,
            Stage stage,
            List<DataIssue> issues,
            Set<Long> invalidSingers) {
        positions.forEach((singerId, singerPositions) -> {
            if (singerPositions.size() > 1) {
                issues.add(new DataIssue(
                        DataIssueCode.DUPLICATE_SINGER_POSITION,
                        stage,
                        singerId));
                invalidSingers.add(singerId);
            }
        });
    }

    private static BigDecimal distance(
            TransitionPlanningPosition from,
            TransitionPlanningPosition to) {
        BigDecimal dx = to.xCoordinate().subtract(from.xCoordinate());
        BigDecimal dy = to.yCoordinate().subtract(from.yCoordinate());
        return dx.multiply(dx)
                .add(dy.multiply(dy))
                .sqrt(SQRT_CONTEXT)
                .setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean hasGeometricMovement(
            TransitionPlanningPosition from,
            TransitionPlanningPosition to) {
        return from.xCoordinate().compareTo(to.xCoordinate()) != 0
                || from.yCoordinate().compareTo(to.yCoordinate()) != 0;
    }
}
