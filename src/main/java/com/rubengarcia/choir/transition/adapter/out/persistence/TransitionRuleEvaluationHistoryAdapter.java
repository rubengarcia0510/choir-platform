package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.RuleResult;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                                snapshot.warnings())));
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
                        loadWarnings(evaluation.id()))
                .map(data -> assembleEvaluation(evaluation, data.getT1(), data.getT2()));
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
            List<Warning> warnings) {
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
                globalWarnings);
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
}
