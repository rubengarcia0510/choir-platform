package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.FormationVersion;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationVersionLifecycleServiceImpl
        implements FormationVersionLifecycleService {

    private final DatabaseClient database;
    private final ResourceAuthorizationService authorizationService;

    public FormationVersionLifecycleServiceImpl(
            DatabaseClient database,
            ResourceAuthorizationService authorizationService) {
        this.database = database;
        this.authorizationService = authorizationService;
    }

    @Override
    public Flux<FormationVersion> list(
            String email, long choirId, long formationId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(requireFormation(choirId, formationId))
                .thenMany(database.sql("""
                        SELECT id, formation_id, version_number, status,
                               notes, created_at, published_at
                        FROM formation_versions
                        WHERE formation_id = :formationId
                        ORDER BY version_number DESC
                        """)
                        .bind("formationId", formationId)
                        .map((row, metadata) -> mapVersion(row))
                        .all());
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationVersion> createDraft(
            String email, long choirId, long formationId, String notes) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(lockFormation(choirId, formationId))
                .then(database.sql("""
                        INSERT INTO formation_versions
                            (formation_id, version_number, status, notes)
                        SELECT :formationId,
                               COALESCE(MAX(version_number), 0) + 1,
                               'DRAFT',
                               :notes
                        FROM formation_versions
                        WHERE formation_id = :formationId
                        """)
                        .bind("formationId", formationId)
                        .bind("notes", notes == null ? "" : notes)
                        .fetch()
                        .rowsUpdated())
                .then(latestVersion(formationId))
                .flatMap(draft -> copyPublishedPositions(formationId, draft.getId())
                        .thenReturn(draft));
    }

    private Mono<Void> copyPublishedPositions(
            long formationId, long draftVersionId) {
        return database.sql("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot,
                     position_label, x_coordinate, y_coordinate)
                SELECT :draftVersionId,
                       fp.singer_id,
                       fp.singer_name_snapshot,
                       fp.voice_part_snapshot,
                       fp.voice_section_id_snapshot,
                       fp.voice_section_name_snapshot,
                       fp.division_number_snapshot,
                       fp.position_label,
                       fp.x_coordinate,
                       fp.y_coordinate
                FROM formation_positions fp
                JOIN formation_versions source
                  ON source.id = fp.formation_version_id
                WHERE source.formation_id = :formationId
                  AND source.status = 'PUBLISHED'
                """)
                .bind("draftVersionId", draftVersionId)
                .bind("formationId", formationId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<FormationVersion> published(
            String email, long choirId, long formationId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(requireFormation(choirId, formationId))
                .then(database.sql("""
                        SELECT id, formation_id, version_number, status,
                               notes, created_at, published_at
                        FROM formation_versions
                        WHERE formation_id = :formationId
                          AND status = 'PUBLISHED'
                        """)
                        .bind("formationId", formationId)
                        .map((row, metadata) -> mapVersion(row))
                        .one()
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "La formación todavía no tiene una versión publicada"))));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<FormationVersion> publish(
            String email, long choirId, long formationId, long versionId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(lockFormation(choirId, formationId))
                .then(findVersion(formationId, versionId))
                .flatMap(version -> {
                    if (!"DRAFT".equals(version.getStatus())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden publicar versiones en estado DRAFT"));
                    }

                    return database.sql("""
                            UPDATE formation_versions
                            SET status = 'ARCHIVED'
                            WHERE formation_id = :formationId
                              AND status = 'PUBLISHED'
                            """)
                            .bind("formationId", formationId)
                            .fetch()
                            .rowsUpdated()
                            .then(database.sql("""
                                    UPDATE formation_versions
                                    SET status = 'PUBLISHED',
                                        published_at = CURRENT_TIMESTAMP
                                    WHERE id = :versionId
                                      AND formation_id = :formationId
                                      AND status = 'DRAFT'
                                    """)
                                    .bind("versionId", versionId)
                                    .bind("formationId", formationId)
                                    .fetch()
                                    .rowsUpdated())
                            .flatMap(updated -> updated == 1
                                    ? findVersion(formationId, versionId)
                                    : Mono.error(new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "La versión cambió de estado y no pudo publicarse")));
                });
    }

    private Mono<Void> requireFormation(long choirId, long formationId) {
        return database.sql("""
                SELECT id
                FROM formations
                WHERE id = :formationId
                  AND choir_id = :choirId
                """)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Formación inexistente o fuera del coro")))
                .then();
    }

    private Mono<Void> lockFormation(long choirId, long formationId) {
        return database.sql("""
                SELECT id
                FROM formations
                WHERE id = :formationId
                  AND choir_id = :choirId
                FOR UPDATE
                """)
                .bind("formationId", formationId)
                .bind("choirId", choirId)
                .fetch()
                .first()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Formación inexistente o fuera del coro")))
                .then();
    }

    private Mono<FormationVersion> latestVersion(long formationId) {
        return database.sql("""
                SELECT id, formation_id, version_number, status,
                       notes, created_at, published_at
                FROM formation_versions
                WHERE formation_id = :formationId
                ORDER BY version_number DESC
                LIMIT 1
                """)
                .bind("formationId", formationId)
                .map((row, metadata) -> mapVersion(row))
                .one();
    }

    private Mono<FormationVersion> findVersion(
            long formationId, long versionId) {
        return database.sql("""
                SELECT id, formation_id, version_number, status,
                       notes, created_at, published_at
                FROM formation_versions
                WHERE formation_id = :formationId
                  AND id = :versionId
                """)
                .bind("formationId", formationId)
                .bind("versionId", versionId)
                .map((row, metadata) -> mapVersion(row))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Versión inexistente o fuera de la formación")));
    }

    private FormationVersion mapVersion(io.r2dbc.spi.Row row) {
        FormationVersion version = new FormationVersion();
        version.setId(row.get("id", Long.class));
        version.setFormationId(row.get("formation_id", Long.class));
        version.setVersionNumber(row.get("version_number", Integer.class));
        version.setStatus(row.get("status", String.class));
        version.setNotes(row.get("notes", String.class));
        version.setCreatedAt(
                row.get("created_at", java.time.LocalDateTime.class));
        version.setPublishedAt(
                row.get("published_at", java.time.LocalDateTime.class));
        return version;
    }
}
