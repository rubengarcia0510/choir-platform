package com.rubengarcia.choir.scene.domain;

import java.util.Objects;

/** Acceso al escenario: segmento sobre una arista del contorno. Se valida contra el contorno en la plantilla. */
public record StageAccess(String name, StageBoundaryPoint start, StageBoundaryPoint end) {

    public StageAccess {
        Objects.requireNonNull(name, "name es obligatorio");
        if (name.isBlank()) {
            throw new StageGeometryException("El acceso requiere un nombre");
        }
        Objects.requireNonNull(start, "start es obligatorio");
        Objects.requireNonNull(end, "end es obligatorio");
        if (start.sameAs(end)) {
            throw new StageGeometryException("El acceso requiere un segmento de longitud positiva");
        }
    }
}
