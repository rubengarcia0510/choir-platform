package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.scene.application.port.out.SceneRuleHistoryPort;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Evaluation;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Input;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Position;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot;
import com.rubengarcia.choir.transition.application.port.out.SceneRuleSeparationSettingsPort;
import com.rubengarcia.choir.transition.application.port.out.TransitionPlanningPositionRepository;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.RuleResult;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class TransitionPlanningServiceImpl
        implements TransitionPlanningService {

    private final TransitionPlanningPositionRepository positionRepository;
    private final SceneRuleHistoryPort sceneRuleHistoryPort;
    private final SceneRuleSeparationSettingsPort settingsPort;
    private final TransitionRuleEvaluationHistoryPort evaluationHistoryPort;
    private final SceneRuleEvaluator evaluator = new SceneRuleEvaluator();

    public TransitionPlanningServiceImpl(
            TransitionPlanningPositionRepository positionRepository,
            SceneRuleHistoryPort sceneRuleHistoryPort,
            SceneRuleSeparationSettingsPort settingsPort,
            TransitionRuleEvaluationHistoryPort evaluationHistoryPort) {
        this.positionRepository = positionRepository;
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
    public Mono<TransitionRuleEvaluationSnapshot> evaluateTransitionVersion(
            long transitionId, long transitionVersionId) {
        return positionRepository.findPlanningContext(transitionVersionId)
                .filter(context -> context.transitionId() == transitionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La versión no pertenece a la transición o no tiene snapshots válidos")))
                .flatMap(context -> Mono.zip(
                                positionRepository
                                        .findByTransitionVersionId(transitionVersionId)
                                        .collectList(),
                                sceneRuleHistoryPort
                                        .findAllRevisionsByRepertoireItemId(
                                                context.repertoireItemId())
                                        .collectList(),
                                settingsPort.getCurrent())
                        .flatMap(data -> evaluateAndSave(context, data)));
    }

    @Override
    public Flux<TransitionRuleEvaluationSnapshot> evaluationHistory(
            long transitionId, long transitionVersionId) {
        return positionRepository.findPlanningContext(transitionVersionId)
                .filter(context -> context.transitionId() == transitionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La versión no pertenece a la transición")))
                .flatMapMany(context -> evaluationHistoryPort
                        .findAllByTransitionVersionId(
                                context.transitionVersionId()));
    }

    private Mono<TransitionRuleEvaluationSnapshot> evaluateAndSave(
            TransitionPlanningContext context,
            Tuple3<
                    List<TransitionPlanningPosition>,
                    List<SceneRuleRevisionSnapshot>,
                    SceneRuleSeparationSettingsPort.Setting> data) {
        List<SceneRuleRevisionSnapshot> activeRevisions =
                latestActiveRevisions(data.getT2());
        List<Position> fromPositions = positionsAt(
                data.getT1(), "FROM", context.fromFormationVersionId());
        List<Position> toPositions = positionsAt(
                data.getT1(), "TO", context.toFormationVersionId());
        Map<Integer, List<Long>> noCountAssignments = Map.of();
        List<RuleResult> results = new ArrayList<>();
        List<Warning> globalWarnings = List.of(new Warning(
                null,
                0,
                "DIRECTOR_VISIBILITY_NOT_EVALUABLE",
                "La visibilidad física del director no puede verificarse con la geometría relativa",
                Status.NOT_EVALUABLE));
        for (int order = 0; order < activeRevisions.size(); order++) {
            SceneRuleRevisionSnapshot revision = activeRevisions.get(order);
            Evaluation result = evaluator.evaluate(new Input(
                    revision,
                    context.fromFormationVersionId(),
                    fromPositions,
                    context.toFormationVersionId(),
                    toPositions,
                    noCountAssignments,
                    data.getT3().threshold()));
            int resultOrder = order;
            List<Warning> warnings = IntStream.range(0, result.warnings().size())
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
                        globalWarnings);
        return evaluationHistoryPort.save(snapshot);
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
            List<RuleResult> results, List<Warning> globalWarnings) {
        if (results.stream().anyMatch(result -> result.status() == Status.VIOLATED)) {
            return Status.VIOLATED;
        }
        if (!globalWarnings.isEmpty()
                || results.stream().anyMatch(
                        result -> result.status() == Status.NOT_EVALUABLE)) {
            return Status.NOT_EVALUABLE;
        }
        return Status.SATISFIED;
    }
}
