package com.rubengarcia.choir.scene.application.model;

import com.rubengarcia.choir.scene.domain.StageAccess;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageElement;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Modelo de lectura que CHOIR-25 consume para la geometría histórica de una inclusión. Expone
 * dimensiones, contorno, accesos, elementos (con sus superficies ocupadas y elevaciones) e
 * identificador y versión del snapshot. Versión 1: los snapshots son inmutables.
 */
public record StageGeometry(
        long snapshotId,
        int version,
        BigDecimal widthCm,
        BigDecimal depthCm,
        StageBoundary boundary,
        List<StageAccess> accesses,
        List<StageElement> elements) {

    public static StageGeometry from(StageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot es obligatorio");
        Objects.requireNonNull(snapshot.id(), "el snapshot debe estar persistido");
        return new StageGeometry(
                snapshot.id(),
                1,
                snapshot.widthCm(),
                snapshot.depthCm(),
                snapshot.boundary(),
                snapshot.accesses(),
                snapshot.elements());
    }
}
