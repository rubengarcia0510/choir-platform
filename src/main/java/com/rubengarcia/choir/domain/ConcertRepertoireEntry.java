package com.rubengarcia.choir.domain;

public record ConcertRepertoireEntry(
        Long concertId,
        Long repertoireItemId,
        Integer orderIndex,
        Integer durationMinutes,
        String stageRequirements,
        String title,
        String composer,
        String arranger) {
}
