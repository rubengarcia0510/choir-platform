package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.transition.application.port.out.TransitionMovementStepRepository;
import com.rubengarcia.choir.transition.domain.TransitionMovementStep;
import com.rubengarcia.choir.transition.domain.TransitionPlanningContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcTransitionMovementStepRepository
        implements TransitionMovementStepRepository {

    private final DatabaseClient database;

    public R2dbcTransitionMovementStepRepository(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Flux<TransitionMovementStep> findByTransitionVersionId(
            long transitionVersionId) {
        return database.sql("""
                SELECT transition_id, transition_version_id,
                       from_formation_version_id, to_formation_version_id,
                       singer_id, sequence_number, temporal_reference, action
                FROM transition_movement_steps
                WHERE transition_version_id = :transitionVersionId
                ORDER BY sequence_number, singer_id
                """)
                .bind("transitionVersionId", transitionVersionId)
                .map((row, metadata) -> new TransitionMovementStep(
                        row.get("transition_id", Long.class),
                        row.get("transition_version_id", Long.class),
                        row.get("from_formation_version_id", Long.class),
                        row.get("to_formation_version_id", Long.class),
                        row.get("singer_id", Long.class),
                        row.get("sequence_number", Integer.class),
                        row.get("temporal_reference", OffsetDateTime.class),
                        row.get("action", String.class)))
                .all();
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<List<TransitionMovementStep>> replaceForDraftVersion(
            TransitionPlanningContext context,
            List<TransitionMovementStep> steps) {
        if (steps.stream().anyMatch(step -> step == null
                || step.transitionId() != context.transitionId()
                || step.transitionVersionId() != context.transitionVersionId()
                || step.fromFormationVersionId() != context.fromFormationVersionId()
                || step.toFormationVersionId() != context.toFormationVersionId())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los pasos no corresponden a los snapshots de la transición"));
        }

        return lockVersion(context)
                .flatMap(version -> {
                    if (!"DRAFT".equals(version.status())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden registrar movimientos en versiones DRAFT"));
                    }
                    return validateSingers(version.choirId(), steps)
                            .then(deleteSteps(context.transitionVersionId()))
                            .thenMany(Flux.fromIterable(steps)
                                    .concatMap(step -> insertStep(version.choirId(), step)))
                            .thenMany(findByTransitionVersionId(
                                    context.transitionVersionId()))
                            .collectList();
                });
    }

    private Mono<LockedVersion> lockVersion(TransitionPlanningContext context) {
        return database.sql("""
                SELECT tv.status, t.choir_id
                FROM transition_versions tv
                JOIN transitions t ON t.id = tv.transition_id
                WHERE tv.id = :transitionVersionId
                  AND tv.transition_id = :transitionId
                  AND tv.from_formation_version_id = :fromVersionId
                  AND tv.to_formation_version_id = :toVersionId
                FOR UPDATE
                """)
                .bind("transitionVersionId", context.transitionVersionId())
                .bind("transitionId", context.transitionId())
                .bind("fromVersionId", context.fromFormationVersionId())
                .bind("toVersionId", context.toFormationVersionId())
                .map((row, metadata) -> new LockedVersion(
                        row.get("status", String.class),
                        row.get("choir_id", Long.class)))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La versión no corresponde a los snapshots de la transición")));
    }

    private Mono<Void> validateSingers(
            long choirId, List<TransitionMovementStep> steps) {
        return Flux.fromIterable(steps)
                .map(TransitionMovementStep::singerId)
                .distinct()
                .concatMap(singerId -> database.sql("""
                        SELECT 1
                        FROM singers
                        WHERE id = :singerId AND choir_id = :choirId
                        """)
                        .bind("singerId", singerId)
                        .bind("choirId", choirId)
                        .fetch()
                        .first()
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Un participante no pertenece al coro de la transición"))))
                .then();
    }

    private Mono<Void> deleteSteps(long transitionVersionId) {
        return database.sql("""
                DELETE FROM transition_movement_steps
                WHERE transition_version_id = :transitionVersionId
                """)
                .bind("transitionVersionId", transitionVersionId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> insertStep(
            long choirId, TransitionMovementStep step) {
        return database.sql("""
                INSERT INTO transition_movement_steps (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    choir_id, singer_id, sequence_number,
                    temporal_reference, action
                ) VALUES (
                    :transitionVersionId, :transitionId,
                    :fromVersionId, :toVersionId, :choirId,
                    :singerId, :sequenceNumber, :temporalReference, :action
                )
                """)
                .bind("transitionVersionId", step.transitionVersionId())
                .bind("transitionId", step.transitionId())
                .bind("fromVersionId", step.fromFormationVersionId())
                .bind("toVersionId", step.toFormationVersionId())
                .bind("choirId", choirId)
                .bind("singerId", step.singerId())
                .bind("sequenceNumber", step.sequenceNumber())
                .bind("temporalReference", step.temporalReference())
                .bind("action", step.action())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.empty()
                        : Mono.error(new IllegalStateException(
                                "No se pudo persistir un paso de movimiento")));
    }

    private record LockedVersion(String status, long choirId) {
    }
}
