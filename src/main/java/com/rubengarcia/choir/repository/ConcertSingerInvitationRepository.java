package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.service.ConcertSingerInvitationService.CastMember;
import com.rubengarcia.choir.service.ConcertSingerInvitationService.RepertoireEntry;
import com.rubengarcia.choir.service.ConcertSingerInvitationService.VocalAssignment;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ConcertSingerInvitationRepository {

    private final DatabaseClient database;

    public ConcertSingerInvitationRepository(DatabaseClient database) {
        this.database = database;
    }

    public record InvitationMeta(long invitationId, long concertId,
                                 long singerId, long choirId) {}

    public record ConcertDetails(String title, LocalDate date, String venue) {}

    public Mono<String> findConcertStatus(long choirId, long concertId) {
        return database.sql("""
                SELECT status FROM concerts
                WHERE id = :concertId AND choir_id = :choirId
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .map((row, meta) -> row.get("status", String.class))
                .one();
    }

    public Mono<Long> findActiveSinger(long choirId, long singerId) {
        return database.sql("""
                SELECT id FROM singers
                WHERE id = :singerId
                  AND choir_id = :choirId
                  AND active = TRUE
                """)
                .bind("singerId", singerId)
                .bind("choirId", choirId)
                .map((row, meta) -> row.get("id", Long.class))
                .one();
    }

    public Mono<Long> insertInvitation(
            long choirId, long concertId, long singerId,
            String tokenHash, LocalDateTime expiresAt) {

        DatabaseClient.GenericExecuteSpec query;

        if (expiresAt == null) {
            query = database.sql("""
                    INSERT INTO concert_singer_invitations
                        (choir_id, concert_id, singer_id, token_hash)
                    VALUES (:choirId, :concertId, :singerId, :hash)
                    """)
                    .bind("choirId", choirId)
                    .bind("concertId", concertId)
                    .bind("singerId", singerId)
                    .bind("hash", tokenHash);
        } else {
            query = database.sql("""
                    INSERT INTO concert_singer_invitations
                        (choir_id, concert_id, singer_id, token_hash, expires_at)
                    VALUES (:choirId, :concertId, :singerId, :hash, :expiresAt)
                    """)
                    .bind("choirId", choirId)
                    .bind("concertId", concertId)
                    .bind("singerId", singerId)
                    .bind("hash", tokenHash)
                    .bind("expiresAt", expiresAt);
        }

        return query.fetch().rowsUpdated()
                .flatMap(count -> count == 0
                        ? Mono.empty()
                        : database.sql("""
                                SELECT id FROM concert_singer_invitations
                                WHERE token_hash = :hash
                                """)
                                .bind("hash", tokenHash)
                                .map((row, meta) -> row.get("id", Long.class))
                                .one());
    }

    public Mono<Integer> revoke(
            long choirId, long concertId, long invitationId) {
        return database.sql("""
                UPDATE concert_singer_invitations
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND choir_id = :choirId
                  AND concert_id = :concertId
                  AND revoked_at IS NULL
                """)
                .bind("id", invitationId)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .fetch()
                .rowsUpdated()
                .map(Long::intValue);
    }

    public Mono<InvitationMeta> findUsableInvitation(String tokenHash) {
        return database.sql("""
                SELECT i.id AS invitation_id, i.concert_id, i.singer_id, i.choir_id
                FROM concert_singer_invitations i
                JOIN concerts c
                  ON c.id = i.concert_id AND c.choir_id = i.choir_id
                WHERE i.token_hash = :hash
                  AND i.revoked_at IS NULL
                  AND (i.expires_at IS NULL OR i.expires_at > CURRENT_TIMESTAMP)
                  AND c.status = 'PUBLISHED'
                """)
                .bind("hash", tokenHash)
                .map((row, meta) -> new InvitationMeta(
                        row.get("invitation_id", Long.class),
                        row.get("concert_id", Long.class),
                        row.get("singer_id", Long.class),
                        row.get("choir_id", Long.class)))
                .one();
    }

    public Mono<ConcertDetails> findConcertDetails(long choirId, long concertId) {
        return database.sql("""
                SELECT title, concert_date, venue
                FROM concerts
                WHERE id = :concertId AND choir_id = :choirId
                  AND status = 'PUBLISHED'
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .map((row, meta) -> new ConcertDetails(
                        row.get("title", String.class),
                        row.get("concert_date", LocalDate.class),
                        row.get("venue", String.class)))
                .one();
    }

    public Mono<String> findInvitedSingerSnapshotName(
            long concertId, long singerId) {
        return database.sql("""
                SELECT singer_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = :concertId
                  AND source_singer_id = :singerId
                """)
                .bind("concertId", concertId)
                .bind("singerId", singerId)
                .map((row, meta) -> row.get("singer_name_snapshot", String.class))
                .one()
                .switchIfEmpty(database.sql("""
                        SELECT singer_name_snapshot
                        FROM concert_vocal_assignment_snapshots
                        WHERE concert_id = :concertId
                          AND source_singer_id = :singerId
                        ORDER BY repertoire_item_id
                        LIMIT 1
                        """)
                        .bind("concertId", concertId)
                        .bind("singerId", singerId)
                        .map((row, meta) -> row.get("singer_name_snapshot", String.class))
                        .one())
                .defaultIfEmpty("");
    }

    public Mono<List<RepertoireEntry>> findRepertoire(long choirId, long concertId) {
        return database.sql("""
                SELECT ri.id AS item_id, ri.title, ri.composer, ri.arranger,
                       cr.order_index
                FROM concert_repertoire cr
                JOIN concerts c ON c.id = cr.concert_id
                JOIN repertoire_items ri
                  ON ri.id = cr.repertoire_item_id AND ri.choir_id = c.choir_id
                WHERE cr.concert_id = :concertId AND c.choir_id = :choirId
                ORDER BY cr.order_index, ri.id
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .map((row, meta) -> new RepertoireEntry(
                        row.get("item_id", Long.class),
                        row.get("title", String.class),
                        row.get("composer", String.class),
                        row.get("arranger", String.class),
                        row.get("order_index", Integer.class)))
                .all()
                .collectList();
    }

    public Mono<List<CastMember>> findCast(long concertId, long invitedSingerId) {
        return database.sql("""
                SELECT source_singer_id, singer_name_snapshot,
                       voice_part_snapshot, voice_section_name_snapshot,
                       division_number_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = :concertId
                ORDER BY singer_name_snapshot
                """)
                .bind("concertId", concertId)
                .map((row, meta) -> {
                    Long singerId = row.get("source_singer_id", Long.class);
                    return new CastMember(
                            singerId,
                            row.get("singer_name_snapshot", String.class),
                            row.get("voice_part_snapshot", String.class),
                            row.get("voice_section_name_snapshot", String.class),
                            row.get("division_number_snapshot", Integer.class),
                            singerId != null && singerId.longValue() == invitedSingerId);
                })
                .all()
                .collectList();
    }

    public Mono<List<VocalAssignment>> findVocalAssignments(
            long concertId, long invitedSingerId) {
        return database.sql("""
                SELECT a.repertoire_item_id, ri.title AS work_title,
                       a.source_singer_id, a.singer_name_snapshot,
                       a.voice_part_snapshot, a.voice_section_name_snapshot,
                       a.division_number_snapshot
                FROM concert_vocal_assignment_snapshots a
                JOIN concerts c ON c.id = a.concert_id
                JOIN concert_repertoire cr
                  ON cr.concert_id = a.concert_id
                 AND cr.repertoire_item_id = a.repertoire_item_id
                JOIN repertoire_items ri
                  ON ri.id = a.repertoire_item_id AND ri.choir_id = c.choir_id
                WHERE a.concert_id = :concertId
                ORDER BY cr.order_index, a.voice_section_name_snapshot,
                         a.division_number_snapshot, a.singer_name_snapshot
                """)
                .bind("concertId", concertId)
                .map((row, meta) -> {
                    Long singerId = row.get("source_singer_id", Long.class);
                    return new VocalAssignment(
                            row.get("repertoire_item_id", Long.class),
                            row.get("work_title", String.class),
                            row.get("singer_name_snapshot", String.class),
                            row.get("voice_part_snapshot", String.class),
                            row.get("voice_section_name_snapshot", String.class),
                            row.get("division_number_snapshot", Integer.class),
                            singerId != null && singerId.longValue() == invitedSingerId);
                })
                .all()
                .collectList();
    }
}
