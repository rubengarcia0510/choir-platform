package com.rubengarcia.choir.formation.application.model;

/**
 * Asignación vocal publicada para una obra dentro de un concierto.
 */
public record VocalAssignment(
        long concertId,
        long repertoireItemId,
        Long singerId,
        String singerName,
        String voicePart,
        Long voiceSectionId,
        String voiceSectionName,
        int divisionNumber) {
}
