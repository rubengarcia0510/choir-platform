package com.rubengarcia.choir.service;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class WorkVocalAssignmentService {

    private final DatabaseClient database;

    public WorkVocalAssignmentService(DatabaseClient database) {
        this.database = database;
    }

    public record Assignment(
            Long id,
            Long choirId,
            Long workId,
            Long singerId,
            Long vocalPlanId,
            Long vocalPlanDivisionId,
            Integer divisionNumber,
            Long vocalSectionId,
            String vocalSectionName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public Flux<Assignment> list(
            String email, long choirId, long workId) {
        return ownedWork(email, choirId, workId)
                .thenMany(database.sql("""
                        SELECT a.id, a.choir_id, a.repertoire_item_id,
                               a.singer_id, a.vocal_plan_id,
                               a.vocal_plan_division_id, d.division_number,
                               d.vocal_section_id, s.name AS vocal_section_name,
                               a.created_at, a.updated_at
                        FROM work_vocal_assignments a
                        JOIN vocal_plan_divisions d
                          ON d.id = a.vocal_plan_division_id
                         AND d.vocal_plan_id = a.vocal_plan_id
                        JOIN vocal_sections s
                          ON s.id = d.vocal_section_id
                         AND s.choir_id = a.choir_id
                        WHERE a.choir_id = :choirId
                          AND a.repertoire_item_id = :workId
                        ORDER BY a.singer_id
                        """)
                        .bind("choirId", choirId)
                        .bind("workId", workId)
                        .map((row, meta) -> mapAssignment(row))
                        .all());
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Assignment> assign(
            String email,
            long choirId,
            long workId,
            long singerId,
            long divisionId) {
        return ownedWork(email, choirId, workId)
                .then(requireExists("""
                        SELECT COUNT(*) AS n
                        FROM singers
                        WHERE id = :entityId
                          AND choir_id = :choirId
                          AND active = TRUE
                        """, singerId, choirId)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(notFound(
                                "Cantante inexistente, inactivo o de otro coro"))))
                .then(database.sql("""
                        SELECT p.id AS plan_id
                        FROM vocal_plans p
                        WHERE p.repertoire_item_id = :workId
                          AND p.choir_id = :choirId
                        """)
                        .bind("workId", workId)
                        .bind("choirId", choirId)
                        .map((row, meta) -> row.get("plan_id", Long.class))
                        .one()
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "La obra todavía no tiene un plan vocal"))))
                .flatMap(planId -> requireDivision(
                        choirId, workId, planId, divisionId)
                        .then(saveAssignment(
                                choirId, workId, singerId, planId, divisionId)))
                .then(findAssignment(choirId, workId, singerId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "No se pudo recuperar la asignación guardada"))));
    }

    private Mono<Void> saveAssignment(
            long choirId,
            long workId,
            long singerId,
            long planId,
            long divisionId) {
        return database.sql("""
                        UPDATE work_vocal_assignments
                        SET vocal_plan_id = :planId,
                            vocal_plan_division_id = :divisionId,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE choir_id = :choirId
                          AND repertoire_item_id = :workId
                          AND singer_id = :singerId
                        """)
                .bind("planId", planId)
                .bind("divisionId", divisionId)
                .bind("choirId", choirId)
                .bind("workId", workId)
                .bind("singerId", singerId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated > 0
                        ? Mono.just(updated)
                        : database.sql("""
                                INSERT INTO work_vocal_assignments
                                    (repertoire_item_id, singer_id, vocal_plan_id,
                                     vocal_plan_division_id, choir_id)
                                VALUES (:workId, :singerId, :planId,
                                        :divisionId, :choirId)
                                """)
                                .bind("workId", workId)
                                .bind("singerId", singerId)
                                .bind("planId", planId)
                                .bind("divisionId", divisionId)
                                .bind("choirId", choirId)
                                .fetch()
                                .rowsUpdated())
                .then();
    }

    private Mono<Void> requireDivision(
            long choirId, long workId, long planId, long divisionId) {
        return requireExists("""
                SELECT COUNT(*) AS n
                FROM vocal_plan_divisions d
                JOIN vocal_plans p
                  ON p.id = d.vocal_plan_id
                 AND p.choir_id = d.choir_id
                WHERE d.id = :entityId
                  AND d.choir_id = :choirId
                  AND d.vocal_plan_id = :planId
                  AND p.repertoire_item_id = :workId
                """, divisionId, choirId, planId, workId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "La división no pertenece al plan vocal de esta obra")))
                .then();
    }

    private Mono<Assignment> findAssignment(
            long choirId, long workId, long singerId) {
        return database.sql("""
                SELECT a.id, a.choir_id, a.repertoire_item_id,
                       a.singer_id, a.vocal_plan_id,
                       a.vocal_plan_division_id, d.division_number,
                       d.vocal_section_id, s.name AS vocal_section_name,
                       a.created_at, a.updated_at
                FROM work_vocal_assignments a
                JOIN vocal_plan_divisions d
                  ON d.id = a.vocal_plan_division_id
                 AND d.vocal_plan_id = a.vocal_plan_id
                JOIN vocal_sections s
                  ON s.id = d.vocal_section_id
                 AND s.choir_id = a.choir_id
                WHERE a.choir_id = :choirId
                  AND a.repertoire_item_id = :workId
                  AND a.singer_id = :singerId
                """)
                .bind("choirId", choirId)
                .bind("workId", workId)
                .bind("singerId", singerId)
                .map((row, meta) -> mapAssignment(row))
                .one();
    }

    private Assignment mapAssignment(
            io.r2dbc.spi.Row row) {
        return new Assignment(
                row.get("id", Long.class),
                row.get("choir_id", Long.class),
                row.get("repertoire_item_id", Long.class),
                row.get("singer_id", Long.class),
                row.get("vocal_plan_id", Long.class),
                row.get("vocal_plan_division_id", Long.class),
                row.get("division_number", Integer.class),
                row.get("vocal_section_id", Long.class),
                row.get("vocal_section_name", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class));
    }

    private Mono<Void> ownedWork(String email, long choirId, long workId) {
        if (email == null || email.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return database.sql("""
                SELECT COUNT(*) AS n
                FROM repertoire_items r
                JOIN choirs c ON c.id = r.choir_id
                JOIN directors d ON d.id = c.director_id
                WHERE r.id = :workId
                  AND r.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("workId", workId)
                .bind("choirId", choirId)
                .bind("email", email)
                .map((row, meta) -> {
                    Long count = row.get("n", Long.class);
                    return count != null && count > 0;
                })
                .one()
                .defaultIfEmpty(false)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(notFound(
                        "Obra inexistente o fuera del coro del director")))
                .then();
    }

    private Mono<Boolean> requireExists(
            String sql, long entityId, long choirId) {
        return requireExists(sql, entityId, choirId, null, null);
    }

    private Mono<Boolean> requireExists(
            String sql, long entityId, long choirId, Long planId, Long workId) {
        return requireExists(sql, entityId, choirId, planId, workId, null);
    }

    private Mono<Boolean> requireExists(
            String sql,
            long entityId,
            long choirId,
            Long planId,
            Long workId,
            String email) {
        DatabaseClient.GenericExecuteSpec spec = database.sql(sql)
                .bind("entityId", entityId)
                .bind("choirId", choirId);

        if (sql.contains(":planId")) {
            spec = spec.bind("planId", planId);
        }
        if (sql.contains(":workId")) {
            spec = spec.bind("workId", workId);
        }
        if (sql.contains(":email")) {
            spec = spec.bind("email", email);
        }

        return spec.map((row, meta) -> {
                    Long count = row.get("n", Long.class);
                    return count != null && count > 0;
                })
                .one()
                .defaultIfEmpty(false);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
