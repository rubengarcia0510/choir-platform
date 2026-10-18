package com.rubengarcia.choir.service;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class ConcertPublicationService {

    private final DatabaseClient database;

    public ConcertPublicationService(DatabaseClient database) {
        this.database = database;
    }

    public record Publication(
            long concertId,
            String status,
            LocalDateTime publishedAt,
            long castSnapshots,
            long vocalAssignmentSnapshots) {}

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Publication> publish(String email, long choirId, long concertId) {
        if (email == null || email.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return database.sql("""
                SELECT c.id, c.status
                FROM concerts c
                JOIN choirs ch ON ch.id = c.choir_id
                JOIN directors d ON d.id = ch.director_id
                WHERE c.id = :concertId
                  AND c.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .bind("email", email)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Concierto inexistente o fuera del coro del director")))
                .flatMap(status -> {
                    if (!"PLANNED".equals(status)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden publicar conciertos en estado PLANNED"));
                    }
                    return capture(concertId, choirId);
                });
    }

    private Mono<Publication> capture(long concertId, long choirId) {
        return database.sql("""
                UPDATE concerts
                SET status = 'PUBLISHED',
                    published_at = CURRENT_TIMESTAMP
                WHERE id = :concertId
                  AND choir_id = :choirId
                  AND status = 'PLANNED'
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> {
                    if (updated == 0) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "El concierto ya fue publicado o cambió de estado"));
                    }
                    return captureCast(concertId)
                            .then(captureVocalAssignments(concertId, choirId))
                            .then(readPublication(concertId));
                });
    }

    private Mono<Long> captureCast(long concertId) {
        return database.sql("""
                INSERT INTO concert_cast_snapshots (
                    concert_id,
                    source_singer_id,
                    singer_name_snapshot,
                    voice_part_snapshot,
                    voice_section_id_snapshot,
                    voice_section_name_snapshot
                )
                SELECT
                    :concertId,
                    s.id,
                    s.display_name,
                    s.voice_part,
                    s.voice_section_id,
                    vs.name
                FROM concerts c
                JOIN singers s
                  ON s.choir_id = c.choir_id
                 AND s.active = TRUE
                LEFT JOIN vocal_sections vs
                  ON vs.id = s.voice_section_id
                 AND vs.choir_id = c.choir_id
                WHERE c.id = :concertId
                """)
                .bind("concertId", concertId)
                .fetch()
                .rowsUpdated()
                .map(Long::valueOf);
    }

    private Mono<Long> captureVocalAssignments(long concertId, long choirId) {
        return database.sql("""
                INSERT INTO concert_vocal_assignment_snapshots (
                    concert_id,
                    repertoire_item_id,
                    source_singer_id,
                    singer_name_snapshot,
                    voice_part_snapshot,
                    voice_section_id_snapshot,
                    voice_section_name_snapshot,
                    division_number_snapshot
                )
                SELECT
                    a.concert_id,
                    a.repertoire_item_id,
                    s.id,
                    s.display_name,
                    s.voice_part,
                    vs.id,
                    vs.name,
                    d.division_number
                FROM concert_vocal_assignments a
                JOIN singers s
                  ON s.id = a.singer_id
                 AND s.choir_id = a.choir_id
                JOIN vocal_plan_divisions d
                  ON d.id = a.vocal_plan_division_id
                 AND d.choir_id = a.choir_id
                JOIN vocal_sections vs
                  ON vs.id = d.vocal_section_id
                 AND vs.choir_id = a.choir_id
                WHERE a.concert_id = :concertId
                  AND a.choir_id = :choirId
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .fetch()
                .rowsUpdated()
                .map(Long::valueOf);
    }

    private Mono<Publication> readPublication(long concertId) {
        return Mono.zip(
                database.sql("""
                        SELECT status, published_at
                        FROM concerts
                        WHERE id = :concertId
                        """)
                        .bind("concertId", concertId)
                        .map((row, metadata) -> new Object[] {
                                row.get("status", String.class),
                                row.get("published_at", LocalDateTime.class)
                        })
                        .one(),
                database.sql("""
                        SELECT COUNT(*) AS total
                        FROM concert_cast_snapshots
                        WHERE concert_id = :concertId
                        """)
                        .bind("concertId", concertId)
                        .map((row, metadata) -> row.get("total", Long.class))
                        .one(),
                database.sql("""
                        SELECT COUNT(*) AS total
                        FROM concert_vocal_assignment_snapshots
                        WHERE concert_id = :concertId
                        """)
                        .bind("concertId", concertId)
                        .map((row, metadata) -> row.get("total", Long.class))
                        .one()
        ).map(result -> {
            Object[] concert = result.getT1();
            return new Publication(
                    concertId,
                    (String) concert[0],
                    (LocalDateTime) concert[1],
                    result.getT2(),
                    result.getT3());
        });
    }
}
