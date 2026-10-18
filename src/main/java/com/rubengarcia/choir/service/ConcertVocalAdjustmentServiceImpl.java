package com.rubengarcia.choir.service;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConcertVocalAdjustmentServiceImpl implements ConcertVocalAdjustmentService {

    private final DatabaseClient database;
    private final ResourceAuthorizationService authorizationService;

    public ConcertVocalAdjustmentServiceImpl(
            DatabaseClient database,
            ResourceAuthorizationService authorizationService) {
        this.database = database;
        this.authorizationService = authorizationService;
    }







    public Mono<Adjustment> getAdjustment(
            String email, long choirId, long concertId, long workId) {
        return ownedOccurrence(email, choirId, concertId, workId)
                .then(database.sql("""
                        SELECT id, concert_id, repertoire_item_id, choir_id,
                               notes, created_at
                        FROM concert_vocal_adjustments
                        WHERE concert_id = :concertId
                          AND repertoire_item_id = :workId
                          AND choir_id = :choirId
                        """)
                        .bind("concertId", concertId)
                        .bind("workId", workId)
                        .bind("choirId", choirId)
                        .map((row, meta) -> new Adjustment(
                                row.get("id", Long.class),
                                row.get("concert_id", Long.class),
                                row.get("repertoire_item_id", Long.class),
                                row.get("choir_id", Long.class),
                                row.get("notes", String.class),
                                row.get("created_at", LocalDateTime.class)))
                        .one()
                        .switchIfEmpty(Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        "No hay ajustes para esta obra en el concierto"))));
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Adjustment> saveAdjustment(
            String email, long choirId, long concertId,
            long workId, String notes) {
        return ownedOccurrence(email, choirId, concertId, workId)
                .then(database.sql("""
                        UPDATE concert_vocal_adjustments
                        SET notes = :notes
                        WHERE concert_id = :concertId
                          AND repertoire_item_id = :workId
                          AND choir_id = :choirId
                        """)
                        .bind("concertId", concertId)
                        .bind("workId", workId)
                        .bind("choirId", choirId)
                        .bind("notes", notes == null ? "" : notes)
                        .fetch()
                        .rowsUpdated())
                .flatMap(updated -> updated > 0
                        ? Mono.just(updated)
                        : database.sql("""
                                INSERT INTO concert_vocal_adjustments
                                    (concert_id, repertoire_item_id, choir_id, notes)
                                VALUES (:concertId, :workId, :choirId, :notes)
                                """)
                                .bind("concertId", concertId)
                                .bind("workId", workId)
                                .bind("choirId", choirId)
                                .bind("notes", notes == null ? "" : notes)
                                .fetch()
                                .rowsUpdated())
                .then(database.sql("""
                        SELECT id, concert_id, repertoire_item_id, choir_id,
                               notes, created_at
                        FROM concert_vocal_adjustments
                        WHERE concert_id = :concertId
                          AND repertoire_item_id = :workId
                          AND choir_id = :choirId
                        """)
                        .bind("concertId", concertId)
                        .bind("workId", workId)
                        .bind("choirId", choirId)
                        .map((row, meta) -> new Adjustment(
                                row.get("id", Long.class),
                                row.get("concert_id", Long.class),
                                row.get("repertoire_item_id", Long.class),
                                row.get("choir_id", Long.class),
                                row.get("notes", String.class),
                                row.get("created_at", LocalDateTime.class)))
                        .one());
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Assignment> assignSinger(
            String email, long choirId, long concertId, long workId,
            long singerId, long divisionId) {
        return ownedOccurrence(email, choirId, concertId, workId)
                .then(requireExists("""
                        SELECT COUNT(*) AS n
                        FROM singers s
                        WHERE s.id = :firstKey
                          AND s.choir_id = :choirId
                          AND s.active = TRUE
                        """, singerId, choirId, null)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Cantante inexistente o inactivo en este coro"))))
                .then(requireExists("""
                        SELECT COUNT(*) AS n
                        FROM vocal_plan_divisions d
                        JOIN vocal_plans p ON p.id = d.vocal_plan_id
                                            AND p.choir_id = d.choir_id
                        WHERE d.id = :firstKey
                          AND d.choir_id = :choirId
                          AND p.repertoire_item_id = :workId
                        """, divisionId, choirId, workId)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "La división no pertenece al plan vocal de esta obra"))))
                .then(database.sql("""
                        UPDATE concert_vocal_assignments
                        SET vocal_plan_division_id = :divisionId
                        WHERE concert_id = :concertId
                          AND repertoire_item_id = :workId
                          AND singer_id = :singerId
                          AND choir_id = :choirId
                        """)
                        .bind("concertId", concertId)
                        .bind("workId", workId)
                        .bind("singerId", singerId)
                        .bind("divisionId", divisionId)
                        .bind("choirId", choirId)
                        .fetch()
                        .rowsUpdated())
                .flatMap(updated -> updated > 0
                        ? Mono.just(updated)
                        : database.sql("""
                                INSERT INTO concert_vocal_assignments
                                    (concert_id, repertoire_item_id, singer_id,
                                     vocal_plan_division_id, choir_id)
                                VALUES (:concertId, :workId, :singerId, :divisionId, :choirId)
                                """)
                                .bind("concertId", concertId)
                                .bind("workId", workId)
                                .bind("singerId", singerId)
                                .bind("divisionId", divisionId)
                                .bind("choirId", choirId)
                                .fetch()
                                .rowsUpdated())
                .then(database.sql("""
                        SELECT id, concert_id, repertoire_item_id, singer_id,
                               vocal_plan_division_id, choir_id, created_at
                        FROM concert_vocal_assignments
                        WHERE concert_id = :concertId
                          AND repertoire_item_id = :workId
                          AND singer_id = :singerId
                          AND choir_id = :choirId
                        """)
                        .bind("concertId", concertId)
                        .bind("workId", workId)
                        .bind("singerId", singerId)
                        .bind("choirId", choirId)
                        .map((row, meta) -> new Assignment(
                                row.get("id", Long.class),
                                row.get("concert_id", Long.class),
                                row.get("repertoire_item_id", Long.class),
                                row.get("singer_id", Long.class),
                                row.get("vocal_plan_division_id", Long.class),
                                row.get("choir_id", Long.class),
                                row.get("created_at", LocalDateTime.class)))
                        .one());
    }

    public Mono<List<Warning>> warnings(
            String email, long choirId, long concertId) {
        return ownedConcert(email, choirId, concertId)
                .thenMany(Flux.concat(
                        missingPlanWarnings(choirId, concertId),
                        absenceWarnings(choirId, concertId),
                        emptyDivisionWarnings(choirId, concertId)))
                .collectList();
    }

    private Flux<Warning> missingPlanWarnings(long choirId, long concertId) {
        return database.sql("""
                SELECT cr.repertoire_item_id AS work_id
                FROM concert_repertoire cr
                LEFT JOIN vocal_plans p
                    ON p.repertoire_item_id = cr.repertoire_item_id
                   AND p.choir_id = :choirId
                WHERE cr.concert_id = :concertId
                  AND p.id IS NULL
                ORDER BY cr.order_index
                """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .map((row, meta) -> new Warning(
                        "MISSING_VOCAL_PLAN",
                        row.get("work_id", Long.class),
                        null, null,
                        "La obra no tiene un plan vocal configurado para este coro."))
                .all();
    }

    private Flux<Warning> absenceWarnings(long choirId, long concertId) {
        return database.sql("""
                SELECT DISTINCT cr.repertoire_item_id AS work_id,
                       s.id AS singer_id, s.display_name AS singer_name
                FROM concert_repertoire cr
                JOIN concerts c ON c.id = cr.concert_id
                JOIN concert_vocal_assignments a
                  ON a.concert_id = cr.concert_id
                 AND a.repertoire_item_id = cr.repertoire_item_id
                JOIN singers s ON s.id = a.singer_id
                              AND s.choir_id = a.choir_id
                JOIN absences ab ON ab.singer_id = s.id
                WHERE c.id = :concertId
                  AND c.choir_id = :choirId
                  AND ab.status IN ('REPORTED', 'CONFIRMED')
                  AND (ab.concert_id = c.id OR ab.concert_id IS NULL)
                  AND (c.concert_date IS NULL OR
                       c.concert_date BETWEEN ab.start_date AND ab.end_date)
                ORDER BY cr.repertoire_item_id, s.id
                """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .map((row, meta) -> new Warning(
                        "ASSIGNED_SINGER_ABSENT",
                        row.get("work_id", Long.class),
                        row.get("singer_id", Long.class),
                        null,
                        "El cantante asignado " + row.get("singer_name", String.class)
                                + " tiene una ausencia registrada para el concierto."))
                .all();
    }

    private Flux<Warning> emptyDivisionWarnings(long choirId, long concertId) {
        return database.sql("""
                SELECT cr.repertoire_item_id AS work_id,
                       d.id AS division_id
                FROM concert_repertoire cr
                JOIN vocal_plans p
                  ON p.repertoire_item_id = cr.repertoire_item_id
                 AND p.choir_id = :choirId
                JOIN vocal_plan_divisions d
                  ON d.vocal_plan_id = p.id
                 AND d.choir_id = p.choir_id
                LEFT JOIN concert_vocal_assignments a
                  ON a.concert_id = cr.concert_id
                 AND a.repertoire_item_id = cr.repertoire_item_id
                 AND a.vocal_plan_division_id = d.id
                 AND a.choir_id = :choirId
                WHERE cr.concert_id = :concertId
                GROUP BY cr.repertoire_item_id, d.id
                HAVING COUNT(a.id) = 0
                ORDER BY cr.repertoire_item_id, d.id
                """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .map((row, meta) -> new Warning(
                        "EMPTY_DIVISION",
                        row.get("work_id", Long.class),
                        null,
                        row.get("division_id", Long.class),
                        "La división no tiene asignaciones explícitas para esta aparición. "
                                + "Revisá la cobertura antes del concierto."))
                .all();
    }

    private Mono<Void> ownedOccurrence(
            String email, long choirId, long concertId, long workId) {
        return authorizationService.requireOwnedConcertWork(
                email, choirId, concertId, workId);
    }

    private Mono<Void> ownedConcert(
            String email, long choirId, long concertId) {
        return authorizationService.requireOwnedConcert(email, choirId, concertId);
    }

    private Mono<Boolean> requireExists(
            String sql, long firstKey, long choirId, Long workId) {
        DatabaseClient.GenericExecuteSpec spec = database.sql(sql)
                .bind("firstKey", firstKey)
                .bind("choirId", choirId);

        if (sql.contains(":workId")) {
            spec = spec.bind("workId", workId);
        }

        return spec.map((row, meta) -> row.get("n", Long.class) != null
                        && row.get("n", Long.class) > 0)
                .one()
                .defaultIfEmpty(false);
    }
}
