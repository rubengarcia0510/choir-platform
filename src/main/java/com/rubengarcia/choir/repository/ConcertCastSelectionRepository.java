package com.rubengarcia.choir.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
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
                        UPDATE concerts
                        SET cast_configured = TRUE
                        WHERE id = :concertId
                          AND choir_id = :choirId
                        """)
                        .bind("concertId", concertId)
                        .bind("choirId", choirId)
                        .fetch()
                        .rowsUpdated())
                .then();
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
}
