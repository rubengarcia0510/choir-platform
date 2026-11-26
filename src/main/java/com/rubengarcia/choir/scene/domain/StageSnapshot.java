package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Copia histórica e inmutable de la geometría de un escenario, asociada a una inclusión del repertorio
 * (concierto + obra). Contiene toda la geometría, no referencias vivas a la plantilla: modificar la
 * plantilla después no altera ningún snapshot.
 */
public record StageSnapshot(
        Long id,
        long choirId,
        long concertId,
        long repertoireItemId,
        long sourceTemplateId,
        String name,
        BigDecimal widthCm,
        BigDecimal depthCm,
        StageBoundary boundary,
        List<StageAccess> accesses,
        List<StageElement> elements,
        LocalDateTime createdAt) {

    public StageSnapshot {
        if (choirId <= 0 || concertId <= 0 || repertoireItemId <= 0 || sourceTemplateId <= 0) {
            throw new StageGeometryException("Los identificadores del snapshot deben ser positivos");
        }
        Objects.requireNonNull(name, "name es obligatorio");
        if (name.isBlank()) {
            throw new StageGeometryException("El snapshot requiere un nombre");
        }
        Objects.requireNonNull(widthCm, "widthCm es obligatorio");
        Objects.requireNonNull(depthCm, "depthCm es obligatorio");
        Objects.requireNonNull(boundary, "boundary es obligatorio");
        Objects.requireNonNull(createdAt, "createdAt es obligatorio");
        accesses = List.copyOf(Objects.requireNonNull(accesses, "accesses es obligatorio"));
        elements = List.copyOf(Objects.requireNonNull(elements, "elements es obligatorio"));
        StageGeometryValidator.validate(widthCm, depthCm, boundary, accesses, elements);
    }

    /**
     * Crea el snapshot de una plantilla persistida y no archivada. La copia toma toda la geometría
     * en este momento; los cambios posteriores de la plantilla no la afectan.
     */
    public static StageSnapshot of(
            StageTemplate template, long concertId, long repertoireItemId, LocalDateTime createdAt) {
        Objects.requireNonNull(template, "template es obligatorio");
        Objects.requireNonNull(template.id(), "la plantilla debe estar persistida para generar un snapshot");
        if (template.archived()) {
            throw new IllegalStateException("No se puede asociar una plantilla archivada");
        }
        return new StageSnapshot(
                null,
                template.choirId(),
                concertId,
                repertoireItemId,
                template.id(),
                template.name(),
                template.widthCm(),
                template.depthCm(),
                template.boundary(),
                template.accesses(),
                template.elements(),
                createdAt);
    }
}
