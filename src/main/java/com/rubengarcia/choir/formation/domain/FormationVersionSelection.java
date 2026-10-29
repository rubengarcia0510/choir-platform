package com.rubengarcia.choir.formation.domain;

import java.time.LocalDateTime;

public record FormationVersionSelection(
        long concertId,
        long repertoireItemId,
        Long formationId,
        Long formationVersionId,
        LocalDateTime selectedAt) {

    public boolean hasSelection() {
        return formationId != null && formationVersionId != null;
    }
}
