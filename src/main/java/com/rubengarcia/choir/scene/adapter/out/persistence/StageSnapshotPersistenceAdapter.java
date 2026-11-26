package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.scene.application.port.out.StageSnapshotPersistencePort;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Snapshots históricos: solo lectura e inserción. No existe ningún método de actualización ni de borrado,
 * ni siquiera indirecto. La geometría se lee de las tablas del snapshot, nunca de la plantilla.
 */
@Component
public class StageSnapshotPersistenceAdapter implements StageSnapshotPersistencePort {

    private final DatabaseClient databaseClient;

    public StageSnapshotPersistenceAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<StageSnapshot> findForInclusion(long choirId, long concertId, long repertoireItemId) {
        return databaseClient.sql("""
                        SELECT id, choir_id, concert_id, repertoire_item_id, source_template_id,
                               name, width_cm, depth_cm, created_at
                        FROM concert_stage_snapshots
                        WHERE choir_id = :choirId AND concert_id = :concertId
                          AND repertoire_item_id = :repertoireItemId
                        """)
                .bind("choirId", choirId)
                .bind("concertId", concertId)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, meta) -> new Header(
                        row.get("id", Long.class),
                        row.get("choir_id", Long.class),
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("source_template_id", Long.class),
                        row.get("name", String.class),
                        row.get("width_cm", BigDecimal.class),
                        row.get("depth_cm", BigDecimal.class),
                        row.get("created_at", LocalDateTime.class)))
                .one()
                .flatMap(this::load);
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<StageSnapshot> insert(StageSnapshot snapshot) {
        return databaseClient.sql("""
                        INSERT INTO concert_stage_snapshots
                            (choir_id, concert_id, repertoire_item_id, source_template_id,
                             name, width_cm, depth_cm, created_at)
                        VALUES (:choirId, :concertId, :repertoireItemId, :sourceTemplateId,
                                :name, :width, :depth, :createdAt)
                        """)
                .bind("choirId", snapshot.choirId())
                .bind("concertId", snapshot.concertId())
                .bind("repertoireItemId", snapshot.repertoireItemId())
                .bind("sourceTemplateId", snapshot.sourceTemplateId())
                .bind("name", snapshot.name())
                .bind("width", StageGeometryRows.centimetres(snapshot.widthCm()))
                .bind("depth", StageGeometryRows.centimetres(snapshot.depthCm()))
                .bind("createdAt", snapshot.createdAt())
                .filter((statement, next) -> statement.returnGeneratedValues("id").execute())
                .map((row, meta) -> row.get("id", Long.class))
                .one()
                .flatMap(snapshotId -> StageGeometryRows.insertGeometry(databaseClient,
                                StageGeometryRows.Tables.SNAPSHOT, snapshot.choirId(), snapshotId,
                                snapshot.boundary(), snapshot.accesses(), snapshot.elements())
                        .then(findById(snapshot.choirId(), snapshotId)));
    }

    private Mono<StageSnapshot> findById(long choirId, long snapshotId) {
        return databaseClient.sql("""
                        SELECT id, choir_id, concert_id, repertoire_item_id, source_template_id,
                               name, width_cm, depth_cm, created_at
                        FROM concert_stage_snapshots
                        WHERE id = :id AND choir_id = :choirId
                        """)
                .bind("id", snapshotId)
                .bind("choirId", choirId)
                .map((row, meta) -> new Header(
                        row.get("id", Long.class),
                        row.get("choir_id", Long.class),
                        row.get("concert_id", Long.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("source_template_id", Long.class),
                        row.get("name", String.class),
                        row.get("width_cm", BigDecimal.class),
                        row.get("depth_cm", BigDecimal.class),
                        row.get("created_at", LocalDateTime.class)))
                .one()
                .flatMap(this::load);
    }

    private Mono<StageSnapshot> load(Header header) {
        long snapshotId = header.id();
        long choirId = header.choirId();
        return StageGeometryRows.loadBoundary(databaseClient, StageGeometryRows.Tables.SNAPSHOT, choirId, snapshotId)
                .flatMap(boundary -> StageGeometryRows.loadAccesses(databaseClient,
                                StageGeometryRows.Tables.SNAPSHOT, choirId, snapshotId)
                        .flatMap(accesses -> StageGeometryRows.loadElements(databaseClient,
                                        StageGeometryRows.Tables.SNAPSHOT, choirId, snapshotId)
                                .map(elements -> new StageSnapshot(
                                        snapshotId,
                                        choirId,
                                        header.concertId(),
                                        header.repertoireItemId(),
                                        header.sourceTemplateId(),
                                        header.name(),
                                        header.widthCm(),
                                        header.depthCm(),
                                        boundary,
                                        accesses,
                                        elements,
                                        header.createdAt()))));
    }

    private record Header(Long id, long choirId, long concertId, long repertoireItemId, long sourceTemplateId,
            String name, BigDecimal widthCm, BigDecimal depthCm, LocalDateTime createdAt) {
    }
}
