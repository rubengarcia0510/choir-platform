package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.scene.domain.StageAccess;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageElement;
import com.rubengarcia.choir.scene.domain.StageElementType;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lectura y escritura de los detalles de geometría (contorno, accesos y elementos) de plantillas y
 * snapshots. Cada detalle se identifica por (padre, seq_no); la lectura exige secuencias consecutivas
 * desde cero y la escritura las genera a partir del índice de la lista.
 */
final class StageGeometryRows {

    /** Tablas y columna de padre de una geometría (plantilla o snapshot). */
    record Tables(String boundary, String accesses, String elements, String parentColumn) {

        static final Tables TEMPLATE = new Tables(
                "stage_boundary_points", "stage_accesses", "stage_elements", "stage_template_id");

        static final Tables SNAPSHOT = new Tables(
                "concert_stage_snapshot_boundary_points",
                "concert_stage_snapshot_accesses",
                "concert_stage_snapshot_elements",
                "snapshot_id");
    }

    /** Detalle leído junto con su posición en la lista. */
    record Seq<T>(int seqNo, T value) {
    }

    private StageGeometryRows() {
    }

    static Mono<StageBoundary> loadBoundary(DatabaseClient db, Tables t, long choirId, long parentId) {
        return db.sql("SELECT seq_no, x_cm, y_cm FROM " + t.boundary()
                        + " WHERE " + t.parentColumn() + " = :parentId AND choir_id = :choirId ORDER BY seq_no")
                .bind("parentId", parentId)
                .bind("choirId", choirId)
                .map((row, meta) -> new Seq<>(row.get("seq_no", Integer.class), new StageBoundaryPoint(
                        row.get("x_cm", BigDecimal.class), row.get("y_cm", BigDecimal.class))))
                .all()
                .collectList()
                .map(seqs -> new StageBoundary(ordered(seqs)));
    }

    static Mono<List<StageAccess>> loadAccesses(DatabaseClient db, Tables t, long choirId, long parentId) {
        return db.sql("SELECT seq_no, name, start_x_cm, start_y_cm, end_x_cm, end_y_cm FROM " + t.accesses()
                        + " WHERE " + t.parentColumn() + " = :parentId AND choir_id = :choirId ORDER BY seq_no")
                .bind("parentId", parentId)
                .bind("choirId", choirId)
                .map((row, meta) -> new Seq<>(row.get("seq_no", Integer.class), new StageAccess(
                        row.get("name", String.class),
                        new StageBoundaryPoint(row.get("start_x_cm", BigDecimal.class), row.get("start_y_cm", BigDecimal.class)),
                        new StageBoundaryPoint(row.get("end_x_cm", BigDecimal.class), row.get("end_y_cm", BigDecimal.class)))))
                .all()
                .collectList()
                .map(seqs -> ordered(seqs));
    }

    static Mono<List<StageElement>> loadElements(DatabaseClient db, Tables t, long choirId, long parentId) {
        return db.sql("SELECT seq_no, name, element_type, x_cm, y_cm, width_cm, depth_cm, elevation_cm FROM "
                        + t.elements() + " WHERE " + t.parentColumn()
                        + " = :parentId AND choir_id = :choirId ORDER BY seq_no")
                .bind("parentId", parentId)
                .bind("choirId", choirId)
                .map((row, meta) -> new Seq<>(row.get("seq_no", Integer.class), element(row)))
                .all()
                .collectList()
                .map(seqs -> ordered(seqs));
    }

    static Mono<Void> insertGeometry(DatabaseClient db, Tables t, long choirId, long parentId,
            StageBoundary boundary, List<StageAccess> accesses, List<StageElement> elements) {
        return insertBoundary(db, t, choirId, parentId, boundary.vertices())
                .then(insertAccesses(db, t, choirId, parentId, accesses))
                .then(insertElements(db, t, choirId, parentId, elements));
    }

    /**
     * El borrado solo puede operar sobre geometría de plantillas.
     * Los detalles de snapshots son inmutables y no tienen una operación de borrado.
     */
    static Mono<Void> deleteTemplateGeometry(DatabaseClient db, long choirId, long parentId) {
        return db.sql("DELETE FROM stage_elements WHERE stage_template_id = :parentId AND choir_id = :choirId")
                .bind("parentId", parentId).bind("choirId", choirId).then()
                .then(db.sql("DELETE FROM stage_accesses WHERE stage_template_id = :parentId AND choir_id = :choirId")
                        .bind("parentId", parentId).bind("choirId", choirId).then())
                .then(db.sql("DELETE FROM stage_boundary_points WHERE stage_template_id = :parentId AND choir_id = :choirId")
                        .bind("parentId", parentId).bind("choirId", choirId).then());
    }

