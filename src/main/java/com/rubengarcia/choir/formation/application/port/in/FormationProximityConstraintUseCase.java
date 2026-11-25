package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.FormationProximityConstraint;
import java.math.BigDecimal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationProximityConstraintUseCase {

    Flux<FormationProximityConstraint> list(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    Mono<FormationProximityConstraint> create(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerOneId,
            long singerTwoId,
            BigDecimal maxDistance);

    Mono<FormationProximityConstraint> update(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            BigDecimal maxDistance,
            boolean active);

    Mono<Boolean> delete(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId);
}
