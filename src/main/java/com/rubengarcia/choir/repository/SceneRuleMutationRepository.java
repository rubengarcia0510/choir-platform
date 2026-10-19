package com.rubengarcia.choir.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class SceneRuleMutationRepository {

    private final DatabaseClient database;

    public SceneRuleMutationRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Boolean> lockRepertoireItem(long repertoireItemId) {
        return database.sql("""
                SELECT id
                FROM repertoire_items
                WHERE id = :repertoireItemId
                FOR UPDATE
                """)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> row.get("id", Long.class) != null)
                .one()
                .defaultIfEmpty(false);
    }
}
