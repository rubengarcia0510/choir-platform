package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionSelectionPersistencePort;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import com.rubengarcia.choir.formation.domain.FormationVersionSelectionHistory;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationVersionSelectionPersistenceAdapter
        implements FormationVersionSelectionPersistencePort {

    private final DatabaseClient database;

    public FormationVersionSelectionPersistenceAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Mono<FormationVersionSelection> findSelection(
            long concertId,
            long repertoireItemId) {
        return database.sql("""
                SELECT cr.concert_id,
                       cr.repertoire_item_id,
                       cr.selected_formation_id,
                       cr.selected_formation_version_id,
                       (
                           SELECT MAX(h.selected_at)
                           FROM formation_version_selection_history h
                           WHERE h.concert_id = cr.concert_id
                             AND h.repertoire_item_id = cr.repertoire_item_id
                       ) AS selected_at
                FROM concert_repertoire cr
                WHERE cr.concert_id = :concertId
                  AND cr.repertoire_item_id = :repertoireItemId
                """)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> new FormationVersionSelection(
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("selected_formation_id", Long.class),
                        row.get("selected_formation_version_id", Long.class),
                        row.get("selected_at", LocalDateTime.class)))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La ocurrencia de repertorio no existe")));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationVersionSelection> selectPublishedVersion(
            long concertId,
            long repertoireItemId,
            long formationId,
            long formationVersionId,
            String actorEmail) {

        return lockFormationForOccurrence(
                        concertId, repertoireItemId, formationId)
                .then(lockOccurrence(concertId, repertoireItemId))
                .then(requirePublishedVersion(formationId, formationVersionId))
                .then(findSelection(concertId, repertoireItemId))
                .flatMap(current -> {
                    if (Objects.equals(current.formationId(), formationId)
                            && Objects.equals(
                                    current.formationVersionId(), formationVersionId)) {
                        return Mono.just(current);
                    }

                    return appendHistory(
                                    concertId,
                                    repertoireItemId,
                                    formationId,
                                    formationVersionId,
                                    actorEmail)
                            .then(updateSelection(
                                    concertId,
                                    repertoireItemId,
                                    formationId,
                                    formationVersionId))
                            .then(findSelection(concertId, repertoireItemId));
                });
    }

    @Override
    public Flux<FormationVersionSelectionHistory> findSelectionHistory(
            long concertId,
            long repertoireItemId) {
        return database.sql("""
                SELECT h.id,
                       h.concert_id,
                       h.repertoire_item_id,
                       h.previous_formation_id,
                       h.previous_formation_version_id,
                       h.selected_formation_id,
                       h.selected_formation_version_id,
                       h.actor_email,
                       h.selected_at
                FROM formation_version_selection_history h
                WHERE h.concert_id = :concertId
                  AND h.repertoire_item_id = :repertoireItemId
                ORDER BY h.selected_at ASC, h.id ASC
                """)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> new FormationVersionSelectionHistory(
                        row.get("id", Long.class),
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("previous_formation_id", Long.class),
                        row.get("previous_formation_version_id", Long.class),
                        row.get("selected_formation_id", Long.class),
                        row.get("selected_formation_version_id", Long.class),
                        row.get("actor_email", String.class),
                        row.get("selected_at", LocalDateTime.class)))
                .all();
    }

    private Mono<Void> lockFormationForOccurrence(
            long concertId,
            long repertoireItemId,
            long formationId) {
        return database.sql("""
                SELECT id
                FROM formations
                WHERE id = :formationId
                  AND concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                FOR UPDATE
                """)
                .bind("formationId", formationId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La formación no pertenece a esta ocurrencia de repertorio")))
                .then();
    }

    private Mono<Void> lockOccurrence(
            long concertId,
            long repertoireItemId) {
        return database.sql("""
                SELECT concert_id
                FROM concert_repertoire
                WHERE concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                FOR UPDATE
                """)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La ocurrencia de repertorio no existe")))
                .then();
    }

    private Mono<Void> requirePublishedVersion(
            long formationId,
            long formationVersionId) {
        return database.sql("""
                SELECT status
                FROM formation_versions
                WHERE id = :formationVersionId
                  AND formation_id = :formationId
                """)
                .bind("formationVersionId", formationVersionId)
                .bind("formationId", formationId)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La versión no pertenece a la formación indicada")))
                .flatMap(status -> {
                    if (!"PUBLISHED".equals(status)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden seleccionar versiones publicadas"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> appendHistory(
            long concertId,
            long repertoireItemId,
            long formationId,
            long formationVersionId,
            String actorEmail) {
        return database.sql("""
                INSERT INTO formation_version_selection_history (
                    concert_id,
                    repertoire_item_id,
                    previous_formation_id,
                    previous_formation_version_id,
                    selected_formation_id,
                    selected_formation_version_id,
                    actor_email
                )
                SELECT cr.concert_id,
                       cr.repertoire_item_id,
                       cr.selected_formation_id,
                       cr.selected_formation_version_id,
                       :formationId,
                       :formationVersionId,
                       :actorEmail
                FROM concert_repertoire cr
                WHERE cr.concert_id = :concertId
                  AND cr.repertoire_item_id = :repertoireItemId
                """)
                .bind("formationId", formationId)
                .bind("formationVersionId", formationVersionId)
                .bind("actorEmail", actorEmail)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No se pudo registrar el historial de selección")));
    }

    private Mono<Void> updateSelection(
            long concertId,
            long repertoireItemId,
            long formationId,
            long formationVersionId) {
        return database.sql("""
                UPDATE concert_repertoire
                SET selected_formation_id = :formationId,
                    selected_formation_version_id = :formationVersionId
                WHERE concert_id = :concertId
                  AND repertoire_item_id = :repertoireItemId
                """)
                .bind("formationId", formationId)
                .bind("formationVersionId", formationVersionId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No se pudo actualizar la selección de versión")));
    }
}
