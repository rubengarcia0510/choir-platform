package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.ConcertCastMember;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public class ConcertCastSelectionRepository {

    private final DatabaseClient database;

    public ConcertCastSelectionRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Void> replaceSelection(
            long choirId,
            long concertId,
            List<Long> singerIds) {

        return database.sql("""
                UPDATE concerts
                SET cast_configured = TRUE
                WHERE id = :concertId
                  AND choir_id = :choirId
                  AND status IN ('PLANNED', 'REHEARSAL')
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> {
                    if (updated == 0) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se puede cambiar el elenco de un concierto en configuración o ensayo"));
                    }
                    return replaceSelectionRows(choirId, concertId, singerIds);
                });
    }

    private Mono<Void> replaceSelectionRows(
            long choirId, long concertId, List<Long> singerIds) {
        return database.sql("""
                DELETE FROM concert_cast_selections
                WHERE concert_id = :concertId
                  AND choir_id = :choirId
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .fetch()
                .rowsUpdated()
                .thenMany(Flux.fromIterable(singerIds)
                        .concatMap(singerId ->
                                database.sql("""
                                        INSERT INTO concert_cast_selections
                                            (concert_id, choir_id, singer_id)
                                        VALUES
                                            (:concertId, :choirId, :singerId)
                                        """)
                                        .bind("concertId", concertId)
                                        .bind("choirId", choirId)
                                        .bind("singerId", singerId)
                                        .fetch()
                                        .rowsUpdated()))
                .then(database.sql("""
                        DELETE FROM concert_cast_snapshots
                        WHERE concert_id = :concertId
                        """)
                        .bind("concertId", concertId)
                        .fetch()
                        .rowsUpdated())
                .then(captureSelectedCast(
                        choirId, concertId, singerIds.size()))
                .then();
    }

    private Mono<Void> captureSelectedCast(
            long choirId, long concertId, int expectedCount) {
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
                    selected.concert_id,
                    s.id,
                    s.display_name,
                    s.voice_part,
                    s.voice_section_id,
                    vs.name
                FROM concert_cast_selections selected
                JOIN singers s
                  ON s.id = selected.singer_id
                 AND s.choir_id = selected.choir_id
                 AND s.active = TRUE
                LEFT JOIN vocal_sections vs
                  ON vs.id = s.voice_section_id
                 AND vs.choir_id = s.choir_id
                WHERE selected.concert_id = :concertId
                  AND selected.choir_id = :choirId
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .fetch()
                .rowsUpdated()
                .flatMap(captured -> captured == expectedCount
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "El elenco cambió durante la configuración; volvé a intentarlo")));
    }

    public Mono<Boolean> isConfigured(long concertId) {
        return database.sql("""
                SELECT cast_configured
                FROM concerts
                WHERE id = :concertId
                """)
                .bind("concertId", concertId)
                .map((row, metadata) ->
                        Boolean.TRUE.equals(row.get("cast_configured", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<Long> captureCast(long concertId) {
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
                    c.id,
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
                  AND NOT EXISTS (
                      SELECT 1
                      FROM concert_cast_snapshots existing
                      WHERE existing.concert_id = c.id
                  )
                  AND (
                      c.cast_configured = FALSE
                      OR EXISTS (
                          SELECT 1
                          FROM concert_cast_selections selected
                          WHERE selected.concert_id = c.id
                            AND selected.choir_id = c.choir_id
                            AND selected.singer_id = s.id
                      )
                  )
                """)
                .bind("concertId", concertId)
                .fetch()
                .rowsUpdated()
                .map(Long::valueOf);
    }

    public Flux<ConcertCastMember> findCast(long concertId) {
        return database.sql("""
                SELECT source_singer_id AS singer_id,
                       singer_name_snapshot AS singer_name,
                       voice_part_snapshot AS voice_part,
                       voice_section_id_snapshot AS voice_section_id,
                       voice_section_name_snapshot AS voice_section_name,
                       division_number_snapshot AS division_number
                FROM concert_cast_snapshots
                WHERE concert_id = :concertId
                UNION ALL
                SELECT s.id AS singer_id,
                       s.display_name AS singer_name,
                       s.voice_part AS voice_part,
                       s.voice_section_id AS voice_section_id,
                       vs.name AS voice_section_name,
                       CAST(NULL AS INTEGER) AS division_number
                FROM concerts c
                JOIN singers s
                  ON s.choir_id = c.choir_id
                 AND s.active = TRUE
                LEFT JOIN vocal_sections vs
                  ON vs.id = s.voice_section_id
                 AND vs.choir_id = c.choir_id
                WHERE c.id = :concertId
                  AND c.status <> 'PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM concert_cast_snapshots existing
                      WHERE existing.concert_id = c.id
                  )
                  AND (
                      c.cast_configured = FALSE
                      OR EXISTS (
                          SELECT 1
                          FROM concert_cast_selections selected
                          WHERE selected.concert_id = c.id
                            AND selected.choir_id = c.choir_id
                            AND selected.singer_id = s.id
                      )
                  )
                ORDER BY singer_name
                """)
                .bind("concertId", concertId)
                .map((row, metadata) -> new ConcertCastMember(
                        row.get("singer_id", Long.class),
                        row.get("singer_name", String.class),
                        row.get("voice_part", String.class),
                        row.get("voice_section_id", Long.class),
                        row.get("voice_section_name", String.class),
                        row.get("division_number", Integer.class)))
                .all();
    }
}
