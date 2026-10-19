package com.rubengarcia.choir.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public class ConcertPublicationRepository {

    private final DatabaseClient database;

    public ConcertPublicationRepository(DatabaseClient database) {
        this.database = database;
    }

    public record PublicationData(
            String status,
            LocalDateTime publishedAt,
            long castSnapshots,
            long vocalAssignmentSnapshots) {
    }

    public Mono<String> findStatus(long choirId, long concertId) {
        return database.sql("""
                SELECT status
                FROM concerts
                WHERE id = :concertId
                  AND choir_id = :choirId
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .map((row, metadata) -> row.get("status", String.class))
                .one();
    }

    public Mono<Boolean> markPublished(long choirId, long concertId) {
        return database.sql("""
                UPDATE concerts
                SET status = 'PUBLISHED',
                    published_at = CURRENT_TIMESTAMP
                WHERE id = :concertId
                  AND choir_id = :choirId
                  AND status IN ('PLANNED', 'REHEARSAL')
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Mono<Long> captureVocalAssignments(long choirId, long concertId) {
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

    public Mono<PublicationData> findPublication(long choirId, long concertId) {
        return database.sql("""
                SELECT
                    c.status,
                    c.published_at,
                    (SELECT COUNT(*)
                     FROM concert_cast_snapshots cs
                     WHERE cs.concert_id = c.id) AS cast_snapshots,
                    (SELECT COUNT(*)
                     FROM concert_vocal_assignment_snapshots vas
                     WHERE vas.concert_id = c.id) AS vocal_assignment_snapshots
                FROM concerts c
                WHERE c.id = :concertId
                  AND c.choir_id = :choirId
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .map((row, metadata) -> new PublicationData(
                        row.get("status", String.class),
                        row.get("published_at", LocalDateTime.class),
                        row.get("cast_snapshots", Long.class),
                        row.get("vocal_assignment_snapshots", Long.class)))
                .one();
    }
}
