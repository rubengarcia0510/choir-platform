package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.domain.TransitionMovementStep;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.DataIssue;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.DataIssueCode;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.SingerDistance;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics.Stage;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.RuleResult;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.GeometryIssue;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.GeometryIssueCode;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TemporalConflictStatus;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis.TrajectoryFinding;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TransitionRuleEvaluationHistoryAdapter
        implements TransitionRuleEvaluationHistoryPort {

    private final DatabaseClient database;

    public TransitionRuleEvaluationHistoryAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TransitionRuleEvaluationSnapshot> save(
            TransitionRuleEvaluationSnapshot snapshot) {
        return insertEvaluation(snapshot)
                .flatMap(saved -> saveResults(saved.id(), snapshot.results())
                        .then(saveWarnings(saved.id(), allWarnings(snapshot)))
                        .then(saveMovementSteps(
                                saved.id(), snapshot.movementSteps()))
                        .then(saveDisplacementMetrics(
                                saved.id(), snapshot.displacementMetrics()))
                        .then(saveTrajectoryAnalysis(
                                saved.id(), snapshot.trajectoryAnalysis()))
                        .thenReturn(new TransitionRuleEvaluationSnapshot(
                                saved.id(),
                                snapshot.transitionVersionId(),
                                snapshot.transitionId(),
                                snapshot.fromFormationVersionId(),
                                snapshot.toFormationVersionId(),
                                snapshot.repertoireItemId(),
                                snapshot.separationSettingsRevision(),
                                snapshot.globalSeparationThreshold(),
                                snapshot.status(),
                                snapshot.evaluatedAt(),
                                snapshot.results(),
                                snapshot.warnings(),
                                snapshot.movementSteps(),
                                snapshot.displacementMetrics(),
                                snapshot.trajectoryAnalysis())));
    }

    @Override
    public Flux<TransitionRuleEvaluationSnapshot> findAllByTransitionVersionId(
            long transitionVersionId) {
        return database.sql("""
                SELECT id, transition_version_id, transition_id,
                       from_formation_version_id, to_formation_version_id,
                       repertoire_item_id, separation_settings_revision,
                       global_separation_threshold, status, evaluated_at
                FROM transition_rule_evaluations
                WHERE transition_version_id = :transitionVersionId
                ORDER BY id
                """)
                .bind("transitionVersionId", transitionVersionId)
                .map((row, metadata) -> new EvaluationRow(
                        row.get("id", Long.class),
                        row.get("transition_version_id", Long.class),
                        row.get("transition_id", Long.class),
                        row.get("from_formation_version_id", Long.class),
                        row.get("to_formation_version_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("separation_settings_revision", Long.class),
                        row.get("global_separation_threshold", BigDecimal.class),
                        Status.valueOf(row.get("status", String.class)),
                        row.get("evaluated_at", LocalDateTime.class)))
                .all()
                .concatMap(this::loadEvaluation);
    }

    @Override
    public Mono<TransitionRuleEvaluationSnapshot> findById(long evaluationId) {
        return database.sql("""
                SELECT id, transition_version_id, transition_id,
                       from_formation_version_id, to_formation_version_id,
                       repertoire_item_id, separation_settings_revision,
                       global_separation_threshold, status, evaluated_at
                FROM transition_rule_evaluations
                WHERE id = :evaluationId
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new EvaluationRow(
                        row.get("id", Long.class),
                        row.get("transition_version_id", Long.class),
                        row.get("transition_id", Long.class),
                        row.get("from_formation_version_id", Long.class),
                        row.get("to_formation_version_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("separation_settings_revision", Long.class),
                        row.get("global_separation_threshold", BigDecimal.class),
                        Status.valueOf(row.get("status", String.class)),
                        row.get("evaluated_at", LocalDateTime.class)))
                .one()
                .flatMap(this::loadEvaluation);
    }

    private Mono<TransitionRuleEvaluationSnapshot> loadEvaluation(
            EvaluationRow evaluation) {
        return Mono.zip(
                        loadResults(evaluation.id()),
                        loadWarnings(evaluation.id()),
                        loadMovementSteps(evaluation),
                        loadDisplacementMetrics(evaluation),
                        loadTrajectoryAnalysis(evaluation))
                .map(data -> assembleEvaluation(
                        evaluation,
                        data.getT1(),
                        data.getT2(),
                        data.getT3(),
                        data.getT4().orElse(null),
                        data.getT5().orElse(null)));
    }

    private Mono<List<TransitionMovementStep>> loadMovementSteps(
            EvaluationRow evaluation) {
        return database.sql("""
                SELECT transition_id, transition_version_id,
                       from_formation_version_id, to_formation_version_id,
                       singer_id, sequence_number, temporal_reference, action
                FROM transition_rule_evaluation_movement_steps
                WHERE evaluation_id = :evaluationId
                ORDER BY sequence_number, singer_id
                """)
                .bind("evaluationId", evaluation.id())
                .map((row, metadata) -> new TransitionMovementStep(
                        row.get("transition_id", Long.class),
                        row.get("transition_version_id", Long.class),
                        row.get("from_formation_version_id", Long.class),
                        row.get("to_formation_version_id", Long.class),
                        row.get("singer_id", Long.class),
                        row.get("sequence_number", Integer.class),
                        row.get("temporal_reference", OffsetDateTime.class),
                        row.get("action", String.class)))
                .all()
                .collectList();
    }

    private Mono<Optional<TransitionDisplacementMetrics>> loadDisplacementMetrics(
            EvaluationRow evaluation) {
        long evaluationId = evaluation.id();
        Mono<Optional<DisplacementMetricsRow>> metrics = database.sql("""
                SELECT total_distance, max_individual_distance,
                       moving_singer_count
                FROM transition_rule_evaluation_displacement_metrics
                WHERE evaluation_id = :evaluationId
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new DisplacementMetricsRow(
                        row.get("total_distance", BigDecimal.class),
                        row.get("max_individual_distance", BigDecimal.class),
                        row.get("moving_singer_count", Integer.class)))
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return metrics.flatMap(optional -> {
            if (optional.isEmpty()) {
                return Mono.just(Optional.empty());
            }
            return Mono.zip(
                            loadSingerDistances(evaluationId),
                            loadDisplacementIssues(evaluationId))
                    .map(data -> Optional.of(new TransitionDisplacementMetrics(
                            evaluation.transitionVersionId(),
                            evaluation.fromFormationVersionId(),
                            evaluation.toFormationVersionId(),
                            data.getT1(),
                            optional.get().totalDistance(),
                            optional.get().maxIndividualDistance(),
                            optional.get().movingSingerCount(),
                            data.getT2())));
        });
    }

    private Mono<List<SingerDistance>> loadSingerDistances(long evaluationId) {
        return database.sql("""
                SELECT singer_id, distance
                FROM transition_rule_evaluation_singer_distances
                WHERE evaluation_id = :evaluationId
                ORDER BY singer_id
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new SingerDistance(
                        row.get("singer_id", Long.class),
                        row.get("distance", BigDecimal.class)))
                .all()
                .collectList();
    }

    private Mono<List<DataIssue>> loadDisplacementIssues(long evaluationId) {
        return database.sql("""
                SELECT issue_code, stage, singer_id
                FROM transition_rule_evaluation_displacement_issues
                WHERE evaluation_id = :evaluationId
                ORDER BY issue_order
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new DataIssue(
                        DataIssueCode.valueOf(row.get("issue_code", String.class)),
                        Stage.valueOf(row.get("stage", String.class)),
                        row.get("singer_id", Long.class)))
                .all()
                .collectList();
    }

    private Mono<Optional<TransitionTrajectoryAnalysis>> loadTrajectoryAnalysis(
            EvaluationRow evaluation) {
        long evaluationId = evaluation.id();
        Mono<Optional<Long>> transitionVersionId = database.sql("""
                SELECT transition_version_id
                FROM transition_rule_evaluation_trajectory_analyses
                WHERE evaluation_id = :evaluationId
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> row.get("transition_version_id", Long.class))
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return transitionVersionId.flatMap(optional -> {
            if (optional.isEmpty()) {
                return Mono.just(Optional.empty());
            }
            return Mono.zip(
                            loadTrajectoryCrossings(evaluationId),
                            loadTrajectoryIssues(evaluationId))
                    .map(data -> Optional.of(new TransitionTrajectoryAnalysis(
                            evaluation.transitionVersionId(),
                            data.getT1(),
                            data.getT2())));
        });
    }

    private Mono<List<TrajectoryFinding>> loadTrajectoryCrossings(
            long evaluationId) {
        return database.sql("""
                SELECT first_singer_id, second_singer_id,
                       temporal_conflict_status
                FROM transition_rule_evaluation_trajectory_crossings
                WHERE evaluation_id = :evaluationId
                ORDER BY first_singer_id, second_singer_id
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new TrajectoryFinding(
                        row.get("first_singer_id", Long.class),
                        row.get("second_singer_id", Long.class),
                        TemporalConflictStatus.valueOf(
                                row.get("temporal_conflict_status", String.class))))
                .all()
                .collectList();
    }

    private Mono<List<GeometryIssue>> loadTrajectoryIssues(long evaluationId) {
        return database.sql("""
                SELECT issue_code, stage, singer_id
                FROM transition_rule_evaluation_trajectory_issues
                WHERE evaluation_id = :evaluationId
                ORDER BY issue_order
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new GeometryIssue(
                        GeometryIssueCode.valueOf(row.get("issue_code", String.class)),
                        row.get("stage", String.class),
                        row.get("singer_id", Long.class)))
                .all()
                .collectList();
    }

    private Mono<List<ResultRow>> loadResults(long evaluationId) {
        return database.sql("""
                SELECT result_order, scene_rule_id, rule_revision_id,
                       rule_type, status, code, explanation
                FROM transition_rule_evaluation_results
                WHERE evaluation_id = :evaluationId
                ORDER BY result_order
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new ResultRow(
                        row.get("result_order", Integer.class),
                        row.get("scene_rule_id", Long.class),
                        row.get("rule_revision_id", Long.class),
                        row.get("rule_type", String.class),
                        Status.valueOf(row.get("status", String.class)),
                        row.get("code", String.class),
                        row.get("explanation", String.class)))
                .all()
                .collectList();
    }

    private Mono<List<Warning>> loadWarnings(long evaluationId) {
        return database.sql("""
                SELECT result_order, warning_order, code, explanation, status
                FROM transition_rule_evaluation_warnings
                WHERE evaluation_id = :evaluationId
                ORDER BY result_order, warning_order
                """)
                .bind("evaluationId", evaluationId)
                .map((row, metadata) -> new Warning(
                        row.get("result_order", Integer.class),
                        row.get("warning_order", Integer.class),
                        row.get("code", String.class),
                        row.get("explanation", String.class),
                        Status.valueOf(row.get("status", String.class))))
                .all()
                .collectList();
    }

    private TransitionRuleEvaluationSnapshot assembleEvaluation(
            EvaluationRow evaluation,
            List<ResultRow> resultRows,
            List<Warning> warnings,
            List<TransitionMovementStep> movementSteps,
            TransitionDisplacementMetrics displacementMetrics,
            TransitionTrajectoryAnalysis trajectoryAnalysis) {
        List<RuleResult> results = resultRows.stream()
                .map(result -> new RuleResult(
                        result.order(),
                        result.sceneRuleId(),
                        result.ruleRevisionId(),
                        result.ruleType(),
                        result.status(),
                        result.code(),
                        result.explanation(),
                        warnings.stream()
                                .filter(warning -> Objects.equals(
                                        warning.resultOrder(), result.order()))
                                .sorted(Comparator.comparingInt(
                                        Warning::warningOrder))
                                .toList()))
                .toList();
        List<Warning> globalWarnings = warnings.stream()
                .filter(warning -> warning.resultOrder() == null)
                .sorted(Comparator.comparingInt(Warning::warningOrder))
                .toList();
        return new TransitionRuleEvaluationSnapshot(
                evaluation.id(),
                evaluation.transitionVersionId(),
                evaluation.transitionId(),
                evaluation.fromFormationVersionId(),
                evaluation.toFormationVersionId(),
                evaluation.repertoireItemId(),
                evaluation.separationSettingsRevision(),
                evaluation.globalSeparationThreshold(),
                evaluation.status(),
                evaluation.evaluatedAt(),
                results,
                globalWarnings,
                movementSteps,
                displacementMetrics,
                trajectoryAnalysis);
    }

    private Mono<InsertedEvaluation> insertEvaluation(
            TransitionRuleEvaluationSnapshot snapshot) {
        return database.sql("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (
                    :transitionVersionId, :transitionId,
                    :fromFormationVersionId, :toFormationVersionId,
                    :repertoireItemId, :settingsRevision,
                    :globalThreshold, :status
                )
                """)
                .bind("transitionVersionId", snapshot.transitionVersionId())
                .bind("transitionId", snapshot.transitionId())
                .bind("fromFormationVersionId", snapshot.fromFormationVersionId())
                .bind("toFormationVersionId", snapshot.toFormationVersionId())
                .bind("repertoireItemId", snapshot.repertoireItemId())
                .bind("settingsRevision", snapshot.separationSettingsRevision())
                .bind("globalThreshold", snapshot.globalSeparationThreshold())
                .bind("status", snapshot.status().name())
                .filter((statement, executeFunction) ->
                        executeFunction.execute(statement.returnGeneratedValues("id")))
                .map((row, metadata) -> new InsertedEvaluation(
                        row.get("id", Long.class)))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No se pudo crear el historial de evaluación")));
    }

    private Mono<Void> saveResults(long evaluationId, List<RuleResult> results) {
        return Flux.fromIterable(results)
                .concatMap(result -> database.sql("""
                        INSERT INTO transition_rule_evaluation_results (
                            evaluation_id, result_order, scene_rule_id,
                            rule_revision_id, rule_type, status, code, explanation
                        ) VALUES (
                            :evaluationId, :resultOrder, :sceneRuleId,
                            :ruleRevisionId, :ruleType, :status, :code, :explanation
                        )
                        """)
                        .bind("evaluationId", evaluationId)
                        .bind("resultOrder", result.order())
                        .bind("sceneRuleId", result.sceneRuleId())
                        .bind("ruleRevisionId", result.ruleRevisionId())
                        .bind("ruleType", result.ruleType())
                        .bind("status", result.status().name())
                        .bind("code", result.code())
                        .bind("explanation", result.explanation())
                        .fetch()
                        .rowsUpdated()
                        .flatMap(rows -> rows == 1
                                ? Mono.empty()
                                : Mono.error(new IllegalStateException(
                                        "No se pudo guardar un resultado de evaluación"))))
                .then();
    }

    private Mono<Void> saveMovementSteps(
            long evaluationId,
            List<TransitionMovementStep> movementSteps) {
        return Flux.fromIterable(movementSteps)
                .concatMap(step -> database.sql("""
                        INSERT INTO transition_rule_evaluation_movement_steps (
                            evaluation_id, transition_version_id, transition_id,
                            from_formation_version_id, to_formation_version_id,
                            singer_id, sequence_number, temporal_reference, action
                        ) VALUES (
                            :evaluationId, :transitionVersionId, :transitionId,
                            :fromVersionId, :toVersionId, :singerId,
                            :sequenceNumber, :temporalReference, :action
                        )
                        """)
                        .bind("evaluationId", evaluationId)
                        .bind("transitionVersionId", step.transitionVersionId())
                        .bind("transitionId", step.transitionId())
                        .bind("fromVersionId", step.fromFormationVersionId())
                        .bind("toVersionId", step.toFormationVersionId())
                        .bind("singerId", step.singerId())
                        .bind("sequenceNumber", step.sequenceNumber())
                        .bind("temporalReference", step.temporalReference())
                        .bind("action", step.action())
                        .fetch()
                        .rowsUpdated()
                        .flatMap(rows -> rows == 1
                                ? Mono.empty()
                                : Mono.error(new IllegalStateException(
                                        "No se pudo guardar la evidencia temporal de evaluación"))))
                .then();
    }

    private Mono<Void> saveDisplacementMetrics(
            long evaluationId,
            TransitionDisplacementMetrics metrics) {
        if (metrics == null) {
            return Mono.empty();
        }
        return database.sql("""
                INSERT INTO transition_rule_evaluation_displacement_metrics (
                    evaluation_id, total_distance, max_individual_distance,
                    moving_singer_count
                ) VALUES (
                    :evaluationId, :totalDistance, :maxDistance, :movingCount
                )
                """)
                .bind("evaluationId", evaluationId)
                .bind("totalDistance", metrics.totalDistance())
                .bind("maxDistance", metrics.maxIndividualDistance())
                .bind("movingCount", metrics.movingSingerCount())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? saveSingerDistances(evaluationId, metrics.singerDistances())
                                .then(saveDisplacementIssues(
                                        evaluationId, metrics.dataIssues()))
                        : Mono.error(new IllegalStateException(
                                "No se pudieron guardar las métricas de desplazamiento")));
    }

    private Mono<Void> saveSingerDistances(
            long evaluationId,
            List<SingerDistance> distances) {
        return Flux.fromIterable(distances)
                .concatMap(distance -> database.sql("""
                        INSERT INTO transition_rule_evaluation_singer_distances (
                            evaluation_id, singer_id, distance
                        ) VALUES (:evaluationId, :singerId, :distance)
                        """)
                        .bind("evaluationId", evaluationId)
                        .bind("singerId", distance.singerId())
                        .bind("distance", distance.distance())
                        .fetch()
                        .rowsUpdated()
                        .flatMap(rows -> rows == 1
                                ? Mono.empty()
                                : Mono.error(new IllegalStateException(
                                        "No se pudo guardar una distancia individual"))))
                .then();
    }

    private Mono<Void> saveDisplacementIssues(
            long evaluationId,
            List<DataIssue> issues) {
        return Flux.range(0, issues.size())
                .concatMap(index -> {
                    DataIssue issue = issues.get(index);
                    DatabaseClient.GenericExecuteSpec insert = database.sql("""
                            INSERT INTO transition_rule_evaluation_displacement_issues (
                                evaluation_id, issue_order, issue_code, stage, singer_id
                            ) VALUES (
                                :evaluationId, :issueOrder, :issueCode, :stage, :singerId
                            )
                            """)
                            .bind("evaluationId", evaluationId)
                            .bind("issueOrder", index)
                            .bind("issueCode", issue.code().name())
                            .bind("stage", issue.stage().name());
                    insert = issue.singerId() == null
                            ? insert.bindNull("singerId", Long.class)
                            : insert.bind("singerId", issue.singerId());
                    return insert.fetch().rowsUpdated().flatMap(rows -> rows == 1
                            ? Mono.empty()
                            : Mono.error(new IllegalStateException(
                                    "No se pudo guardar un problema de desplazamiento")));
                })
                .then();
    }

    private Mono<Void> saveTrajectoryAnalysis(
            long evaluationId,
            TransitionTrajectoryAnalysis analysis) {
        if (analysis == null) {
            return Mono.empty();
        }
        return database.sql("""
                INSERT INTO transition_rule_evaluation_trajectory_analyses (
                    evaluation_id, transition_version_id
                ) VALUES (:evaluationId, :transitionVersionId)
                """)
                .bind("evaluationId", evaluationId)
                .bind("transitionVersionId", analysis.transitionVersionId())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? saveTrajectoryCrossings(evaluationId, analysis.crossings())
                                .then(saveTrajectoryIssues(
                                        evaluationId, analysis.geometryIssues()))
                        : Mono.error(new IllegalStateException(
                                "No se pudo guardar el análisis de trayectorias")));
    }

    private Mono<Void> saveTrajectoryCrossings(
            long evaluationId,
            List<TrajectoryFinding> crossings) {
        return Flux.fromIterable(crossings)
                .concatMap(crossing -> database.sql("""
                        INSERT INTO transition_rule_evaluation_trajectory_crossings (
                            evaluation_id, first_singer_id, second_singer_id,
                            temporal_conflict_status
                        ) VALUES (
                            :evaluationId, :firstSingerId, :secondSingerId, :status
                        )
                        """)
                        .bind("evaluationId", evaluationId)
                        .bind("firstSingerId", crossing.firstSingerId())
                        .bind("secondSingerId", crossing.secondSingerId())
                        .bind("status", crossing.temporalConflictStatus().name())
                        .fetch()
                        .rowsUpdated()
                        .flatMap(rows -> rows == 1
                                ? Mono.empty()
                                : Mono.error(new IllegalStateException(
                                        "No se pudo guardar un cruce de trayectorias"))))
                .then();
    }

    private Mono<Void> saveTrajectoryIssues(
            long evaluationId,
            List<GeometryIssue> issues) {
        return Flux.range(0, issues.size())
                .concatMap(index -> {
                    GeometryIssue issue = issues.get(index);
                    DatabaseClient.GenericExecuteSpec insert = database.sql("""
                            INSERT INTO transition_rule_evaluation_trajectory_issues (
                                evaluation_id, issue_order, issue_code, stage, singer_id
                            ) VALUES (
                                :evaluationId, :issueOrder, :issueCode, :stage, :singerId
                            )
                            """)
                            .bind("evaluationId", evaluationId)
                            .bind("issueOrder", index)
                            .bind("issueCode", issue.code().name())
                            .bind("stage", issue.stage());
                    insert = issue.singerId() == null
                            ? insert.bindNull("singerId", Long.class)
                            : insert.bind("singerId", issue.singerId());
                    return insert.fetch().rowsUpdated().flatMap(rows -> rows == 1
                            ? Mono.empty()
                            : Mono.error(new IllegalStateException(
                                    "No se pudo guardar un problema geométrico")));
                })
                .then();
    }

    private Mono<Void> saveWarnings(long evaluationId, List<Warning> warnings) {
        return Flux.fromIterable(warnings)
                .concatMap(warning -> insertWarning(evaluationId, warning))
                .then();
    }

    private Mono<Void> insertWarning(long evaluationId, Warning warning) {
        DatabaseClient.GenericExecuteSpec insert = database.sql("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, warning_order,
                    code, explanation, status
                ) VALUES (
                    :evaluationId, :resultOrder, :warningOrder,
                    :code, :explanation, :status
                )
                """)
                .bind("evaluationId", evaluationId)
                .bind("warningOrder", warning.warningOrder())
                .bind("code", warning.code())
                .bind("explanation", warning.explanation())
                .bind("status", warning.status().name());
        insert = warning.resultOrder() == null
                ? insert.bindNull("resultOrder", Integer.class)
                : insert.bind("resultOrder", warning.resultOrder());
        return insert.fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.empty()
                        : Mono.error(new IllegalStateException(
                                "No se pudo guardar una advertencia de evaluación")));
    }

    private List<Warning> allWarnings(TransitionRuleEvaluationSnapshot snapshot) {
        List<Warning> warnings = new ArrayList<>(snapshot.warnings());
        snapshot.results().forEach(result -> warnings.addAll(result.warnings()));
        return warnings;
    }

    private record InsertedEvaluation(Long id) {
    }

    private record EvaluationRow(
            long id,
            long transitionVersionId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            long repertoireItemId,
            long separationSettingsRevision,
            BigDecimal globalSeparationThreshold,
            Status status,
            LocalDateTime evaluatedAt) {
    }

    private record ResultRow(
            int order,
            long sceneRuleId,
            long ruleRevisionId,
            String ruleType,
            Status status,
            String code,
            String explanation) {
    }

    private record DisplacementMetricsRow(
            BigDecimal totalDistance,
            BigDecimal maxIndividualDistance,
            int movingSingerCount) {
    }
}