    private static Mono<Void> insertBoundary(DatabaseClient db, Tables t, long choirId, long parentId,
            List<StageBoundaryPoint> vertices) {
        return Flux.range(0, vertices.size()).concatMap(i -> {
            StageBoundaryPoint v = vertices.get(i);
            return db.sql("INSERT INTO " + t.boundary() + " (" + t.parentColumn()
                            + ", choir_id, seq_no, x_cm, y_cm) VALUES (:parentId, :choirId, :seq, :x, :y)")
                    .bind("parentId", parentId)
                    .bind("choirId", choirId)
                    .bind("seq", i)
                    .bind("x", centimetres(v.x()))
                    .bind("y", centimetres(v.y()))
                    .then();
        }).then();
    }

    private static Mono<Void> insertAccesses(DatabaseClient db, Tables t, long choirId, long parentId,
            List<StageAccess> accesses) {
        return Flux.range(0, accesses.size()).concatMap(i -> {
            StageAccess a = accesses.get(i);
            return db.sql("INSERT INTO " + t.accesses() + " (" + t.parentColumn()
                            + ", choir_id, seq_no, name, start_x_cm, start_y_cm, end_x_cm, end_y_cm)"
                            + " VALUES (:parentId, :choirId, :seq, :name, :sx, :sy, :ex, :ey)")
                    .bind("parentId", parentId)
                    .bind("choirId", choirId)
                    .bind("seq", i)
                    .bind("name", a.name())
                    .bind("sx", centimetres(a.start().x()))
                    .bind("sy", centimetres(a.start().y()))
                    .bind("ex", centimetres(a.end().x()))
                    .bind("ey", centimetres(a.end().y()))
                    .then();
        }).then();
    }

    private static Mono<Void> insertElements(DatabaseClient db, Tables t, long choirId, long parentId,
            List<StageElement> elements) {
        return Flux.range(0, elements.size()).concatMap(i -> {
            StageElement e = elements.get(i);
            return db.sql("INSERT INTO " + t.elements() + " (" + t.parentColumn()
                            + ", choir_id, seq_no, name, element_type, x_cm, y_cm, width_cm, depth_cm, elevation_cm)"
                            + " VALUES (:parentId, :choirId, :seq, :name, :type, :x, :y, :w, :d, :h)")
                    .bind("parentId", parentId)
                    .bind("choirId", choirId)
                    .bind("seq", i)
                    .bind("name", e.name())
                    .bind("type", e.type().name())
                    .bind("x", centimetres(e.x()))
                    .bind("y", centimetres(e.y()))
                    .bind("w", centimetres(e.widthCm()))
                    .bind("d", centimetres(e.depthCm()))
                    .bind("h", centimetres(e.elevationCm()))
                    .then();
        }).then();
    }

    private static StageElement element(Row row) {
        return new StageElement(
                row.get("name", String.class),
                StageElementType.valueOf(row.get("element_type", String.class)),
                row.get("x_cm", BigDecimal.class),
                row.get("y_cm", BigDecimal.class),
                row.get("width_cm", BigDecimal.class),
                row.get("depth_cm", BigDecimal.class),
                row.get("elevation_cm", BigDecimal.class));
    }

    /** Devuelve los valores ordenados exigiendo secuencia consecutiva 0..n-1. */
    static <T> List<T> ordered(List<Seq<T>> seqs) {
        List<T> values = new ArrayList<>(seqs.size());
        for (int i = 0; i < seqs.size(); i++) {
            if (seqs.get(i).seqNo() != i) {
                throw new IllegalStateException("Orden de detalle de escenario inconsistente en la base de datos");
            }
            values.add(seqs.get(i).value());
        }
        return values;
    }

    /** Rechaza valores con más de tres decimales en lugar de redondearlos en silencio. */
    static BigDecimal centimetres(BigDecimal value) {
        if (value.stripTrailingZeros().scale() > 3) {
            throw new IllegalArgumentException("Las medidas de escenario admiten hasta tres decimales");
        }
        return value;
    }
}
