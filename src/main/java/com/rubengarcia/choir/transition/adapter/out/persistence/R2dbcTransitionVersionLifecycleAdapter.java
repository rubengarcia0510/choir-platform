package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionLifecyclePort;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class R2dbcTransitionVersionLifecycleAdapter
        implements TransitionVersionLifecyclePort {

    private final DatabaseClient database;

    public R2dbcTransitionVersionLifecycleAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Flux<TransitionVersion> list(long choirId, long transitionId) {
        return requireTransition(choirId, transitionId)
                .thenMany(database.sql("""
                        SELECT id, transition_id, version_number,
                               from_formation_version_id,
                               to_formation_version_id, status, instructions,
                               created_at, published_at
                        FROM transition_versions
                        WHERE transition_id = :transitionId
                        ORDER BY version_number DESC
                        """)
                        .bind("transitionId", transitionId)
                        .map((row, metadata) -> mapVersion(row))
                        .all());
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TransitionVersion> createDraft(
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            String instructions) {
        return lockTransition(choirId, transitionId)
                .then(requireFormationVersions(
                        choirId,
                        transitionId,
                        fromFormationVersionId,
                        toFormationVersionId))
                .then(insertDraft(
                        transitionId,
                        fromFormationVersionId,
                        toFormationVersionId,
                        instructions))
                .then(latestVersion(transitionId));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TransitionVersion> publishAfterRuleEvaluation(
            long choirId, long transitionId, long versionId) {
        return lockTransition(choirId, transitionId)
                .then(findVersion(transitionId, versionId))
                .flatMap(version -> {
                    if (!"DRAFT".equals(version.getStatus())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden publicar versiones en estado DRAFT"));
                    }

                    return database.sql("""
                            UPDATE transition_versions
                            SET status = 'PUBLISHED',
                                published_at = CURRENT_TIMESTAMP
                            WHERE id = :versionId
                              AND transition_id = :transitionId
                              AND status = 'DRAFT'
                            """)
                            .bind("versionId", versionId)
                            .bind("transitionId", transitionId)
                            .fetch()
                            .rowsUpdated()
                            .flatMap(updated -> updated == 1
                                    ? findVersion(transitionId, versionId)
                                    : Mono.error(new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "La versión cambió de estado y no pudo publicarse")));
                });
    }

    private Mono<Void> requireTransition(long choirId, long transitionId) {
        return transitionQuery(choirId, transitionId, false)
                .then();
    }

    private Mono<Void> lockTransition(long choirId, long transitionId) {
        return transitionQuery(choirId, transitionId, true)
                .then();
    }

    private Mono<Map<String, Object>> transitionQuery(
            long choirId, long transitionId, boolean lock) {
        String sql = """
                SELECT id
                FROM transitions
                WHERE id = :transitionId
                  AND choir_id = :choirId
                """ + (lock ? " FOR UPDATE" : "");

        return database.sql(sql)
                .bind("transitionId", transitionId)
                .bind("choirId", choirId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transición inexistente o fuera del coro")));
    }

    private Mono<Void> requireFormationVersions(
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId) {
        return database.sql("""
                SELECT 1
                FROM transitions t
                JOIN formation_versions from_version
                  ON from_version.id = :fromVersionId
                JOIN formations from_formation
                  ON from_formation.id = from_version.formation_id
                 AND from_formation.choir_id = t.choir_id
                JOIN formation_versions to_version
                  ON to_version.id = :toVersionId
                JOIN formations to_formation
                  ON to_formation.id = to_version.formation_id
                 AND to_formation.choir_id = t.choir_id
                WHERE t.id = :transitionId
                  AND t.choir_id = :choirId
                  AND from_version.id <> to_version.id
                """)
                .bind("fromVersionId", fromFormationVersionId)
                .bind("toVersionId", toFormationVersionId)
                .bind("transitionId", transitionId)
                .bind("choirId", choirId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Las versiones de formación no pertenecen al coro o no son válidas")))
                .then();
    }

    private Mono<Void> insertDraft(
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            String instructions) {
        DatabaseClient.GenericExecuteSpec insert = database.sql("""
                INSERT INTO transition_versions
                    (transition_id, version_number,
                     from_formation_version_id, to_formation_version_id,
                     status, instructions)
                SELECT :transitionId,
                       COALESCE(MAX(version_number), 0) + 1,
                       :fromVersionId,
                       :toVersionId,
                       'DRAFT',
                       :instructions
                FROM transition_versions
                WHERE transition_id = :transitionId
                """)
                .bind("transitionId", transitionId)
                .bind("fromVersionId", fromFormationVersionId)
                .bind("toVersionId", toFormationVersionId);
        insert = instructions == null
                ? insert.bindNull("instructions", String.class)
                : insert.bind("instructions", instructions);

        return insert.fetch().rowsUpdated().then();
    }

    private Mono<TransitionVersion> latestVersion(long transitionId) {
        return database.sql("""
                SELECT id, transition_id, version_number,
                       from_formation_version_id,
                       to_formation_version_id, status, instructions,
                       created_at, published_at
                FROM transition_versions
                WHERE transition_id = :transitionId
                ORDER BY version_number DESC
                LIMIT 1
                """)
                .bind("transitionId", transitionId)
                .map((row, metadata) -> mapVersion(row))
                .one();
    }

    private Mono<TransitionVersion> findVersion(
            long transitionId, long versionId) {
        return database.sql("""
                SELECT id, transition_id, version_number,
                       from_formation_version_id,
                       to_formation_version_id, status, instructions,
                       created_at, published_at
                FROM transition_versions
                WHERE transition_id = :transitionId
                  AND id = :versionId
                """)
                .bind("transitionId", transitionId)
                .bind("versionId", versionId)
                .map((row, metadata) -> mapVersion(row))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Versión inexistente o fuera de la transición")));
    }

    private TransitionVersion mapVersion(io.r2dbc.spi.Row row) {
        TransitionVersion version = new TransitionVersion();
        version.setId(row.get("id", Long.class));
        version.setTransitionId(row.get("transition_id", Long.class));
        version.setVersionNumber(row.get("version_number", Integer.class));
        version.setFromFormationVersionId(
                row.get("from_formation_version_id", Long.class));
        version.setToFormationVersionId(
                row.get("to_formation_version_id", Long.class));
        version.setStatus(row.get("status", String.class));
        version.setInstructions(row.get("instructions", String.class));
        version.setCreatedAt(row.get("created_at", LocalDateTime.class));
        version.setPublishedAt(row.get("published_at", LocalDateTime.class));
        return version;
    }
}
