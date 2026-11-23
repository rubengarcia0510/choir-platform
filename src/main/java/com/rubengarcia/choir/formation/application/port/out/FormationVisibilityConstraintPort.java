package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVisibilityConstraintPort {

    Flux<FormationVisibilityConstraint> findForFormation(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    Mono<FormationVisibilityConstraint> create(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long protectedSingerId);

    Mono<FormationVisibilityConstraint> update(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            boolean active);

    Mono<Boolean> delete(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId);
}
