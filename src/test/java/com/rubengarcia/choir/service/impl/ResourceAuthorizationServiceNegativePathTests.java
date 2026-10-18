package com.rubengarcia.choir.service.impl;

import com.rubengarcia.choir.repository.ResourceAuthorizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceAuthorizationServiceNegativePathTests {

    @Mock
    private ResourceAuthorizationRepository repository;

    @InjectMocks
    private ResourceAuthorizationServiceImpl service;

    @Test
    void rejectsConcertNotOwnedByDirectorWithoutRevealingExistence() {
        when(repository.directorOwnsConcert("other@example.test", 10L, 20L))
                .thenReturn(Mono.just(false));

        assertNotFound(service.requireOwnedConcert(
                "other@example.test", 10L, 20L));
    }

    @Test
    void rejectsWorkNotOwnedByDirectorWithoutRevealingExistence() {
        when(repository.directorOwnsWork("other@example.test", 10L, 30L))
                .thenReturn(Mono.just(false));

        assertNotFound(service.requireOwnedWork(
                "other@example.test", 10L, 30L));
    }

    @Test
    void rejectsConcertWorkNotOwnedByDirectorWithoutRevealingExistence() {
        when(repository.directorOwnsConcertWork(
                "other@example.test", 10L, 20L, 30L))
                .thenReturn(Mono.just(false));

        assertNotFound(service.requireOwnedConcertWork(
                "other@example.test", 10L, 20L, 30L));
    }

    @Test
    void rejectsFormationVersionNotOwnedByDirectorWithoutRevealingExistence() {
        when(repository.directorOwnsFormationVersion(
                "other@example.test", 10L, 40L))
                .thenReturn(Mono.just(false));

        assertNotFound(service.requireOwnedFormationVersion(
                "other@example.test", 10L, 40L));
    }

    @Test
    void rejectsTransitionVersionNotOwnedByDirectorWithoutRevealingExistence() {
        when(repository.directorOwnsTransitionVersion(
                "other@example.test", 10L, 50L))
                .thenReturn(Mono.just(false));

        assertNotFound(service.requireOwnedTransitionVersion(
                "other@example.test", 10L, 50L));
    }

    @Test
    void rejectsPositionNotOwnedByDirectorWithoutRevealingExistence() {
        when(repository.directorOwnsPosition("other@example.test", 10L, 60L))
                .thenReturn(Mono.just(false));

        assertNotFound(service.requireOwnedPosition(
                "other@example.test", 10L, 60L));
    }

    private void assertNotFound(Mono<Void> authorization) {
        StepVerifier.create(authorization)
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();
    }
}
