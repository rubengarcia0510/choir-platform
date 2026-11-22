package com.rubengarcia.choir.formation.domain;

import java.time.LocalDateTime;

/**
 * Restricción de movilidad de un coreuta dentro de una formación.
 *
 * <p>Es independiente de {@link FormationVisibilityConstraint} y de
 * {@link FormationProximityConstraint}, y no reemplaza los campos de texto libre
 * {@code singers.mobility} ni {@code singers.restrictions}.</p>
 */
public record FormationMobilityConstraint(
        Long id,
        Long formationId,
        Long singerId,
        MobilityRegion region,
        boolean active,
        LocalDateTime createdAt) {

    public FormationMobilityConstraint {
        if (formationId == null) {
            throw new IllegalArgumentException("formationId es obligatorio");
        }
        if (singerId == null) {
            throw new IllegalArgumentException("singerId es obligatorio");
        }
        if (region == null) {
            throw new IllegalArgumentException("region es obligatoria");
        }
    }
}
