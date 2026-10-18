package com.rubengarcia.choir.service;

import reactor.core.publisher.Mono;

public interface ResourceAuthorizationService {

    Mono<Void> requireOwnedChoir(String email, long choirId);

    Mono<Void> requireOwnedConcert(
            String email, long choirId, long concertId);

    Mono<Void> requireOwnedWork(
            String email, long choirId, long workId);

    Mono<Void> requireOwnedConcertWork(
            String email, long choirId, long concertId, long workId);

    Mono<Void> requireOwnedFormationVersion(
            String email, long choirId, long formationVersionId);

    Mono<Void> requireOwnedTransitionVersion(
            String email, long choirId, long transitionVersionId);

    Mono<Void> requireOwnedPosition(
            String email, long choirId, long positionId);
}
