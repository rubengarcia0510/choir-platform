package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.port.in.FormationVisibilityConstraintUseCase;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FormationVisibilityConstraintControllerMappingTests {

    private static final String DIRECTOR = "director";

    private FormationVisibilityConstraintUseCase useCase;
    private Authentication authentication;
    private FormationVisibilityConstraintController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(FormationVisibilityConstraintUseCase.class);
        authentication = mock(Authentication.class);
        controller = new FormationVisibilityConstraintController(useCase);
        when(authentication.getName()).thenReturn(DIRECTOR);
    }

    @Test
    void listDelegatesWithTheDirectorIdentityFromAuthentication() {
        when(useCase.list(DIRECTOR, 1L, 2L, 3L, 4L))
                .thenReturn(Flux.just(constraint(true)));

        StepVerifier.create(controller.list(authentication, 1L, 2L, 3L, 4L))
                .assertNext(response -> assertEquals(6L, response.protectedSingerId()))
                .verifyComplete();

        verify(useCase).list(DIRECTOR, 1L, 2L, 3L, 4L);
    }

    @Test
    void listPropagatesFormationNotFoundSoTheHandlerAnswersNotFound() {
        when(useCase.list(DIRECTOR, 1L, 2L, 3L, 4L))
                .thenReturn(Flux.error(new FormationNotFoundException()));

        StepVerifier.create(controller.list(authentication, 1L, 2L, 3L, 4L))
                .expectError(FormationNotFoundException.class)
                .verify();
    }

    @Test
    void createReturnsCreatedAndDelegatesTheProtectedSinger() {
        when(useCase.create(DIRECTOR, 1L, 2L, 3L, 4L, 6L))
                .thenReturn(Mono.just(constraint(true)));

        StepVerifier.create(controller.create(
                        authentication, 1L, 2L, 3L, 4L,
                        new FormationVisibilityConstraintController.CreateConstraintRequest(6L)))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    assertEquals(6L, response.getBody().protectedSingerId());
                })
                .verifyComplete();
    }

    @Test
    void createMapsRejectedSingerToBadRequest() {
        when(useCase.create(DIRECTOR, 1L, 2L, 3L, 4L, 99L))
                .thenReturn(Mono.error(new IllegalArgumentException("coreuta no asignado")));

        StepVerifier.create(controller.create(
                        authentication, 1L, 2L, 3L, 4L,
                        new FormationVisibilityConstraintController.CreateConstraintRequest(99L)))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertEquals(HttpStatus.BAD_REQUEST, status.getStatusCode());
                })
                .verify();
    }

    @Test
    void updateReturnsNotFoundWhenTheConstraintDoesNotExistInTheContext() {
        when(useCase.update(DIRECTOR, 1L, 2L, 3L, 4L, 5L, false))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.update(
                        authentication, 1L, 2L, 3L, 4L, 5L,
                        new FormationVisibilityConstraintController.UpdateConstraintRequest(false)))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertEquals(HttpStatus.NOT_FOUND, status.getStatusCode());
                })
                .verify();
    }

    @Test
    void deleteReturnsNoContentWhenTheConstraintIsRemoved() {
        when(useCase.delete(DIRECTOR, 1L, 2L, 3L, 4L, 5L)).thenReturn(Mono.just(true));

        StepVerifier.create(controller.delete(authentication, 1L, 2L, 3L, 4L, 5L))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT,
                        response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void deleteReturnsNotFoundWhenTheConstraintIsMissing() {
        when(useCase.delete(DIRECTOR, 1L, 2L, 3L, 4L, 5L)).thenReturn(Mono.just(false));

        StepVerifier.create(controller.delete(authentication, 1L, 2L, 3L, 4L, 5L))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertEquals(HttpStatus.NOT_FOUND, status.getStatusCode());
                })
                .verify();
    }

    private static FormationVisibilityConstraint constraint(boolean active) {
        return new FormationVisibilityConstraint(
                5L, 4L, 6L, active, LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
