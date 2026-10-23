package com.rubengarcia.choir.formation.application.model;

import java.math.BigDecimal;

/**
 * Posición de una formación con los datos vocales históricos.
 */
public record PlanningPosition(
        long formationVersionId,
        long singerId,
        String singerName,
        String voicePart,
        Long voiceSectionId,
        String voiceSectionName,
        Integer divisionNumber,
        String positionLabel,
        BigDecimal xCoordinate,
        BigDecimal yCoordinate) {
}
