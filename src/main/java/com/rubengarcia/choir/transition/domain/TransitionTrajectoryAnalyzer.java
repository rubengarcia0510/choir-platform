package com.rubengarcia.choir.transition.domain;

import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.GeometryIssue;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.GeometryIssueCode;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TemporalConflictStatus;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TrajectoryFinding;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class TransitionTrajectoryAnalyzer {

    public TransitionTrajectoryAnalysis analyze(
            TransitionPlanningContext context,
            List<TransitionPlanningPosition> positions,
            List<TransitionMovementStep> movementSteps) {
        validateContext(context, positions);

        Map<Long, List<TransitionPlanningPosition>> fromPositions =
                new TreeMap<>();
        Map<Long, List<TransitionPlanningPosition>> toPositions =
                new TreeMap<>();
        List<GeometryIssue> issues = new ArrayList<>();
        Set<Long> invalidSingers = new HashSet<>();

        for (TransitionPlanningPosition position : positions) {
            if (position == null) {
                throw new IllegalArgumentException(
                        "Las posiciones de transición no pueden ser nulas");
            }
            String stage = stageFor(position, context);
            Long singerId = position.singerId();
            if (singerId == null || singerId <= 0) {
                issues.add(new GeometryIssue(
                        GeometryIssueCode.INVALID_SINGER_ID,
                        stage,
                        singerId));
                continue;
            }

            Map<Long, List<TransitionPlanningPosition>> stagePositions =
                    "FROM".equals(stage) ? fromPositions : toPositions;
            stagePositions.computeIfAbsent(singerId, ignored -> new ArrayList<>())
                    .add(position);
            if (position.xCoordinate() == null
                    || position.yCoordinate() == null) {
                issues.add(new GeometryIssue(
                        GeometryIssueCode.INVALID_COORDINATE,
                        stage,
                        singerId));
                invalidSingers.add(singerId);
            }
        }

        markDuplicates(fromPositions, "FROM", issues, invalidSingers);
        markDuplicates(toPositions, "TO", issues, invalidSingers);

        Set<Long> singerIds = new TreeSet<>();
        singerIds.addAll(fromPositions.keySet());
        singerIds.addAll(toPositions.keySet());
        List<MovementSegment> movements = new ArrayList<>();
        for (long singerId : singerIds) {
            List<TransitionPlanningPosition> from = fromPositions.get(singerId);
            List<TransitionPlanningPosition> to = toPositions.get(singerId);
            if (from == null || to == null) {
                issues.add(new GeometryIssue(
                        GeometryIssueCode.MISSING_COUNTERPART,
                        from == null ? "FROM" : "TO",
                        singerId));
                continue;
            }
            if (invalidSingers.contains(singerId)) {
                continue;
            }
            TransitionPlanningPosition start = from.getFirst();
            TransitionPlanningPosition end = to.getFirst();
            if (!samePoint(start, end)) {
                movements.add(new MovementSegment(singerId, start, end));
            }
        }

        TemporalEvidence evidence = validateEvidence(context, movementSteps);
        List<TrajectoryFinding> findings = new ArrayList<>();
        for (int first = 0; first < movements.size(); first++) {
            MovementSegment left = movements.get(first);
            for (int second = first + 1; second < movements.size(); second++) {
                MovementSegment right = movements.get(second);
                if (intersects(left.from(), left.to(), right.from(), right.to())) {
                    findings.add(new TrajectoryFinding(
                            left.singerId(),
                            right.singerId(),
                            temporalStatus(left.singerId(), right.singerId(), evidence)));
                }
            }
        }

        issues.sort(Comparator
                .comparing(
                        GeometryIssue::singerId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GeometryIssue::stage)
                .thenComparing(GeometryIssue::code));
        return new TransitionTrajectoryAnalysis(
                context.transitionVersionId(),
                findings,
                issues);
    }

    private static void validateContext(
            TransitionPlanningContext context,
            List<TransitionPlanningPosition> positions) {
        if (context == null
                || positions == null
                || context.transitionId() <= 0
                || context.transitionVersionId() <= 0
                || context.fromFormationVersionId() <= 0
                || context.toFormationVersionId() <= 0
                || context.fromFormationVersionId()
                        == context.toFormationVersionId()) {
            throw new IllegalArgumentException(
                    "Se requiere un contexto de transición y posiciones válidos");
        }
    }

    private static String stageFor(
            TransitionPlanningPosition position,
            TransitionPlanningContext context) {
        if (position.transitionVersionId() != context.transitionVersionId()) {
            throw new IllegalArgumentException(
                    "Las posiciones deben pertenecer a la versión exacta de transición");
        }
        String stage = position.stage();
        long expectedFormationVersionId;
        if ("FROM".equals(stage)) {
            expectedFormationVersionId = context.fromFormationVersionId();
        } else if ("TO".equals(stage)) {
            expectedFormationVersionId = context.toFormationVersionId();
        } else {
            throw new IllegalArgumentException(
                    "La etapa de posición debe ser FROM o TO");
        }
        if (position.formationVersionId() != expectedFormationVersionId) {
            throw new IllegalArgumentException(
                    "Las posiciones deben pertenecer a los snapshots exactos de transición");
        }
        return stage;
    }

    private static void markDuplicates(
            Map<Long, List<TransitionPlanningPosition>> positions,
            String stage,
            List<GeometryIssue> issues,
            Set<Long> invalidSingers) {
        positions.forEach((singerId, singerPositions) -> {
            if (singerPositions.size() > 1) {
                issues.add(new GeometryIssue(
                        GeometryIssueCode.DUPLICATE_SINGER_POSITION,
                        stage,
                        singerId));
                invalidSingers.add(singerId);
            }
        });
    }

    private static TemporalEvidence validateEvidence(
            TransitionPlanningContext context,
            List<TransitionMovementStep> movementSteps) {
        if (movementSteps == null || movementSteps.isEmpty()) {
            return TemporalEvidence.unavailable();
        }

        Map<Integer, List<TransitionMovementStep>> bySequence = new TreeMap<>();
        Set<String> uniqueParticipantsBySequence = new HashSet<>();
        for (TransitionMovementStep step : movementSteps) {
            if (step == null
                    || step.transitionId() != context.transitionId()
                    || step.transitionVersionId() != context.transitionVersionId()
                    || step.fromFormationVersionId()
                            != context.fromFormationVersionId()
                    || step.toFormationVersionId()
                            != context.toFormationVersionId()
                    || step.singerId() <= 0
                    || step.sequenceNumber() <= 0
                    || step.temporalReference() == null
                    || step.action() == null
                    || step.action().isBlank()) {
                return TemporalEvidence.inconsistent();
            }
            String participantKey = step.sequenceNumber() + ":" + step.singerId();
            if (!uniqueParticipantsBySequence.add(participantKey)) {
                return TemporalEvidence.inconsistent();
            }
            bySequence.computeIfAbsent(
                    step.sequenceNumber(), ignored -> new ArrayList<>()).add(step);
        }

        OffsetDateTime previousReference = null;
        for (int index = 0; index < bySequence.size(); index++) {
            List<TransitionMovementStep> sequence = bySequence.get(index + 1);
            if (sequence == null) {
                return TemporalEvidence.inconsistent();
            }
            OffsetDateTime reference = sequence.getFirst().temporalReference();
            if (sequence.stream().anyMatch(step ->
                    !reference.toInstant().equals(
                            step.temporalReference().toInstant()))) {
                return TemporalEvidence.inconsistent();
            }
            if (previousReference != null
                    && !reference.isAfter(previousReference)) {
                return TemporalEvidence.inconsistent();
            }
            previousReference = reference;
        }

        Map<Long, Set<Integer>> sequencesBySinger = new HashMap<>();
        bySequence.forEach((sequenceNumber, sequence) -> sequence.forEach(step ->
                sequencesBySinger.computeIfAbsent(
                        step.singerId(), ignored -> new TreeSet<>())
                        .add(sequenceNumber)));
        return TemporalEvidence.valid(sequencesBySinger);
    }

    private static TemporalConflictStatus temporalStatus(
            long firstSingerId,
            long secondSingerId,
            TemporalEvidence evidence) {
        if (!evidence.consistent()) {
            return TemporalConflictStatus.NOT_EVALUABLE;
        }
        Set<Integer> firstSequences = evidence.sequencesBySinger()
                .get(firstSingerId);
        Set<Integer> secondSequences = evidence.sequencesBySinger()
                .get(secondSingerId);
        if (firstSequences == null || secondSequences == null) {
            return TemporalConflictStatus.NOT_EVALUABLE;
        }
        return firstSequences.stream().anyMatch(secondSequences::contains)
                ? TemporalConflictStatus.CONFIRMED
                : TemporalConflictStatus.NOT_CONCURRENT;
    }

    private static boolean intersects(
            TransitionPlanningPosition a,
            TransitionPlanningPosition b,
            TransitionPlanningPosition c,
            TransitionPlanningPosition d) {
        BigDecimal first = cross(a, b, c);
        BigDecimal second = cross(a, b, d);
        BigDecimal third = cross(c, d, a);
        BigDecimal fourth = cross(c, d, b);
        int firstSign = first.signum();
        int secondSign = second.signum();
        int thirdSign = third.signum();
        int fourthSign = fourth.signum();

        if (firstSign == 0 && onSegment(a, b, c)
                || secondSign == 0 && onSegment(a, b, d)
                || thirdSign == 0 && onSegment(c, d, a)
                || fourthSign == 0 && onSegment(c, d, b)) {
            return true;
        }
        return firstSign * secondSign < 0 && thirdSign * fourthSign < 0;
    }

    private static BigDecimal cross(
            TransitionPlanningPosition a,
            TransitionPlanningPosition b,
            TransitionPlanningPosition point) {
        BigDecimal abX = b.xCoordinate().subtract(a.xCoordinate());
        BigDecimal abY = b.yCoordinate().subtract(a.yCoordinate());
        BigDecimal apX = point.xCoordinate().subtract(a.xCoordinate());
        BigDecimal apY = point.yCoordinate().subtract(a.yCoordinate());
        return abX.multiply(apY).subtract(abY.multiply(apX));
    }

    private static boolean onSegment(
            TransitionPlanningPosition start,
            TransitionPlanningPosition end,
            TransitionPlanningPosition point) {
        return point.xCoordinate().compareTo(
                        start.xCoordinate().min(end.xCoordinate())) >= 0
                && point.xCoordinate().compareTo(
                        start.xCoordinate().max(end.xCoordinate())) <= 0
                && point.yCoordinate().compareTo(
                        start.yCoordinate().min(end.yCoordinate())) >= 0
                && point.yCoordinate().compareTo(
                        start.yCoordinate().max(end.yCoordinate())) <= 0;
    }

    private static boolean samePoint(
            TransitionPlanningPosition first,
            TransitionPlanningPosition second) {
        return first.xCoordinate().compareTo(second.xCoordinate()) == 0
                && first.yCoordinate().compareTo(second.yCoordinate()) == 0;
    }

    private record MovementSegment(
            long singerId,
            TransitionPlanningPosition from,
            TransitionPlanningPosition to) {
    }

    private record TemporalEvidence(
            boolean consistent,
            Map<Long, Set<Integer>> sequencesBySinger) {

        private static TemporalEvidence unavailable() {
            return new TemporalEvidence(true, Map.of());
        }

        private static TemporalEvidence inconsistent() {
            return new TemporalEvidence(false, Map.of());
        }

        private static TemporalEvidence valid(
                Map<Long, Set<Integer>> sequencesBySinger) {
            return new TemporalEvidence(
                    true,
                    Map.copyOf(sequencesBySinger));
        }
    }
}
