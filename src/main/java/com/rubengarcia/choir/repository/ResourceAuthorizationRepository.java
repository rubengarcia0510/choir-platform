package com.rubengarcia.choir.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class ResourceAuthorizationRepository {

    private final DatabaseClient database;

    public ResourceAuthorizationRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Boolean> directorOwnsChoir(String email, long choirId) {
        return exists("""
                SELECT 1 AS authorized
                FROM choirs ch
                JOIN directors d ON d.id = ch.director_id
                WHERE ch.id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("choirId", choirId)
                .bind("email", email)
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> directorOwnsConcert(
            String email, long choirId, long concertId) {
        return exists("""
                SELECT 1 AS authorized
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
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> directorOwnsWork(
            String email, long choirId, long workId) {
        return exists("""
                SELECT 1 AS authorized
                FROM repertoire_items r
                JOIN choirs ch ON ch.id = r.choir_id
                JOIN directors d ON d.id = ch.director_id
                WHERE r.id = :workId
                  AND r.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("workId", workId)
                .bind("choirId", choirId)
                .bind("email", email)
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> directorOwnsConcertWork(
            String email, long choirId, long concertId, long workId) {
        return exists("""
                SELECT 1 AS authorized
                FROM concerts c
                JOIN choirs ch ON ch.id = c.choir_id
                JOIN directors d ON d.id = ch.director_id
                JOIN repertoire_items r
                  ON r.id = :workId
                 AND r.choir_id = c.choir_id
                JOIN concert_repertoire cr
                  ON cr.concert_id = c.id
                 AND cr.repertoire_item_id = r.id
                WHERE c.id = :concertId
                  AND c.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("concertId", concertId)
                .bind("workId", workId)
                .bind("choirId", choirId)
                .bind("email", email)
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> directorOwnsFormationVersion(
            String email, long choirId, long formationVersionId) {
        return exists("""
                SELECT 1 AS authorized
                FROM formation_versions fv
                JOIN formations f ON f.id = fv.formation_id
                JOIN choirs ch ON ch.id = f.choir_id
                JOIN directors d ON d.id = ch.director_id
                WHERE fv.id = :versionId
                  AND f.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("versionId", formationVersionId)
                .bind("choirId", choirId)
                .bind("email", email)
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> directorOwnsTransitionVersion(
            String email, long choirId, long transitionVersionId) {
        return exists("""
                SELECT 1 AS authorized
                FROM transition_versions tv
                JOIN transitions t ON t.id = tv.transition_id
                JOIN choirs ch ON ch.id = t.choir_id
                JOIN directors d ON d.id = ch.director_id
                JOIN formation_versions from_version
                  ON from_version.id = tv.from_formation_version_id
                JOIN formations from_formation
                  ON from_formation.id = from_version.formation_id
                 AND from_formation.choir_id = ch.id
                JOIN formation_versions to_version
                  ON to_version.id = tv.to_formation_version_id
                JOIN formations to_formation
                  ON to_formation.id = to_version.formation_id
                 AND to_formation.choir_id = ch.id
                WHERE tv.id = :transitionVersionId
                  AND t.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("transitionVersionId", transitionVersionId)
                .bind("choirId", choirId)
                .bind("email", email)
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> directorOwnsPosition(
            String email, long choirId, long positionId) {
        return exists("""
                SELECT 1 AS authorized
                FROM formation_positions fp
                JOIN formation_versions fv
                  ON fv.id = fp.formation_version_id
                JOIN formations f ON f.id = fv.formation_id
                JOIN choirs ch ON ch.id = f.choir_id
                JOIN directors d ON d.id = ch.director_id
                WHERE fp.id = :positionId
                  AND f.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("positionId", positionId)
                .bind("choirId", choirId)
                .bind("email", email)
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(false);
    }

    private DatabaseClient.GenericExecuteSpec exists(String sql) {
        return database.sql(sql);
    }
}
