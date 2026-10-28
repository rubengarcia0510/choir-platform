package com.rubengarcia.choir.scene.domain;

import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Direction;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantRef;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.ParticipantType;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Relation;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.SpatialConstraint;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition.Stage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SceneRuleEvaluator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final MathContext CENTER_CONTEXT = MathContext.DECIMAL128;

    public Evaluation evaluate(Input input) {
        if (input == null || input.revision() == null) {
            return notEvaluable("EVALUATION_INPUT_MISSING", "Faltan datos de evaluación");
        }
        SceneRuleRevisionSnapshot revision = input.revision();
        if (!revision.active()) {
            return notEvaluable(revision,
                    "RULE_INACTIVE",
                    "Una regla inactiva no se puede evaluar");
        }
        if (!isSupportedRuleType(revision.ruleType())) {
            return notEvaluable(revision,
                    "RULE_TYPE_UNSUPPORTED",
                    "El tipo de regla no está soportado");
        }
        SceneRuleCondition condition = revision.condition();
        if (condition == null) {
            return notEvaluable(
                    "STRUCTURED_CONDITION_MISSING",
                    "La revisión no contiene una condición estructurada");
        }
        if (!"READY".equals(revision.definitionStatus())) {
            return notEvaluable(
                    revision,
                    "RULE_DEFINITION_DRAFT",
                    "La regla está guardada como borrador y no se puede evaluar");
        }
        if (input.fromFormationVersionId() <= 0 || input.toFormationVersionId() <= 0
                || input.fromFormationVersionId() == input.toFormationVersionId()) {
            return notEvaluable(
                    "FORMATION_SNAPSHOT_INVALID",
                    "Las versiones FROM y TO no son válidas");
        }
        if (input.fromPositions() == null || input.toPositions() == null) {
            return notEvaluable(
                    "FORMATION_POSITIONS_MISSING",
                    "Faltan posiciones de los snapshots FROM o TO");
        }
        if ("MOVEMENT".equals(revision.ruleType())
                && condition.orderingMode() == SceneRuleCondition.OrderingMode.SEQUENTIAL) {
            return notEvaluable(
                    revision,
                    "TEMPORAL_SEQUENCE_UNAVAILABLE",
                    "Los snapshots no incluyen datos para verificar una secuencia temporal");
        }

        if ("EXIT".equals(revision.ruleType())) {
            return aggregate(
                    revision,
                    List.of(evaluateExit(input, condition.exitParticipants())));
        }

        List<Check> checks = new ArrayList<>();
        if (condition.exitParticipants() != null
                && !condition.exitParticipants().isEmpty()) {
            checks.add(Check.notEvaluable(
                    "EXIT_CONDITION_TYPE_MISMATCH",
                    "La condición de salida no corresponde al tipo de regla"));
        }

        List<SpatialConstraint> constraints = condition.spatialConstraints();
        if (constraints != null) {
            for (SpatialConstraint constraint : constraints) {
                checks.add(evaluateConstraint(input, constraint));
            }
        }
        if (checks.isEmpty()) {
            return notEvaluable(
                    "STRUCTURED_CONDITION_INCOMPLETE",
                    "La condición no contiene criterios evaluables");
        }
        return aggregate(revision, checks);
    }

    private boolean isSupportedRuleType(String ruleType) {
        return "MOVEMENT".equals(ruleType)
                || "EXIT".equals(ruleType)
                || "CAST_DIVISION".equals(ruleType);
    }

    private Check evaluateExit(Input input, List<ParticipantRef> participants) {
        return Check.notEvaluable(
                "EXIT_CONDITION_NOT_DEFINED",
                "La regla EXIT requiere una condición de salida estructurada explícita");
    }

    private Check evaluateConstraint(Input input, SpatialConstraint constraint) {
        if (constraint == null || constraint.relation() == null
                || constraint.stage() == null || constraint.participants() == null) {
            return Check.notEvaluable(
                    "SPATIAL_CONDITION_INCOMPLETE",
                    "La relación, etapa o participantes están incompletos");
        }
        if ("MOVEMENT".equals(input.revision().ruleType())
                && !hasBothMovementStages(input.revision().condition())) {
            return Check.notEvaluable(
                    "MOVEMENT_SNAPSHOTS_INCOMPLETE",
                    "La regla de movimiento requiere condiciones FROM y TO explícitas");
        }
        List<Position> positions = constraint.stage() == Stage.FROM
                ? input.fromPositions()
                : input.toPositions();
        Map<Long, Position> bySinger = positionsBySinger(positions);
        if (bySinger == null) {
            return Check.notEvaluable(
                    "FORMATION_POSITIONS_INVALID",
                    "El snapshot contiene posiciones inválidas o duplicadas");
        }
        return switch (constraint.relation()) {
            case TOGETHER -> evaluateTogether(input, constraint, bySinger);
            case NEAR -> evaluateNear(input, constraint, bySinger);
            case RELATIVE_ORDER -> evaluateRelativeOrder(
                    input, constraint, bySinger);
            case SEPARATED -> evaluateSeparated(input, constraint, bySinger);
        };
    }

    private Check evaluateTogether(
            Input input,
            SpatialConstraint constraint,
            Map<Long, Position> positions) {
        if (constraint.participants().size() < 2 || constraint.threshold() == null) {
            return Check.notEvaluable(
                    "TOGETHER_CONDITION_INCOMPLETE",
                    "TOGETHER requiere al menos dos participantes y un umbral");
        }
        List<Position> vertices = new ArrayList<>();
        for (ParticipantRef participant : constraint.participants()) {
            if (participant == null || participant.type() != ParticipantType.SINGER
                    || participant.singerId() == null) {
                return Check.notEvaluable(
                        "GROUP_DISTANCE_UNDEFINED",
                        "TOGETHER no define una geometría para participantes grupales");
            }
            Position position = positions.get(participant.singerId());
            if (position == null) {
                return Check.notEvaluable(
                        "PARTICIPANT_POSITION_MISSING",
                        "Falta la posición de un participante de TOGETHER");
            }
            vertices.add(position);
        }
        if (hasDuplicateSinger(vertices)) {
            return Check.notEvaluable(
                    "PARTICIPANTS_NOT_DISTINCT",
                    "TOGETHER requiere participantes distintos");
        }
        boolean[] visited = new boolean[vertices.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited[0] = true;
        queue.add(0);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int candidate = 0; candidate < vertices.size(); candidate++) {
                if (visited[candidate]) {
                    continue;
                }
                int comparison = compareDistance(
                        vertices.get(current), vertices.get(candidate),
                        constraint.threshold());
                if (comparison <= 0) {
                    visited[candidate] = true;
                    queue.add(candidate);
                }
            }
        }
        for (boolean connected : visited) {
            if (!connected) {
                return Check.violated(
                        "TOGETHER_NETWORK_DISCONNECTED",
                        "Los participantes no forman una red conectada dentro del umbral");
            }
        }
        return Check.satisfied(
                "TOGETHER_NETWORK_CONNECTED",
                "Los participantes forman una red conectada dentro del umbral");
    }

    private Check evaluateNear(
            Input input,
            SpatialConstraint constraint,
            Map<Long, Position> positions) {
        if (constraint.participants().size() != 2 || constraint.threshold() == null) {
            return Check.notEvaluable(
                    "NEAR_CONDITION_INCOMPLETE",
                    "NEAR requiere exactamente dos participantes y un umbral");
        }
        if (constraint.participants().stream()
                .anyMatch(participant -> participant == null
                        || participant.type() != ParticipantType.SINGER
                        || participant.singerId() == null)) {
            return Check.notEvaluable(
                    "GROUP_DISTANCE_UNDEFINED",
                    "NEAR no define una geometría para participantes grupales");
        }
        Position first = positions.get(constraint.participants().get(0).singerId());
        Position second = positions.get(constraint.participants().get(1).singerId());
        if (first == null || second == null
                || first.singerId().equals(second.singerId())) {
            return Check.notEvaluable(
                    "PARTICIPANT_POSITION_MISSING",
                    "NEAR requiere dos participantes distintos con posición disponible");
        }
        int comparison = compareDistance(first, second, constraint.threshold());
        return comparison <= 0
                ? Check.satisfied("NEAR_WITHIN_THRESHOLD", "La distancia está dentro del máximo permitido")
                : Check.violated("NEAR_THRESHOLD_EXCEEDED", "La distancia supera el umbral");
    }

    private Check evaluateRelativeOrder(
            Input input,
            SpatialConstraint constraint,
            Map<Long, Position> positions) {
        if (constraint.participants().size() != 2 || constraint.direction() == null) {
            return Check.notEvaluable(
                    "RELATIVE_ORDER_INCOMPLETE",
                    "RELATIVE_ORDER requiere dos participantes y una dirección");
        }
        Position first = centerOf(
                input, constraint.participants().get(0), positions);
        Position second = centerOf(
                input, constraint.participants().get(1), positions);
        if (first == null || second == null) {
            return Check.notEvaluable(
                    "PARTICIPANT_POSITION_MISSING",
                    "No se pudo determinar el centro de un participante");
        }
        boolean vertical = constraint.direction() == Direction.FRONT
                || constraint.direction() == Direction.BACK;
        int comparison = vertical
                ? first.yCoordinate().compareTo(second.yCoordinate())
                : first.xCoordinate().compareTo(second.xCoordinate());
        if (comparison == 0) {
            return Check.violated(
                    vertical ? "RELATIVE_ORDER_ALIGNED_VERTICAL" : "RELATIVE_ORDER_ALIGNED",
                    vertical
                            ? "Los participantes están alineados en el eje delantero/trasero"
                            : "Los participantes están alineados en el eje de orden");
        }
        boolean satisfied = switch (constraint.direction()) {
            case LEFT, BACK -> comparison < 0;
            case RIGHT, FRONT -> comparison > 0;
        };
        return satisfied
                ? Check.satisfied(
                        "RELATIVE_ORDER_SATISFIED",
                        "Se cumple la dirección relativa solicitada")
                : Check.violated(
                        "RELATIVE_ORDER_REVERSED",
                        "La dirección relativa está invertida");
    }

    private Check evaluateSeparated(
            Input input,
            SpatialConstraint constraint,
            Map<Long, Position> positions) {
        List<ParticipantRef> compared = constraint.comparedParticipants();
        if (constraint.participants().isEmpty() || compared == null || compared.isEmpty()
                || constraint.threshold() == null) {
            return Check.notEvaluable(
                    "SEPARATED_CONDITION_INCOMPLETE",
                    "SEPARATED requiere dos conjuntos y un mínimo explícito");
        }
        BigDecimal globalThreshold = input.globalSeparationThreshold();
        if (globalThreshold == null || globalThreshold.signum() <= 0) {
            return Check.notEvaluable(
                    "GLOBAL_SEPARATION_THRESHOLD_MISSING",
                    "No hay un umbral global de separación versionado");
        }
        BigDecimal effectiveThreshold = constraint.threshold().max(globalThreshold);
        Check unresolved = unresolvedParticipant(
                input, constraint.participants());
        if (unresolved != null) {
            return unresolved;
        }
        unresolved = unresolvedParticipant(input, compared);
        if (unresolved != null) {
            return unresolved;
        }
        List<Position> first = resolvePositions(
                input, constraint.participants(), positions);
        List<Position> second = resolvePositions(input, compared, positions);
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return Check.notEvaluable(
                    "SEPARATION_MEMBERS_UNAVAILABLE",
                    "No se pudieron resolver todos los miembros de los conjuntos");
        }
        for (Position left : first) {
            for (Position right : second) {
                int comparison = compareDistance(left, right, effectiveThreshold);
                if (comparison < 0) {
                    return Check.violated(
                            "SEPARATION_MINIMUM_NOT_MET",
                            "Al menos un par cruzado no alcanza la separación mínima");
                }
            }
        }
        return Check.satisfied(
                "SEPARATION_MINIMUM_MET",
                "Todos los pares cruzados alcanzan la separación mínima efectiva");
    }

    private List<Position> resolvePositions(
            Input input,
            List<ParticipantRef> participants,
            Map<Long, Position> positions) {
        List<Position> result = new ArrayList<>();
        for (ParticipantRef participant : participants) {
            Resolution members = resolveMembers(
                    input.revision(), participant, input.resolvedGroupMembers());
            if (!members.evaluable()) {
                return null;
            }
            for (Long singerId : members.singerIds()) {
                Position position = positions.get(singerId);
                if (position == null) {
                    return null;
                }
                result.add(position);
            }
        }
        return result;
    }

    private Check unresolvedParticipant(
            Input input, List<ParticipantRef> participants) {
        for (ParticipantRef participant : participants) {
            Resolution resolution = resolveMembers(
                    input.revision(), participant, input.resolvedGroupMembers());
            if (!resolution.evaluable()) {
                return Check.notEvaluable(
                        resolution.code(), resolution.explanation());
            }
        }
        return null;
    }

    private Position centerOf(
            Input input,
            ParticipantRef participant,
            Map<Long, Position> positions) {
        Resolution members = resolveMembers(
                input.revision(), participant, input.resolvedGroupMembers());
        if (!members.evaluable() || members.singerIds().isEmpty()) {
            return null;
        }
        List<Position> memberPositions = new ArrayList<>();
        for (Long singerId : members.singerIds()) {
            Position position = positions.get(singerId);
            if (position == null) {
                return null;
            }
            memberPositions.add(position);
        }
        if (memberPositions.size() == 1) {
            return memberPositions.get(0);
        }
        BigDecimal sumX = ZERO;
        BigDecimal sumY = ZERO;
        for (Position position : memberPositions) {
            sumX = sumX.add(position.xCoordinate());
            sumY = sumY.add(position.yCoordinate());
        }
        return new Position(
                -1L,
                sumX.divide(BigDecimal.valueOf(memberPositions.size()), CENTER_CONTEXT),
                sumY.divide(BigDecimal.valueOf(memberPositions.size()), CENTER_CONTEXT));
    }

    private Resolution resolveMembers(
            SceneRuleRevisionSnapshot revision,
            ParticipantRef participant,
            Map<Integer, List<Long>> resolvedGroupMembers) {
        if (participant == null || participant.type() == null) {
            return Resolution.failure(
                    "PARTICIPANT_REFERENCE_INVALID",
                    "La referencia de participante está incompleta");
        }
        if (participant.type() == ParticipantType.SINGER) {
            if (participant.singerId() == null || participant.singerId() <= 0
                    || participant.groupOrder() != null) {
                return Resolution.failure(
                        "PARTICIPANT_REFERENCE_INVALID",
                        "La referencia individual está incompleta");
            }
            return Resolution.success(List.of(participant.singerId()));
        }
        if (participant.groupOrder() == null || participant.singerId() != null) {
            return Resolution.failure(
                    "PARTICIPANT_REFERENCE_INVALID",
                    "La referencia grupal está incompleta");
        }
        SceneRuleRevisionSnapshot.GroupSnapshot group = revision.groups().stream()
                .filter(candidate -> candidate.groupOrder() == participant.groupOrder())
                .findFirst()
                .orElse(null);
        if (group == null) {
            return Resolution.failure(
                    "GROUP_REFERENCE_MISSING",
                    "El grupo no existe en la revisión evaluada");
        }
        if ("IDENTIFIED".equals(group.compositionMode())) {
            if (group.singerIds().size() != group.memberCount()
                    || group.singerIds().isEmpty()) {
                return Resolution.failure(
                        "IDENTIFIED_GROUP_INCOMPLETE",
                        "La revisión no conserva todos los miembros identificados");
            }
            return Resolution.success(group.singerIds());
        }
        if ("COUNT".equals(group.compositionMode())) {
            List<Long> assignedMembers = resolvedGroupMembers == null
                    ? null
                    : resolvedGroupMembers.get(group.groupOrder());
            if (assignedMembers == null
                    || assignedMembers.size() != group.memberCount()
                    || assignedMembers.stream().anyMatch(
                            singerId -> singerId == null || singerId <= 0)
                    || new HashSet<>(assignedMembers).size() != assignedMembers.size()) {
                return Resolution.failure(
                        "COUNT_GROUP_ASSIGNMENT_UNAVAILABLE",
                        "La composición COUNT no tiene una asignación de planificación completa");
            }
            return Resolution.success(assignedMembers);
        }
        return Resolution.failure(
                "GROUP_COMPOSITION_INVALID",
                "El modo de composición del grupo no es reconocido");
    }

    private boolean hasBothMovementStages(SceneRuleCondition condition) {
        if (condition.spatialConstraints() == null) {
            return false;
        }
        boolean from = condition.spatialConstraints().stream()
                .anyMatch(constraint -> constraint != null
                        && constraint.stage() == Stage.FROM);
        boolean to = condition.spatialConstraints().stream()
                .anyMatch(constraint -> constraint != null
                        && constraint.stage() == Stage.TO);
        return from && to;
    }

    private Map<Long, Position> positionsBySinger(List<Position> positions) {
        Map<Long, Position> result = new HashMap<>();
        for (Position position : positions) {
            if (position == null || position.singerId() == null
                    || position.singerId() <= 0
                    || !validCoordinate(position.xCoordinate())
                    || !validCoordinate(position.yCoordinate())
                    || result.put(position.singerId(), position) != null) {
                return null;
            }
        }
        return result;
    }

    private boolean validCoordinate(BigDecimal coordinate) {
        return coordinate != null
                && coordinate.compareTo(ZERO) >= 0
                && coordinate.compareTo(ONE) <= 0;
    }

    private int compareDistance(Position first, Position second, BigDecimal threshold) {
        BigDecimal deltaX = first.xCoordinate().subtract(second.xCoordinate());
        BigDecimal deltaY = first.yCoordinate().subtract(second.yCoordinate());
        BigDecimal distanceSquared = deltaX.multiply(deltaX).add(deltaY.multiply(deltaY));
        return distanceSquared.compareTo(threshold.multiply(threshold));
    }

    private boolean hasDuplicateSinger(List<Position> positions) {
        Set<Long> ids = new HashSet<>();
        return positions.stream().anyMatch(position -> !ids.add(position.singerId()));
    }

    private Evaluation aggregate(
            SceneRuleRevisionSnapshot revision, List<Check> checks) {
        List<Warning> warnings = checks.stream()
                .filter(check -> check.status() != Status.SATISFIED)
                .map(check -> new Warning(
                        check.code(), check.explanation(), check.status()))
                .toList();
        if (checks.stream().anyMatch(check -> check.status() == Status.VIOLATED)) {
            return new Evaluation(
                    revision.sceneRuleId(),
                    revision.id(),
                    revision.ruleType(),
                    Status.VIOLATED,
                    "RULE_VIOLATED",
                    "Al menos una condición de la regla se incumple",
                    warnings);
        }
        if (!warnings.isEmpty()) {
            return new Evaluation(
                    revision.sceneRuleId(),
                    revision.id(),
                    revision.ruleType(),
                    Status.NOT_EVALUABLE,
                    "RULE_NOT_EVALUABLE",
                    "No se pueden comprobar todos los criterios de la regla",
                    warnings);
        }
        return new Evaluation(
                revision.sceneRuleId(),
                revision.id(),
                revision.ruleType(),
                Status.SATISFIED,
                "RULE_SATISFIED",
                "Todos los criterios de la regla se cumplen",
                List.of());
    }

    private Evaluation notEvaluable(
            SceneRuleRevisionSnapshot revision, String code, String explanation) {
        return new Evaluation(
                revision.sceneRuleId(),
                revision.id(),
                revision.ruleType(),
                Status.NOT_EVALUABLE,
                code,
                explanation,
                List.of(new Warning(code, explanation, Status.NOT_EVALUABLE)));
    }

    private Evaluation notEvaluable(String code, String explanation) {
        return new Evaluation(
                null,
                null,
                null,
                Status.NOT_EVALUABLE,
                code,
                explanation,
                List.of(new Warning(code, explanation, Status.NOT_EVALUABLE)));
    }

    private record Resolution(
            boolean evaluable, List<Long> singerIds, String code, String explanation) {
        private static Resolution success(List<Long> singerIds) {
            return new Resolution(true, List.copyOf(singerIds), null, null);
        }

        private static Resolution failure(String code, String explanation) {
            return new Resolution(false, List.of(), code, explanation);
        }
    }

    private record Check(Status status, String code, String explanation) {
        private static Check satisfied(String code, String explanation) {
            return new Check(Status.SATISFIED, code, explanation);
        }

        private static Check violated(String code, String explanation) {
            return new Check(Status.VIOLATED, code, explanation);
        }

        private static Check notEvaluable(String code, String explanation) {
            return new Check(Status.NOT_EVALUABLE, code, explanation);
        }
    }

    public enum Status {
        SATISFIED,
        VIOLATED,
        NOT_EVALUABLE
    }

    public record Input(
            SceneRuleRevisionSnapshot revision,
            long fromFormationVersionId,
            List<Position> fromPositions,
            long toFormationVersionId,
            List<Position> toPositions,
            Map<Integer, List<Long>> resolvedGroupMembers,
            BigDecimal globalSeparationThreshold) {
    }

    public record Position(Long singerId, BigDecimal xCoordinate, BigDecimal yCoordinate) {
    }

    public record Evaluation(
            Long sceneRuleId,
            Long ruleRevisionId,
            String ruleType,
            Status status,
            String code,
            String explanation,
            List<Warning> warnings) {
        public Evaluation {
            warnings = List.copyOf(warnings);
        }
    }

    public record Warning(String code, String explanation, Status status) {
    }
}
