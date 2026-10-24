package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.ConcertRepertoire;
import com.rubengarcia.choir.domain.ConcertRepertoireEntry;
import com.rubengarcia.choir.domain.ConcertRepertoireId;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Explicit SQL access for concert_repertoire. Spring Data Relational does not
 * provide a portable composite @Id mapping for this table's two-column key.
 */
@Repository
public class ConcertRepertoireRepository {
    private final DatabaseClient databaseClient;

    public ConcertRepertoireRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<ConcertRepertoire> findByConcertId(Long concertId) {
        return databaseClient.sql("""
                SELECT concert_id, repertoire_item_id, order_index,
                       duration_minutes, stage_requirements
                FROM concert_repertoire
                WHERE concert_id = :concertId
                ORDER BY order_index, repertoire_item_id
                """)
                .bind("concertId", concertId)
                .map((row, metadata) -> new ConcertRepertoire(
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("order_index", Integer.class),
                        row.get("duration_minutes", Integer.class),
                        row.get("stage_requirements", String.class)))
                .all();
    }

    public Flux<ConcertRepertoireEntry> findConcertEntries(long concertId) {
        return databaseClient.sql("""
                SELECT cr.concert_id, cr.repertoire_item_id, cr.order_index,
                       cr.duration_minutes, cr.stage_requirements,
                       ri.title, ri.composer, ri.arranger
                FROM concert_repertoire cr
                JOIN repertoire_items ri ON ri.id = cr.repertoire_item_id
                WHERE cr.concert_id = :concertId
                ORDER BY cr.order_index, cr.repertoire_item_id
                """)
                .bind("concertId", concertId)
                .map((row, metadata) -> new ConcertRepertoireEntry(
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("order_index", Integer.class),
                        row.get("duration_minutes", Integer.class),
                        row.get("stage_requirements", String.class),
                        row.get("title", String.class),
                        row.get("composer", String.class),
                        row.get("arranger", String.class)))
                .all();
    }

    public Mono<String> findStatusForUpdate(long choirId, long concertId) {
        return databaseClient.sql("""
                SELECT status
                FROM concerts
                WHERE id = :concertId
                  AND choir_id = :choirId
                FOR UPDATE
                """)
                .bind("concertId", concertId)
                .bind("choirId", choirId)
                .map((row, metadata) -> row.get("status", String.class))
                .one();
    }

    public Mono<ConcertRepertoire> findById(ConcertRepertoireId id) {
        return databaseClient.sql("""
                SELECT concert_id, repertoire_item_id, order_index,
                       duration_minutes, stage_requirements
                FROM concert_repertoire
                WHERE concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                """)
                .bind("concertId", id.getConcertId())
                .bind("repertoireItemId", id.getRepertoireItemId())
                .map((row, metadata) -> new ConcertRepertoire(
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("order_index", Integer.class),
                        row.get("duration_minutes", Integer.class),
                        row.get("stage_requirements", String.class)))
                .one();
    }

    public Mono<ConcertRepertoire> insert(ConcertRepertoire item) {
        GenericExecuteSpec statement = databaseClient.sql("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index,
                    duration_minutes, stage_requirements)
                VALUES (
                    :concertId, :repertoireItemId, :orderIndex,
                    :durationMinutes, :stageRequirements)
                """)
                .bind("concertId", item.getConcertId())
                .bind("repertoireItemId", item.getRepertoireItemId())
                .bind("orderIndex", item.getOrderIndex());
        statement = item.getDurationMinutes() == null
                ? statement.bindNull("durationMinutes", Integer.class)
                : statement.bind("durationMinutes", item.getDurationMinutes());
        statement = item.getStageRequirements() == null
                ? statement.bindNull("stageRequirements", String.class)
                : statement.bind("stageRequirements", item.getStageRequirements());
        return statement.fetch()
                .rowsUpdated()
                .thenReturn(item);
    }

    public Mono<Long> deleteByConcertId(long concertId) {
        return databaseClient.sql("""
                DELETE FROM concert_repertoire
                WHERE concert_id = :concertId
                """)
                .bind("concertId", concertId)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> updateOrderIndex(ConcertRepertoireId id, int orderIndex) {
        return databaseClient.sql("""
                UPDATE concert_repertoire
                SET order_index = :orderIndex
                WHERE concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                """)
                .bind("orderIndex", orderIndex)
                .bind("concertId", id.getConcertId())
                .bind("repertoireItemId", id.getRepertoireItemId())
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> deleteById(ConcertRepertoireId id) {
        return databaseClient.sql("""
                DELETE FROM concert_repertoire
                WHERE concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                """)
                .bind("concertId", id.getConcertId())
                .bind("repertoireItemId", id.getRepertoireItemId())
                .fetch()
                .rowsUpdated();
    }
}
