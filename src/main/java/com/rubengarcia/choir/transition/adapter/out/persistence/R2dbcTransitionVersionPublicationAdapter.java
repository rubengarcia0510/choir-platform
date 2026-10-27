package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionPublicationPort;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionPublicationPort.PublicationCommand;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionPublicationPort.WarningAcceptance;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.RuleResult;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class R2dbcTransitionVersionPublicationAdapter
        implements TransitionVersionPublicationPort {

    private final DatabaseClient database;
    private final TransitionRuleEvaluationHistoryPort evaluationHistory;

    public R2dbcTransitionVersionPublicationAdapter(
            DatabaseClient database,
            TransitionRuleEvaluationHistoryPort evaluationHistory) {
        this.database = database;
        this.evaluationHistory = evaluationHistory;
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TransitionVersion> publish(PublicationCommand command) {
        return lockVersion(command)
                .flatMap(version -> requireDraft(version)
                        .then(evaluationHistory.findById(command.evaluationId())
                                .switchIfEmpty(Mono.error(notFound(
                                        "No existe la evaluación indicada")))
                                .flatMap(evaluation ->
                                        validateAndPublish(command, version, evaluation))));
    }

    private Mono<LockedVersion> lockVersion(PublicationCommand command) {
        return database.sql("""
                SELECT tv.id, tv.transition_id, tv.version_number,
                       tv.from_formation_version_id,
                       tv.to_formation_version_id, tv.status,
                       tv.instructions, tv.created_at, tv.published_at
                FROM transition_versions tv
                WHERE tv.id = :versionId
                  AND tv.transition_id = :transitionId
                  AND EXISTS (
                      SELECT 1
                      FROM transitions t
                      WHERE t.id = tv.transition_id
                        AND t.choir_id = :choirId
                  )
                FOR UPDATE
                """)
                .bind("versionId", command.transitionVersionId())
                .bind("transitionId", command.transitionId())
                .bind("choirId", command.choirId())
                .map((row, metadata) -> new LockedVersion(
                        row.get("id", Long.class),
                        row.get("transition_id", Long.class),
                        row.get("version_number", Integer.class),
                        row.get("from_formation_version_id", Long.class),
                        row.get("to_formation_version_id", Long.class),
                        row.get("status", String.class),
                        row.get("instructions", String.class),
                        row.get("created_at", LocalDateTime.class),
                        row.get("published_at", LocalDateTime.class)))
                .one()
                .switchIfEmpty(Mono.error(notFound(
                        "La versión no existe o no pertenece a la transición del coro")));
    }

    private Mono<Void> requireDraft(LockedVersion version) {
        if (!"DRAFT".equals(version.status())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden publicar versiones en estado DRAFT"));
        }
        return Mono.empty();
    }

    private Mono<TransitionVersion> validateAndPublish(
            PublicationCommand command,
            LockedVersion version,
            TransitionRuleEvaluationSnapshot evaluation) {
        if (evaluation.id() == null
                || evaluation.transitionVersionId() != version.id()
                || evaluation.transitionId() != version.transitionId()
                || evaluation.fromFormationVersionId()
                        != version.fromFormationVersionId()
                || evaluation.toFormationVersionId()
                        != version.toFormationVersionId()) {
            return Mono.error(notFound(
                    "La evaluación no corresponde a esta versión de transición"));
        }

        if (evaluation.status() == Status.NOT_EVALUABLE) {
            return Mono.error(conflict(
                    "No se puede publicar una evaluación no evaluable"));
        }

        List<Warning> warnings = collectWarnings(evaluation);

        if (evaluation.status() == Status.VIOLATED
                && warnings.stream().noneMatch(warning ->
                        warning.status() == Status.VIOLATED)) {
            return Mono.error(conflict(
                    "Una evaluación VIOLATED requiere advertencias VIOLATED aceptables"));
        }

        if (warnings.stream().anyMatch(warning ->
                warning.status() == Status.NOT_EVALUABLE)) {
            return Mono.error(conflict(
                    "No se puede publicar mientras existan advertencias no evaluables"));
        }

        if (evaluation.results().stream().anyMatch(result ->
                result.status() == Status.NOT_EVALUABLE)) {
            return Mono.error(conflict(
                    "No se puede publicar mientras existan reglas no evaluables"));
        }

        return validateAcceptances(command.acceptedWarnings(), warnings,
                        evaluation.results())
                .flatMap(accepted -> {
                    LocalDateTime publishedAt = LocalDateTime.now();
                    return saveDecisions(
                                    command, evaluation, accepted, publishedAt)
                            .then(saveEvidence(command, evaluation, publishedAt))
                            .then(markPublished(version.id(), publishedAt))
                            .thenReturn(toEntity(version, publishedAt));
                });
    }

    private List<Warning> collectWarnings(
            TransitionRuleEvaluationSnapshot evaluation) {
        List<Warning> warnings = new ArrayList<>(evaluation.warnings());
        evaluation.results().forEach(result ->
                warnings.addAll(result.warnings()));
        return List.copyOf(warnings);
    }

    private Mono<List<WarningAcceptance>> validateAcceptances(
            List<WarningAcceptance> requested,
            List<Warning> warnings,
            List<RuleResult> results) {
        List<WarningAcceptance> acceptances =
                requested == null ? List.of() : List.copyOf(requested);
        Set<String> acceptedKeys = new HashSet<>();

        for (WarningAcceptance acceptance : acceptances) {
            if (acceptance == null
                    || acceptance.reason() == null
                    || acceptance.reason().isBlank()
                    || acceptance.reason().length() > 1000
                    || acceptance.resultOrder() < -1
                    || acceptance.warningOrder() < 0) {
                return Mono.error(badRequest(
                        "Cada aceptación requiere una referencia válida y un motivo"));
            }

            String key = key(acceptance.resultOrder(), acceptance.warningOrder());
            if (!acceptedKeys.add(key)) {
                return Mono.error(badRequest(
                        "No se puede aceptar dos veces la misma advertencia"));
            }

            Warning matched = findWarning(
                    warnings, acceptance.resultOrder(), acceptance.warningOrder());

            if (matched == null || matched.status() != Status.VIOLATED) {
                return Mono.error(badRequest(
                        "La aceptación debe referenciar una advertencia VIOLATED existente"));
            }
        }

        for (Warning warning : warnings) {
            if (warning.status() == Status.VIOLATED) {
                int resultOrder = warning.resultOrder() == null
                        ? -1 : warning.resultOrder();
                if (!acceptedKeys.contains(key(resultOrder, warning.warningOrder()))) {
                    return Mono.error(conflict(
                            "Todas las advertencias VIOLATED deben aceptarse explícitamente"));
                }
            }
        }

        for (RuleResult result : results) {
            if (result.status() == Status.VIOLATED
                    && result.warnings().stream().noneMatch(
                            warning -> warning.status() == Status.VIOLATED)) {
                return Mono.error(conflict(
                        "Una regla VIOLATED no puede publicarse sin una advertencia aceptable"));
            }
        }

        return Mono.just(acceptances);
    }

    private Warning findWarning(
            List<Warning> warnings, int resultOrder, int warningOrder) {
        return warnings.stream()
                .filter(warning -> (warning.resultOrder() == null
                        ? -1 : warning.resultOrder()) == resultOrder)
                .filter(warning -> warning.warningOrder() == warningOrder)
                .findFirst()
                .orElse(null);
    }

    private String key(int resultOrder, int warningOrder) {
        return resultOrder + ":" + warningOrder;
    }

    private Mono<Void> saveDecisions(
            PublicationCommand command,
            TransitionRuleEvaluationSnapshot evaluation,
            List<WarningAcceptance> acceptances,
            LocalDateTime publishedAt) {
        return Flux.fromIterable(acceptances)
                .concatMap(acceptance -> {
                    Warning warning = findWarning(
                            collectWarnings(evaluation),
                            acceptance.resultOrder(),
                            acceptance.warningOrder());

                    return database.sql("""
                            INSERT INTO transition_publication_decisions (
                                evaluation_id, transition_version_id,
                                result_order, warning_order, warning_code,
                                warning_explanation, decision, director_email,
                                reason, decided_at
                            ) VALUES (
                                :evaluationId, :versionId, :resultOrder,
                                :warningOrder, :warningCode,
                                :warningExplanation, 'ACCEPTED',
                                :directorEmail, :reason, :decidedAt
                            )
                            """)
                            .bind("evaluationId", command.evaluationId())
                            .bind("versionId", command.transitionVersionId())
                            .bind("resultOrder", acceptance.resultOrder())
                            .bind("warningOrder", acceptance.warningOrder())
                            .bind("warningCode", warning.code())
                            .bind("warningExplanation", warning.explanation())
                            .bind("directorEmail", command.directorEmail())
                            .bind("reason", acceptance.reason().trim())
                            .bind("decidedAt", publishedAt)
                            .fetch()
                            .rowsUpdated()
                            .flatMap(rows -> rows == 1
                                    ? Mono.empty()
                                    : Mono.error(new IllegalStateException(
                                            "No se pudo guardar la decisión de publicación")));
                })
                .then();
    }

    private Mono<Void> saveEvidence(
            PublicationCommand command,
            TransitionRuleEvaluationSnapshot evaluation,
            LocalDateTime publishedAt) {
        return database.sql("""
                INSERT INTO transition_publication_evidence (
                    transition_version_id, evaluation_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    separation_settings_revision, global_separation_threshold,
                    published_by_email, published_at
                ) VALUES (
                    :versionId, :evaluationId, :transitionId,
                    :fromVersionId, :toVersionId, :settingsRevision,
                    :globalThreshold, :directorEmail, :publishedAt
                )
                """)
                .bind("versionId", command.transitionVersionId())
                .bind("evaluationId", command.evaluationId())
                .bind("transitionId", evaluation.transitionId())
                .bind("fromVersionId", evaluation.fromFormationVersionId())
                .bind("toVersionId", evaluation.toFormationVersionId())
                .bind("settingsRevision", evaluation.separationSettingsRevision())
                .bind("globalThreshold", evaluation.globalSeparationThreshold())
                .bind("directorEmail", command.directorEmail())
                .bind("publishedAt", publishedAt)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.empty()
                        : Mono.error(new IllegalStateException(
                                "No se pudo guardar la evidencia de publicación")));
    }

    private Mono<Void> markPublished(long versionId, LocalDateTime publishedAt) {
        return database.sql("""
                UPDATE transition_versions
                SET status = 'PUBLISHED', published_at = :publishedAt
                WHERE id = :versionId AND status = 'DRAFT'
                """)
                .bind("publishedAt", publishedAt)
                .bind("versionId", versionId)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.empty()
                        : Mono.error(conflict(
                                "La versión dejó de estar en estado DRAFT")));
    }

    private TransitionVersion toEntity(
            LockedVersion version, LocalDateTime publishedAt) {
        TransitionVersion entity = new TransitionVersion();
        entity.setId(version.id());
        entity.setTransitionId(version.transitionId());
        entity.setVersionNumber(version.versionNumber());
        entity.setFromFormationVersionId(version.fromFormationVersionId());
        entity.setToFormationVersionId(version.toFormationVersionId());
        entity.setStatus("PUBLISHED");
        entity.setInstructions(version.instructions());
        entity.setCreatedAt(version.createdAt());
        entity.setPublishedAt(publishedAt);
        return entity;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record LockedVersion(
            long id,
            long transitionId,
            int versionNumber,
            long fromFormationVersionId,
            long toFormationVersionId,
            String status,
            String instructions,
            LocalDateTime createdAt,
            LocalDateTime publishedAt) {
    }
}
