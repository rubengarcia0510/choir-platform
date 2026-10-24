package com.rubengarcia.choir.formation.application.port.out;

import reactor.core.publisher.Mono;

public interface FormationPlanningResourceAuthorizationPort {

    Mono<Void> requireOwnedFormationVersion(
            String email, long choirId, long formationVersionId);

    Mono<Void> requireOwnedConcertWork(
            String email, long choirId, long concertId, long repertoireItemId);

    Mono<Void> requireOwnedTransitionVersion(
            String email, long choirId, long transitionVersionId);

    Mono<Void> requireOwnedPosition(
            String email, long choirId, long positionId);
}
