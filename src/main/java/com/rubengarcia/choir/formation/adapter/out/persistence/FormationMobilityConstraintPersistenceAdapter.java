package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.port.out.FormationMobilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationMobilityConstraintPersistenceAdapter
        implements FormationMobilityConstraintPort {

    private final DatabaseClient databaseClient;

    public FormationMobilityConstraintPersistenceAdapter(
            DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<FormationMobilityConstraint> findForFormation(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return databaseClient.sql("""
                        SELECT
                            c.id,
                            c.formation_id,
                            c.singer_id,
                            c.min_x,
                            c.max_x,
                            c.min_y,
                            c.max_y,
                            c.active,
                            c.created_at
                        FROM formation_mobility_constraints c
                        JOIN formations f
                            ON f.id = c.formation_id
                           AND f.choir_id = c.choir_id
                        WHERE c.formation_id = :formationId
                          AND f.choir_id = :choirId
                          AND f.concert_id = :concertId
                          AND f.repertoire_item_id = :repertoireItemId
                        ORDER BY c.singer_id, c.id
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
    public Mono<FormationMobilityConstraint> create(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerId,
            MobilityRegion region) {

        return requireFormation(
                        choirId, concertId, repertoireItemId, formationId)
                .then(requireSingerInDraft(formationId, singerId))
                .then(insertConstraint(choirId, formationId, singerId, region))
                .flatMap(constraintId -> findById(formationId, constraintId));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationMobilityConstraint> update(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            MobilityRegion region,
            boolean active) {
        return databaseClient.sql("""
                        UPDATE formation_mobility_constraints c
                        SET min_x = :minX,
                            max_x = :maxX,
                            min_y = :minY,
                            max_y = :maxY,
                            active = :active
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
                .bind("minX", region.minX())
                .bind("maxX", region.maxX())
                .bind("minY", region.minY())
                .bind("maxY", region.maxY())
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
                        DELETE FROM formation_mobility_constraints
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

    private Mono<Void> requireSingerInDraft(long formationId, long singerId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM formation_positions p
                        JOIN formation_versions v
                            ON v.id = p.formation_version_id
                        WHERE v.formation_id = :formationId
                          AND v.status = 'DRAFT'
                          AND p.singer_id = :singerId
                        """)
                .bind("formationId", formationId)
                .bind("singerId", singerId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .flatMap(total -> total >= 1
                        ? Mono.empty()
                        : Mono.error(new IllegalArgumentException(
                                "El coreuta debe tener una posición en un "
                                        + "borrador de esta formación")));
    }

    private Mono<Long> insertConstraint(
            long choirId,
            long formationId,
            long singerId,
            MobilityRegion region) {
        return databaseClient.sql("""
                        INSERT INTO formation_mobility_constraints (
                            formation_id,
                            choir_id,
                            singer_id,
                            min_x,
                            max_x,
                            min_y,
                            max_y,
                            active
                        )
                        VALUES (
                            :formationId,
                            :choirId,
                            :singerId,
                            :minX,
                            :maxX,
                            :minY,
                            :maxY,
                            TRUE
                        )
                        """)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .bind("singerId", singerId)
                .bind("minX", region.minX())
                .bind("maxX", region.maxX())
                .bind("minY", region.minY())
                .bind("maxY", region.maxY())
                .filter((statement, executeFunction) ->
                        executeFunction.execute(
                                statement.returnGeneratedValues("id")))
                .map((row, metadata) -> row.get("id", Long.class))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No se pudo crear la restricción de movilidad")));
    }

    private Mono<FormationMobilityConstraint> findById(
            long formationId,
            long constraintId) {
        return databaseClient.sql("""
                        SELECT
                            id,
                            formation_id,
                            singer_id,
                            min_x,
                            max_x,
                            min_y,
                            max_y,
                            active,
                            created_at
                        FROM formation_mobility_constraints
                        WHERE id = :constraintId
                          AND formation_id = :formationId
                        """)
                .bind("constraintId", constraintId)
                .bind("formationId", formationId)
                .map((row, metadata) -> mapConstraint(row))
                .one();
    }

    private FormationMobilityConstraint mapConstraint(Row row) {
        return new FormationMobilityConstraint(
                row.get("id", Long.class),
                row.get("formation_id", Long.class),
                row.get("singer_id", Long.class),
                new MobilityRegion(
                        row.get("min_x", BigDecimal.class),
                        row.get("max_x", BigDecimal.class),
                        row.get("min_y", BigDecimal.class),
                        row.get("max_y", BigDecimal.class)),
                Boolean.TRUE.equals(row.get("active", Boolean.class)),
                row.get("created_at", LocalDateTime.class));
    }
}
