package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityRosterPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class FormationVisibilityRosterPersistenceAdapter
        implements FormationVisibilityRosterPort {

    private final DatabaseClient databaseClient;

    public FormationVisibilityRosterPersistenceAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<Long> findActiveAssignedSingerIds(
            long choirId,
            long concertId,
            long repertoireItemId) {
        return databaseClient.sql("""
                        SELECT DISTINCT a.singer_id
                        FROM concert_vocal_assignments a
                        JOIN singers s
                            ON s.id = a.singer_id
                           AND s.choir_id = a.choir_id
                        WHERE a.choir_id = :choirId
                          AND a.concert_id = :concertId
                          AND a.repertoire_item_id = :repertoireItemId
                          AND s.active = TRUE
                        ORDER BY a.singer_id
                        """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> row.get("singer_id", Long.class))
                .all();
    }
}
