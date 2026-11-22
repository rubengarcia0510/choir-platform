package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.FormationProximityConstraint;
import java.math.BigDecimal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationProximityConstraintPort {

    Flux<FormationProximityConstraint> findForFormation(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    Mono<FormationProximityConstraint> create(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerOneId,
            long singerTwoId,
            BigDecimal maxDistance);

    Mono<FormationProximityConstraint> update(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            BigDecimal maxDistance,
            boolean active);

    Mono<Boolean> delete(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId);
}
