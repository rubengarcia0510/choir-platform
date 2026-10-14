package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.ConcertRepertoire;
import com.rubengarcia.choir.domain.ConcertRepertoireId;
import org.springframework.r2dbc.core.DatabaseClient;
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
                SELECT concert_id, repertoire_item_id, order_index, choir_id
                FROM concert_repertoire
                WHERE concert_id = :concertId
                ORDER BY order_index, repertoire_item_id
                """)
                .bind("concertId", concertId)
                .map((row, metadata) -> {
                    ConcertRepertoire item = new ConcertRepertoire(
                            row.get("concert_id", Long.class),
                            row.get("repertoire_item_id", Long.class),
                            row.get("order_index", Integer.class));
                    item.setChoirId(row.get("choir_id", Long.class));
                    return item;
                })
                .all();
    }

    public Mono<ConcertRepertoire> findById(ConcertRepertoireId id) {
        return databaseClient.sql("""
                SELECT concert_id, repertoire_item_id, order_index, choir_id
                FROM concert_repertoire
                WHERE concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                """)
                .bind("concertId", id.getConcertId())
                .bind("repertoireItemId", id.getRepertoireItemId())
                .map((row, metadata) -> {
                    ConcertRepertoire item = new ConcertRepertoire(
                            row.get("concert_id", Long.class),
                            row.get("repertoire_item_id", Long.class),
                            row.get("order_index", Integer.class));
                    item.setChoirId(row.get("choir_id", Long.class));
                    return item;
                })
                .one();
    }

    public Mono<ConcertRepertoire> insert(ConcertRepertoire item) {
        return databaseClient.sql("""
                INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index, choir_id)
                SELECT :concertId, :repertoireItemId, :orderIndex, c.choir_id
                FROM concerts c
                WHERE c.id = :concertId
                """)
                .bind("concertId", item.getConcertId())
                .bind("repertoireItemId", item.getRepertoireItemId())
                .bind("orderIndex", item.getOrderIndex())
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows == 0) {
                        return Mono.error(new IllegalArgumentException(
                                "Concert does not exist: " + item.getConcertId()));
                    }

                    return findById(new ConcertRepertoireId(
                            item.getConcertId(), item.getRepertoireItemId()));
                });
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
