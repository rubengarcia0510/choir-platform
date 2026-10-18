package com.rubengarcia.choir.service.impl;

import com.rubengarcia.choir.repository.ResourceAuthorizationRepository;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class ResourceAuthorizationServiceImpl
        implements ResourceAuthorizationService {

    private final ResourceAuthorizationRepository repository;

    public ResourceAuthorizationServiceImpl(
            ResourceAuthorizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Void> requireOwnedChoir(String email, long choirId) {
        return authorize(email,
                () -> repository.directorOwnsChoir(email, choirId));
    }

    @Override
    public Mono<Void> requireOwnedConcert(
            String email, long choirId, long concertId) {
        return authorize(email,
                () -> repository.directorOwnsConcert(
                        email, choirId, concertId));
    }

    @Override
    public Mono<Void> requireOwnedWork(
            String email, long choirId, long workId) {
        return authorize(email,
                () -> repository.directorOwnsWork(
                        email, choirId, workId));
    }

    @Override
    public Mono<Void> requireOwnedConcertWork(
            String email, long choirId, long concertId, long workId) {
        return authorize(email,
                () -> repository.directorOwnsConcertWork(
                        email, choirId, concertId, workId));
    }

    @Override
    public Mono<Void> requireOwnedFormationVersion(
            String email, long choirId, long formationVersionId) {
        return authorize(email,
                () -> repository.directorOwnsFormationVersion(
                        email, choirId, formationVersionId));
    }

    @Override
    public Mono<Void> requireOwnedTransitionVersion(
            String email, long choirId, long transitionVersionId) {
        return authorize(email,
                () -> repository.directorOwnsTransitionVersion(
                        email, choirId, transitionVersionId));
    }

    @Override
    public Mono<Void> requireOwnedPosition(
            String email, long choirId, long positionId) {
        return authorize(email,
                () -> repository.directorOwnsPosition(
                        email, choirId, positionId));
    }

    private Mono<Void> authorize(
            String email, Supplier<Mono<Boolean>> ownershipCheck) {
        if (email == null || email.isBlank()) {
            return Mono.error(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return Mono.defer(ownershipCheck)
                .flatMap(owned -> owned
                        ? Mono.<Void>empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND)));
    }
}
