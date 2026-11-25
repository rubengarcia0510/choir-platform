package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationVisibilityConstraintPersistenceAdapter
        implements FormationVisibilityConstraintPort {

    private final DatabaseClient databaseClient;

    public FormationVisibilityConstraintPersistenceAdapter(
            DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<FormationVisibilityConstraint> findForFormation(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return databaseClient.sql("""
                        SELECT
                            c.id,
                            c.formation_id,
                            c.protected_singer_id,
                            c.active,
                            c.created_at
                        FROM formation_visibility_constraints c
                        JOIN formations f
                            ON f.id = c.formation_id
                           AND f.choir_id = c.choir_id
                        WHERE c.formation_id = :formationId
                          AND f.choir_id = :choirId
                          AND f.concert_id = :concertId
                          AND f.repertoire_item_id = :repertoireItemId
                        ORDER BY c.protected_singer_id, c.id
                        """)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> mapConstraint(row))
                .all();
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationVisibilityConstraint> create(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long protectedSingerId) {

        return requireFormation(
                        choirId, concertId, repertoireItemId, formationId)
                .then(requireSingerAssigned(
                        choirId, concertId, repertoireItemId, protectedSingerId))
                .then(insertConstraint(choirId, formationId, protectedSingerId))
                .flatMap(constraintId -> findById(formationId, constraintId));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationVisibilityConstraint> update(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            boolean active) {
        return databaseClient.sql("""
                        UPDATE formation_visibility_constraints c
                        SET active = :active
                        WHERE c.id = :constraintId
                          AND c.formation_id = :formationId
                          AND EXISTS (
                              SELECT 1
                              FROM formations f
                              WHERE f.id = c.formation_id
                                AND f.choir_id = :choirId
                                AND f.concert_id = :concertId
                                AND f.repertoire_item_id = :repertoireItemId
                          )
                        """)
                .bind("active", active)
                .bind("constraintId", constraintId)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 1
                        ? findById(formationId, constraintId)
                        : Mono.empty());
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Boolean> delete(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId) {
        return databaseClient.sql("""
                        DELETE FROM formation_visibility_constraints
                        WHERE id = :constraintId
                          AND formation_id = :formationId
                          AND EXISTS (
                              SELECT 1
                              FROM formations f
                              WHERE f.id = formation_id
                                AND f.choir_id = :choirId
                                AND f.concert_id = :concertId
                                AND f.repertoire_item_id = :repertoireItemId
                          )
                        """)
                .bind("constraintId", constraintId)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated == 1);
    }

    private Mono<Void> requireFormation(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM formations
                        WHERE id = :formationId
                          AND choir_id = :choirId
                          AND concert_id = :concertId
                          AND repertoire_item_id = :repertoireItemId
                        """)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .flatMap(total -> total == 1
                        ? Mono.empty()
                        : Mono.error(new FormationNotFoundException()));
    }

    private Mono<Void> requireSingerAssigned(
            long choirId,
            long concertId,
            long repertoireItemId,
            long protectedSingerId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM concert_vocal_assignments a
                        JOIN singers s
                            ON s.id = a.singer_id
                           AND s.choir_id = a.choir_id
                        WHERE a.choir_id = :choirId
                          AND a.concert_id = :concertId
                          AND a.repertoire_item_id = :repertoireItemId
                          AND a.singer_id = :singerId
                          AND s.active = TRUE
                        """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .bind("singerId", protectedSingerId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .flatMap(total -> total >= 1
                        ? Mono.empty()
                        : Mono.error(new IllegalArgumentException(
                                "El coreuta protegido debe estar activo "
                                        + "y asignado a la obra del concierto")));
    }

    private Mono<Long> insertConstraint(
            long choirId,
            long formationId,
            long protectedSingerId) {
        return databaseClient.sql("""
                        INSERT INTO formation_visibility_constraints (
                            formation_id,
                            choir_id,
                            protected_singer_id,
                            active
                        )
                        VALUES (
                            :formationId,
                            :choirId,
                            :protectedSingerId,
                            TRUE
                        )
                        """)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .bind("protectedSingerId", protectedSingerId)
                .filter((statement, executeFunction) ->
                        executeFunction.execute(
                                statement.returnGeneratedValues("id")))
                .map((row, metadata) -> row.get("id", Long.class))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No se pudo crear la restricción de visibilidad")));
    }

    private Mono<FormationVisibilityConstraint> findById(
            long formationId,
            long constraintId) {
        return databaseClient.sql("""
                        SELECT
                            id,
                            formation_id,
                            protected_singer_id,
                            active,
                            created_at
                        FROM formation_visibility_constraints
                        WHERE id = :constraintId
                          AND formation_id = :formationId
                        """)
                .bind("constraintId", constraintId)
                .bind("formationId", formationId)
                .map((row, metadata) -> mapConstraint(row))
                .one();
    }

    private FormationVisibilityConstraint mapConstraint(Row row) {
        return new FormationVisibilityConstraint(
                row.get("id", Long.class),
                row.get("formation_id", Long.class),
                row.get("protected_singer_id", Long.class),
                Boolean.TRUE.equals(row.get("active", Boolean.class)),
                row.get("created_at", LocalDateTime.class));
    }
}
