package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Plantilla reutilizable de escenario de un coro. Las invariantes geométricas se comprueban al construir
 * la instancia; ninguna plantilla inválida puede existir en el dominio.
 */
public record StageTemplate(
        Long id,
        long choirId,
        String name,
        BigDecimal widthCm,
        BigDecimal depthCm,
        StageBoundary boundary,
        List<StageAccess> accesses,
        List<StageElement> elements,
        boolean archived) {

    public StageTemplate {
        if (choirId <= 0) {
            throw new StageGeometryException("El coro es obligatorio");
        }
        Objects.requireNonNull(name, "name es obligatorio");
        if (name.isBlank()) {
            throw new StageGeometryException("La plantilla requiere un nombre");
        }
        Objects.requireNonNull(widthCm, "widthCm es obligatorio");
        Objects.requireNonNull(depthCm, "depthCm es obligatorio");
        if (widthCm.signum() <= 0 || depthCm.signum() <= 0) {
            throw new StageGeometryException("Las dimensiones del escenario deben ser positivas");
        }
        Objects.requireNonNull(boundary, "boundary es obligatorio");
        accesses = List.copyOf(Objects.requireNonNull(accesses, "accesses es obligatorio"));
        elements = List.copyOf(Objects.requireNonNull(elements, "elements es obligatorio"));
        StageGeometryValidator.validate(widthCm, depthCm, boundary, accesses, elements);
    }
}
