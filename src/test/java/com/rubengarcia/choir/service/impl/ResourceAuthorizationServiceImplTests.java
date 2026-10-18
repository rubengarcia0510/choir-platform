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
class ResourceAuthorizationServiceImplTests {

@Mock
private ResourceAuthorizationRepository repository;

@InjectMocks
private ResourceAuthorizationServiceImpl service;

@Test
void authorizesOwnedChoir() {
    when(repository.directorOwnsChoir("director@example.com", 1L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedChoir("director@example.com", 1L))
            .verifyComplete();
}

@Test
void authorizesOwnedConcert() {
    when(repository.directorOwnsConcert("director@example.com", 1L, 2L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedConcert(
                    "director@example.com", 1L, 2L))
            .verifyComplete();
}

@Test
void authorizesOwnedWork() {
    when(repository.directorOwnsWork("director@example.com", 1L, 3L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedWork(
                    "director@example.com", 1L, 3L))
            .verifyComplete();
}

@Test
void authorizesWorkAssociatedWithOwnedConcert() {
    when(repository.directorOwnsConcertWork(
            "director@example.com", 1L, 2L, 3L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedConcertWork(
                    "director@example.com", 1L, 2L, 3L))
            .verifyComplete();
}

@Test
void authorizesOwnedFormationVersion() {
    when(repository.directorOwnsFormationVersion(
            "director@example.com", 1L, 4L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedFormationVersion(
                    "director@example.com", 1L, 4L))
            .verifyComplete();
}

@Test
void authorizesOwnedTransitionVersion() {
    when(repository.directorOwnsTransitionVersion(
            "director@example.com", 1L, 5L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedTransitionVersion(
                    "director@example.com", 1L, 5L))
            .verifyComplete();
}

@Test
void authorizesOwnedPosition() {
    when(repository.directorOwnsPosition(
            "director@example.com", 1L, 6L))
            .thenReturn(Mono.just(true));

    StepVerifier.create(service.requireOwnedPosition(
                    "director@example.com", 1L, 6L))
            .verifyComplete();
}

@Test
void rejectsResourceNotOwnedByDirectorWithoutRevealingExistence() {
    when(repository.directorOwnsChoir("director@example.com", 99L))
            .thenReturn(Mono.just(false));

    StepVerifier.create(service.requireOwnedChoir(
                    "director@example.com", 99L))
            .expectErrorSatisfies(error ->
                    assertEquals(HttpStatus.NOT_FOUND,
                            ((ResponseStatusException) error).getStatusCode()))
            .verify();
}

@Test
void rejectsMissingIdentityBeforeQueryingRepository() {
    StepVerifier.create(service.requireOwnedChoir(null, 1L))
            .expectErrorSatisfies(error ->
                    assertEquals(HttpStatus.UNAUTHORIZED,
                            ((ResponseStatusException) error).getStatusCode()))
            .verify();

    StepVerifier.create(service.requireOwnedChoir("   ", 1L))
            .expectErrorSatisfies(error ->
                    assertEquals(HttpStatus.UNAUTHORIZED,
                            ((ResponseStatusException) error).getStatusCode()))
            .verify();

    verifyNoInteractions(repository);
}

@Test
void propagatesRepositoryErrors() {
    RuntimeException failure = new RuntimeException("database unavailable");
    when(repository.directorOwnsPosition("director@example.com", 1L, 6L))
            .thenReturn(Mono.error(failure));

    StepVerifier.create(service.requireOwnedPosition(
                    "director@example.com", 1L, 6L))
            .expectErrorSatisfies(error ->
                    assertEquals(failure, error))
            .verify();
}

}
