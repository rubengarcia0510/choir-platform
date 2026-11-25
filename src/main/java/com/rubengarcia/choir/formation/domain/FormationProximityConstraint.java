package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FormationProximityConstraint(
        Long id,
        Long formationId,
        Long singerOneId,
        Long singerTwoId,
        BigDecimal maxDistance,
        boolean active,
        LocalDateTime createdAt) {
}
