package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVisibilityConstraintUseCase {

    Flux<FormationVisibilityConstraint> list(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    Mono<FormationVisibilityConstraint> create(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long protectedSingerId);

    Mono<FormationVisibilityConstraint> update(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            boolean active);

    Mono<Boolean> delete(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId);
}
