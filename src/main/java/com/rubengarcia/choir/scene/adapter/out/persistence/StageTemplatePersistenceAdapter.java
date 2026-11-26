package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.scene.application.port.out.StageTemplatePersistencePort;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Persistencia de plantillas con sus contornos, accesos y elementos. Todas las consultas filtran por coro. */
@Component
public class StageTemplatePersistenceAdapter implements StageTemplatePersistencePort {

    private final DatabaseClient databaseClient;

    public StageTemplatePersistenceAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<StageTemplate> findByChoir(long choirId) {
        return databaseClient.sql("SELECT id FROM stage_templates WHERE choir_id = :choirId ORDER BY id")
                .bind("choirId", choirId)
                .map((row, meta) -> row.get("id", Long.class))
                .all()
                .concatMap(stageId -> load(choirId, stageId));
    }

    @Override
    public Mono<StageTemplate> findById(long choirId, long stageId) {
        return load(choirId, stageId);
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<StageTemplate> save(StageTemplate template) {
        if (template.id() == null) {
            return insert(template);
        }
        return update(template);
    }

    private Mono<StageTemplate> insert(StageTemplate template) {
        LocalDateTime now = LocalDateTime.now();
        return databaseClient.sql("""
                        INSERT INTO stage_templates
                            (choir_id, name, width_cm, depth_cm, archived, created_at, updated_at)
                        VALUES (:choirId, :name, :width, :depth, :archived, :now, :now)
                        """)
                .bind("choirId", template.choirId())
                .bind("name", template.name())
                .bind("width", StageGeometryRows.centimetres(template.widthCm()))
                .bind("depth", StageGeometryRows.centimetres(template.depthCm()))
                .bind("archived", template.archived())
                .bind("now", now)
                .filter((statement, next) -> statement.returnGeneratedValues("id").execute())
                .map((row, meta) -> row.get("id", Long.class))
                .one()
                .flatMap(stageId -> StageGeometryRows.insertGeometry(databaseClient,
                                StageGeometryRows.Tables.TEMPLATE, template.choirId(), stageId,
                                template.boundary(), template.accesses(), template.elements())
                        .then(load(template.choirId(), stageId)));
    }

    private Mono<StageTemplate> update(StageTemplate template) {
        return databaseClient.sql("""
                        UPDATE stage_templates
                        SET name = :name, width_cm = :width, depth_cm = :depth,
                            archived = :archived, updated_at = :now
                        WHERE id = :id AND choir_id = :choirId
                        """)
                .bind("name", template.name())
                .bind("width", StageGeometryRows.centimetres(template.widthCm()))
                .bind("depth", StageGeometryRows.centimetres(template.depthCm()))
                .bind("archived", template.archived())
                .bind("now", LocalDateTime.now())
                .bind("id", template.id())
                .bind("choirId", template.choirId())
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> {
                    if (updated == 0) {
                        return Mono.empty();
                    }
                    return StageGeometryRows.deleteGeometry(databaseClient,
                                    StageGeometryRows.Tables.TEMPLATE, template.choirId(), template.id())
                            .then(StageGeometryRows.insertGeometry(databaseClient,
                                    StageGeometryRows.Tables.TEMPLATE, template.choirId(), template.id(),
                                    template.boundary(), template.accesses(), template.elements()))
                            .then(load(template.choirId(), template.id()));
                });
    }

    private Mono<StageTemplate> load(long choirId, long stageId) {
        return databaseClient.sql("""
                        SELECT id, choir_id, name, width_cm, depth_cm, archived
                        FROM stage_templates
                        WHERE id = :id AND choir_id = :choirId
                        """)
                .bind("id", stageId)
                .bind("choirId", choirId)
                .map((row, meta) -> new Header(
                        row.get("id", Long.class),
                        row.get("choir_id", Long.class),
                        row.get("name", String.class),
                        row.get("width_cm", BigDecimal.class),
                        row.get("depth_cm", BigDecimal.class),
                        Boolean.TRUE.equals(row.get("archived", Boolean.class))))
                .one()
                .flatMap(header -> StageGeometryRows.loadBoundary(databaseClient,
                                StageGeometryRows.Tables.TEMPLATE, choirId, stageId)
                        .flatMap(boundary -> StageGeometryRows.loadAccesses(databaseClient,
                                        StageGeometryRows.Tables.TEMPLATE, choirId, stageId)
                                .flatMap(accesses -> StageGeometryRows.loadElements(databaseClient,
                                                StageGeometryRows.Tables.TEMPLATE, choirId, stageId)
                                        .map(elements -> new StageTemplate(
                                                header.id(), header.choirId(), header.name(),
                                                header.widthCm(), header.depthCm(),
                                                boundary, accesses, elements, header.archived())))));
    }

    private record Header(Long id, long choirId, String name, BigDecimal widthCm, BigDecimal depthCm,
            boolean archived) {
    }
}
