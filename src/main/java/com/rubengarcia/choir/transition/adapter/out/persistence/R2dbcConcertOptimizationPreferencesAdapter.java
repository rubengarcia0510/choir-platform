package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.transition.application.port.out.ConcertOptimizationPreferencesPort;
import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcConcertOptimizationPreferencesAdapter
        implements ConcertOptimizationPreferencesPort {

    private final DatabaseClient database;

    public R2dbcConcertOptimizationPreferencesAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Mono<ConcertOptimizationPreferences> findByConcertId(long concertId) {
        return database.sql("""
                SELECT distance, crossings, compactness, section_continuity
                FROM concert_optimization_preferences
                WHERE concert_id = :concertId
                """)
                .bind("concertId", concertId)
                .map((row, metadata) -> new ConcertOptimizationPreferences(
                        row.get("distance", Integer.class),
                        row.get("crossings", Integer.class),
                        row.get("compactness", Integer.class),
                        row.get("section_continuity", Integer.class)))
                .one();
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<ConcertOptimizationPreferences> save(
            long concertId,
            ConcertOptimizationPreferences preferences) {
        return lockConcert(concertId)
                .then(update(concertId, preferences))
                .flatMap(rowsUpdated -> rowsUpdated == 0
                        ? insert(concertId, preferences)
                        : Mono.empty())
                .thenReturn(preferences);
    }

    private Mono<Void> lockConcert(long concertId) {
        return database.sql("""
                SELECT id
                FROM concerts
                WHERE id = :concertId
                FOR UPDATE
                """)
                .bind("concertId", concertId)
                .fetch()
                .first()
                .then();
    }

    private Mono<Long> update(
            long concertId,
            ConcertOptimizationPreferences preferences) {
        return database.sql("""
                UPDATE concert_optimization_preferences
                SET distance = :distance,
                    crossings = :crossings,
                    compactness = :compactness,
                    section_continuity = :sectionContinuity
                WHERE concert_id = :concertId
                """)
                .bind("concertId", concertId)
                .bind("distance", preferences.distance())
                .bind("crossings", preferences.crossings())
                .bind("compactness", preferences.compactness())
                .bind("sectionContinuity", preferences.sectionContinuity())
                .fetch()
                .rowsUpdated();
    }

    private Mono<Void> insert(
            long concertId,
            ConcertOptimizationPreferences preferences) {
        return database.sql("""
                INSERT INTO concert_optimization_preferences (
                    concert_id,
                    distance,
                    crossings,
                    compactness,
                    section_continuity
                ) VALUES (
                    :concertId,
                    :distance,
                    :crossings,
                    :compactness,
                    :sectionContinuity
                )
                """)
                .bind("concertId", concertId)
                .bind("distance", preferences.distance())
                .bind("crossings", preferences.crossings())
                .bind("compactness", preferences.compactness())
                .bind("sectionContinuity", preferences.sectionContinuity())
                .fetch()
                .rowsUpdated()
                .then();
    }
}
