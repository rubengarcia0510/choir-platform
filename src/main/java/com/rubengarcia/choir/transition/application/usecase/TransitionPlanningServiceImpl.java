package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.scene.application.port.out.SceneRuleHistoryPort;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Evaluation;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Input;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Position;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.TemporalMovementStep;
import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot;
import com.rubengarcia.choir.transition.application.model.CountGroupAssignment;
import com.rubengarcia.choir.transition.application.model.TransitionMovementStepInput;
import com.rubengarcia.choir.transition.application.port.out.SceneRuleSeparationSettingsPort;
import com.rubengarcia.choir.transition.application.port.out.TransitionMovementStepRepository;
import com.rubengarcia.choir.transition.application.port.out.TransitionPlanningPositionRepository;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementCalculator;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import com.rubengarcia.choir.transition.domain.TransitionMovementStep;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.RuleResult;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning.PublicationImpact;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TemporalConflictStatus;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalyzer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

@Service
public class TransitionPlanningServiceImpl
        implements TransitionPlanningService {

    private final TransitionPlanningPositionRepository positionRepository;
    private final TransitionMovementStepRepository movementStepRepository;
    private final SceneRuleHistoryPort sceneRuleHistoryPort;
    private final SceneRuleSeparationSettingsPort settingsPort;
    private final TransitionRuleEvaluationHistoryPort evaluationHistoryPort;
    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();
    private final TransitionDisplacementCalculator displacementCalculator =
            new TransitionDisplacementCalculator();
    private final TransitionTrajectoryAnalyzer trajectoryAnalyzer =
            new TransitionTrajectoryAnalyzer();

    public TransitionPlanningServiceImpl(
            TransitionPlanningPositionRepository positionRepository,
            TransitionMovementStepRepository movementStepRepository,
            SceneRuleHistoryPort sceneRuleHistoryPort,
            SceneRuleSeparationSettingsPort settingsPort,
            TransitionRuleEvaluationHistoryPort evaluationHistoryPort) {
        this.positionRepository = positionRepository;
        this.movementStepRepository = movementStepRepository;
        this.sceneRuleHistoryPort = sceneRuleHistoryPort;
        this.settingsPort = settingsPort;
        this.evaluationHistoryPort = evaluationHistoryPort;
    }

    @Override
    public Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId) {
        return positionRepository.findByTransitionVersionId(transitionVersionId);
    }

    @Override
    public Flux<TransitionMovementStep> movementStepsForTransitionVersion(
            long transitionId, long transitionVersionId) {
        return findPlanningContext(transitionId, transitionVersionId)
                .flatMapMany(context -> movementStepRepository
                        .findByTransitionVersionId(context.transitionVersionId()));
    }

    @Override
    public Mono<List<TransitionMovementStep>> replaceMovementSteps(
            long transitionId,
            long transitionVersionId,
            List<TransitionMovementStepInput> steps) {
        return findPlanningContext(transitionId, transitionVersionId)
                .flatMap(context -> {
                    List<TransitionMovementStepInput> safeSteps =
                            steps == null ? null : new ArrayList<>(steps);
                    return validateMovementSteps(safeSteps)
                            .then(Mono.defer(() -> {
                                List<TransitionMovementStep> captured =
                                        safeSteps.stream()
                                                .map(step -> new TransitionMovementStep(
                                                        context.transitionId(),
                                                        context.transitionVersionId(),
                                                        context.fromFormationVersionId(),
                                                        context.toFormationVersionId(),
                                                        step.singerId(),
                                                        step.sequenceNumber(),
                                                        step.temporalReference()
                                                                .withOffsetSameInstant(
                                                                        ZoneOffset.UTC),
                                                        step.action().trim()))
                                                .toList();
                                return movementStepRepository
                                        .replaceForDraftVersion(context, captured);
                            }));
                });
    }

    @Override
    public Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            long transitionId,
            long transitionVersionId) {
        return evaluateTransitionVersion(
                transitionId,
                transitionVersionId,
                List.of());
    }

    @Override
    public Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            long transitionId,
            long transitionVersionId,
            List<CountGroupAssignment> assignments) {
        List<CountGroupAssignment> safeAssignments =
                assignments == null ? List.of() : new ArrayList<>(assignments);

        return findPlanningContext(transitionId, transitionVersionId)
                .flatMap(context -> Mono.zip(
                                positionRepository
                                        .findByTransitionVersionId(transitionVersionId)
                                        .collectList(),
                                sceneRuleHistoryPort
                                        .findAllRevisionsByRepertoireItemId(
                                                context.repertoireItemId())
                                        .collectList(),
                                settingsPort.getCurrent(),
                                movementStepRepository
                                        .findByTransitionVersionId(transitionVersionId)
                                        .collectList())
                        .flatMap(data -> {
                            TransitionDisplacementMetrics displacementMetrics =
                                    displacementCalculator.calculate(context, data.getT1());
                            TransitionTrajectoryAnalysis trajectoryAnalysis =
                                    trajectoryAnalyzer.analyze(
                                            context, data.getT1(), data.getT4());
                            return evaluateAndSave(
                                    context,
                                    data,
                                    safeAssignments,
                                    displacementMetrics,
                                    trajectoryAnalysis);
                        }));
    }

    @Override
    public Flux<TransitionRuleEvaluationSnapshot> evaluationHistory(
            long transitionId,
            long transitionVersionId) {
        return findPlanningContext(transitionId, transitionVersionId)
                .flatMapMany(context -> evaluationHistoryPort
                        .findAllByTransitionVersionId(
                                context.transitionVersionId()));
    }

    private Mono<TransitionRuleEvaluationSnapshot> evaluateAndSave(
            TransitionPlanningContext context,
            Tuple4<
                    List<TransitionPlanningPosition>,
                    List<SceneRuleRevisionSnapshot>,
                    SceneRuleSeparationSettingsPort.Setting,
                    List<TransitionMovementStep>> data,
            List<CountGroupAssignment> assignments,
            TransitionDisplacementMetrics displacementMetrics,
            TransitionTrajectoryAnalysis trajectoryAnalysis) {

        List<SceneRuleRevisionSnapshot> activeRevisions =
                latestActiveRevisions(data.getT2());

        List<Position> fromPositions = positionsAt(
                data.getT1(),
                "FROM",
                context.fromFormationVersionId());

        List<Position> toPositions = positionsAt(
                data.getT1(),
                "TO",
                context.toFormationVersionId());

        List<RuleResult> results = new ArrayList<>();
        List<TemporalMovementStep> temporalSteps = data.getT4().stream()
                .map(step -> new TemporalMovementStep(
                        step.transitionId(),
                        step.transitionVersionId(),
                        step.fromFormationVersionId(),
                        step.toFormationVersionId(),
                        step.singerId(),
                        step.sequenceNumber(),
                        step.temporalReference(),
                        step.action()))
                .toList();

        List<Warning> globalWarnings = new ArrayList<>(List.of(new Warning(
                null,
                0,
                "DIRECTOR_VISIBILITY_NOT_EVALUABLE",
                "La visibilidad física del director no puede verificarse con la geometría relativa",
                Status.NOT_EVALUABLE,
                PublicationImpact.INFORMATIONAL)));
        addDisplacementWarnings(globalWarnings, displacementMetrics);
        addTrajectoryWarnings(globalWarnings, trajectoryAnalysis);

        for (int order = 0; order < activeRevisions.size(); order++) {
            SceneRuleRevisionSnapshot revision = activeRevisions.get(order);

            Map<Integer, List<Long>> revisionAssignments =
                    assignmentsForRevision(revision, assignments);

            Evaluation result = evaluator.evaluate(new Input(
                    revision,
                    context.fromFormationVersionId(),
                    fromPositions,
                    context.toFormationVersionId(),
                    toPositions,
                    revisionAssignments,
                    data.getT3().threshold(),
                    context.transitionId(),
                    context.transitionVersionId(),
                    temporalSteps));

            int resultOrder = order;

            List<Warning> warnings = IntStream
                    .range(0, result.warnings().size())
                    .mapToObj(warningOrder -> {
                        var warning = result.warnings().get(warningOrder);
                        return new Warning(
                                resultOrder,
                                warningOrder,
                                warning.code(),
                                warning.explanation(),
                                warning.status());
                    })
                    .toList();

            results.add(new RuleResult(
                    order,
                    revision.sceneRuleId(),
                    revision.id(),
                    revision.ruleType(),
                    result.status(),
                    result.code(),
                    result.explanation(),
                    warnings));
        }

        Status overallStatus = overallStatus(results, globalWarnings);

        TransitionRuleEvaluationSnapshot snapshot =
                new TransitionRuleEvaluationSnapshot(
                        null,
                        context.transitionVersionId(),
                        context.transitionId(),
                        context.fromFormationVersionId(),
                        context.toFormationVersionId(),
                        context.repertoireItemId(),
                        data.getT3().revision(),
                        data.getT3().threshold(),
                        overallStatus,
                        LocalDateTime.now(),
                        results,
                        globalWarnings,
                        data.getT4(),
                        displacementMetrics,
                        trajectoryAnalysis);

        return evaluationHistoryPort.save(snapshot);
    }

    private Mono<TransitionPlanningContext> findPlanningContext(
            long transitionId, long transitionVersionId) {
        if (transitionId <= 0 || transitionVersionId <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los identificadores de transición y versión deben ser positivos"));
        }
        return positionRepository.findPlanningContext(transitionVersionId)
                .filter(context -> context.transitionId() == transitionId
                        && context.transitionVersionId() == transitionVersionId
                        && context.fromFormationVersionId() > 0
                        && context.toFormationVersionId() > 0
                        && context.fromFormationVersionId()
                                != context.toFormationVersionId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La versión no pertenece a la transición o no tiene snapshots válidos")));
    }

    private Mono<Void> validateMovementSteps(
            List<TransitionMovementStepInput> steps) {
        if (steps == null || steps.size() > 500
                || steps.stream().anyMatch(step -> step == null
                        || step.singerId() <= 0
                        || step.sequenceNumber() <= 0
                        || step.temporalReference() == null
                        || step.action() == null
                        || step.action().isBlank()
                        || step.action().length() > 100)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los pasos requieren participantes, secuencias y acciones válidas"));
        }
        Set<String> uniqueParticipantsBySequence = new HashSet<>();
        if (steps.stream().anyMatch(step -> !uniqueParticipantsBySequence.add(
                step.sequenceNumber() + ":" + step.singerId()))) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Un participante no puede aparecer más de una vez en el mismo paso"));
        }
        return Mono.empty();
    }

    private Map<Integer, List<Long>> assignmentsForRevision(
            SceneRuleRevisionSnapshot revision,
            List<CountGroupAssignment> assignments) {

        Map<Integer, List<Long>> resolved = new HashMap<>();
        Set<Integer> duplicateGroupOrders = new HashSet<>();

        for (CountGroupAssignment assignment : assignments) {
            if (assignment == null
                    || assignment.ruleRevisionId() != revision.id()) {
                continue;
            }

            int groupOrder = assignment.groupOrder();

            if (resolved.containsKey(groupOrder)) {
                resolved.remove(groupOrder);
                duplicateGroupOrders.add(groupOrder);
                continue;
            }

            if (!duplicateGroupOrders.contains(groupOrder)) {
                resolved.put(groupOrder, assignment.singerIds());
            }
        }

        return resolved;
    }

    private List<SceneRuleRevisionSnapshot> latestActiveRevisions(
            List<SceneRuleRevisionSnapshot> revisions) {
        Map<Long, SceneRuleRevisionSnapshot> latestByRule = new HashMap<>();

        for (SceneRuleRevisionSnapshot revision : revisions) {
            latestByRule.merge(
                    revision.sceneRuleId(),
                    revision,
                    (current, candidate) -> candidate.revisionNumber()
                                    > current.revisionNumber()
                            ? candidate
                            : current);
        }

        return latestByRule.values().stream()
                .filter(SceneRuleRevisionSnapshot::active)
                .sorted(Comparator.comparingInt(
                                SceneRuleRevisionSnapshot::ruleOrder)
                        .thenComparingLong(
                                SceneRuleRevisionSnapshot::sceneRuleId))
                .toList();
    }

    private List<Position> positionsAt(
            List<TransitionPlanningPosition> positions,
            String stage,
            long formationVersionId) {
        return positions.stream()
                .filter(position -> stage.equals(position.stage())
                        && position.formationVersionId() == formationVersionId)
                .map(position -> new Position(
                        position.singerId(),
                        position.xCoordinate(),
                        position.yCoordinate()))
                .toList();
    }

    private Status overallStatus(
            List<RuleResult> results,
            List<Warning> globalWarnings) {
        if (results.stream().anyMatch(
                result -> result.status() == Status.VIOLATED)
                || globalWarnings.stream().anyMatch(
                        warning -> warning.status() == Status.VIOLATED)) {
            return Status.VIOLATED;
        }

        if (!globalWarnings.isEmpty()
                || results.stream().anyMatch(
                        result -> result.status() == Status.NOT_EVALUABLE)) {
            return Status.NOT_EVALUABLE;
        }

        return Status.SATISFIED;
    }

    private void addDisplacementWarnings(
            List<Warning> warnings,
            TransitionDisplacementMetrics metrics) {
        for (var issue : metrics.dataIssues()) {
            warnings.add(new Warning(
                    null,
                    warnings.size(),
                    "DISPLACEMENT_" + issue.code().name(),
                    "Datos de desplazamiento incompletos para " + issue.singerId()
                            + " en " + issue.stage(),
                    Status.NOT_EVALUABLE));
        }
    }

    private void addTrajectoryWarnings(
            List<Warning> warnings,
            TransitionTrajectoryAnalysis analysis) {
        for (var issue : analysis.geometryIssues()) {
            warnings.add(new Warning(
                    null,
                    warnings.size(),
                    "TRAJECTORY_" + issue.code().name(),
                    "Datos geométricos incompletos para " + issue.singerId()
                            + " en " + issue.stage(),
                    Status.NOT_EVALUABLE));
        }
        for (var crossing : analysis.crossings()) {
            TemporalConflictStatus temporalStatus =
                    crossing.temporalConflictStatus();
            Status warningStatus = switch (temporalStatus) {
                case CONFIRMED -> Status.VIOLATED;
                case NOT_CONCURRENT -> Status.SATISFIED;
                case NOT_EVALUABLE -> Status.NOT_EVALUABLE;
            };
            String code = switch (temporalStatus) {
                case CONFIRMED -> "TRAJECTORY_CONFLICT_CONFIRMED";
                case NOT_CONCURRENT -> "TRAJECTORY_CROSSING_NOT_CONCURRENT";
                case NOT_EVALUABLE -> "TRAJECTORY_CONFLICT_NOT_EVALUABLE";
            };
            warnings.add(new Warning(
                    null,
                    warnings.size(),
                    code,
                    "Cruce geométrico entre coreutas "
                            + crossing.firstSingerId() + " y "
                            + crossing.secondSingerId() + ": " + temporalStatus,
                    warningStatus));
        }
    }
}
