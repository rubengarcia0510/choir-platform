package com.rubengarcia.choir.scene.domain;

import java.util.List;
import java.util.Objects;

/** Contorno del escenario como polígono de vértices ordenados. La validez geométrica la comprueba {@link StageGeometryValidator}. */
public record StageBoundary(List<StageBoundaryPoint> vertices) {

    public StageBoundary {
        Objects.requireNonNull(vertices, "vertices es obligatorio");
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3) {
            throw new StageGeometryException("El contorno requiere al menos tres vértices");
        }
        for (int i = 0; i < vertices.size(); i++) {
            StageBoundaryPoint current = vertices.get(i);
            StageBoundaryPoint next = vertices.get((i + 1) % vertices.size());
            Objects.requireNonNull(current, "los vértices no pueden ser nulos");
            if (current.sameAs(next)) {
                throw new StageGeometryException("El contorno no admite vértices consecutivos repetidos");
            }
        }
    }
}
