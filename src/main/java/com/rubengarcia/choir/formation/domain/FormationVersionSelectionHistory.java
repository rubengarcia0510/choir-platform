package com.rubengarcia.choir.formation.domain;

import java.time.LocalDateTime;

public record FormationVersionSelectionHistory(
        long id,
        long concertId,
        long repertoireItemId,
        Long previousFormationId,
        Long previousFormationVersionId,
        long selectedFormationId,
        long selectedFormationVersionId,
        String actorEmail,
        LocalDateTime selectedAt) {
}
