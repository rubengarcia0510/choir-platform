package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.port.out.FormationProximityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationProximityConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationProximityConstraintPersistenceAdapter
        implements FormationProximityConstraintPort {

    private final DatabaseClient databaseClient;

    public FormationProximityConstraintPersistenceAdapter(
            DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<FormationProximityConstraint> findForFormation(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return databaseClient.sql("""
                        SELECT
                            c.id,
                            c.formation_id,
                            c.singer_one_id,
                            c.singer_two_id,
                            c.max_distance,
                            c.active,
                            c.created_at
                        FROM formation_proximity_constraints c
                        JOIN formations f ON f.id = c.formation_id
                        WHERE f.id = :formationId
                          AND f.choir_id = :choirId
                          AND f.concert_id = :concertId
                          AND f.repertoire_item_id = :repertoireItemId
                        ORDER BY c.singer_one_id, c.singer_two_id
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
    public Mono<FormationProximityConstraint> create(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerOneId,
            long singerTwoId,
            BigDecimal maxDistance) {

        return requireFormation(choirId, concertId, repertoireItemId, formationId)
                .then(requireSingersInDraft(
                        formationId, singerOneId, singerTwoId))
                .then(databaseClient.sql("""
                                INSERT INTO formation_proximity_constraints (
                                    formation_id,
                                    choir_id,
                                    singer_one_id,
                                    singer_two_id,
                                    max_distance,
                                    active
                                )
                                VALUES (
                                    :formationId,
                                    :choirId,
                                    :singerOneId,
                                    :singerTwoId,
                                    :maxDistance,
                                    TRUE
                                )
                                """)
                        .bind("formationId", formationId)
                        .bind("choirId", choirId)
                        .bind("singerOneId", singerOneId)
                        .bind("singerTwoId", singerTwoId)
                        .bind("maxDistance", maxDistance)
                        .fetch()
                        .rowsUpdated())
                .then(findByPair(
                        formationId, singerOneId, singerTwoId));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationProximityConstraint> update(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            BigDecimal maxDistance,
            boolean active) {
        return databaseClient.sql("""
                        UPDATE formation_proximity_constraints c
                        SET max_distance = :maxDistance,
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
                .bind("maxDistance", maxDistance)
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
                        DELETE FROM formation_proximity_constraints
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

    private Mono<Void> requireSingersInDraft(
            long formationId,
            long singerOneId,
            long singerTwoId) {
        return databaseClient.sql("""
                        SELECT COUNT(DISTINCT p.singer_id) AS total
                        FROM formation_versions v
                        JOIN formation_positions p
                            ON p.formation_version_id = v.id
                        WHERE v.formation_id = :formationId
                          AND v.status = 'DRAFT'
                          AND p.singer_id IN (:singerOneId, :singerTwoId)
                        """)
                .bind("formationId", formationId)
                .bind("singerOneId", singerOneId)
                .bind("singerTwoId", singerTwoId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .flatMap(total -> total == 2
                        ? Mono.empty()
                        : Mono.error(new IllegalArgumentException(
                                "Los dos coreutas deben tener una posición "
                                        + "en el borrador de esta formación")));
    }

    private Mono<FormationProximityConstraint> findByPair(
            long formationId,
            long singerOneId,
            long singerTwoId) {
        return databaseClient.sql("""
                        SELECT
                            id,
                            formation_id,
                            singer_one_id,
                            singer_two_id,
                            max_distance,
                            active,
                            created_at
                        FROM formation_proximity_constraints
                        WHERE formation_id = :formationId
                          AND singer_one_id = :singerOneId
                          AND singer_two_id = :singerTwoId
                        """)
                .bind("formationId", formationId)
                .bind("singerOneId", singerOneId)
                .bind("singerTwoId", singerTwoId)
                .map((row, metadata) -> mapConstraint(row))
                .one();
    }

    private Mono<FormationProximityConstraint> findById(
            long formationId,
            long constraintId) {
        return databaseClient.sql("""
                        SELECT
                            id,
                            formation_id,
                            singer_one_id,
                            singer_two_id,
                            max_distance,
                            active,
                            created_at
                        FROM formation_proximity_constraints
                        WHERE id = :constraintId
                          AND formation_id = :formationId
                        """)
                .bind("constraintId", constraintId)
                .bind("formationId", formationId)
                .map((row, metadata) -> mapConstraint(row))
                .one();
    }

    private FormationProximityConstraint mapConstraint(
            io.r2dbc.spi.Row row) {
        return new FormationProximityConstraint(
                row.get("id", Long.class),
                row.get("formation_id", Long.class),
                row.get("singer_one_id", Long.class),
                row.get("singer_two_id", Long.class),
                row.get("max_distance", BigDecimal.class),
                Boolean.TRUE.equals(row.get("active", Boolean.class)),
                row.get("created_at", LocalDateTime.class));
    }
}
