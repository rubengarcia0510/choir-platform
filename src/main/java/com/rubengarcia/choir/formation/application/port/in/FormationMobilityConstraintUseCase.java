package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationMobilityConstraintUseCase {

    Flux<FormationMobilityConstraint> list(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    Mono<FormationMobilityConstraint> create(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerId,
            MobilityRegion region);

    Mono<FormationMobilityConstraint> update(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            MobilityRegion region,
            boolean active);

    Mono<Boolean> delete(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId);
}
