package com.rubengarcia.choir.formation.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.port.in.FormationProximityConstraintUseCase;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FormationProximityConstraintControllerMappingTests {

    private FormationProximityConstraintUseCase useCase;
    private Authentication authentication;
    private FormationProximityConstraintController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(FormationProximityConstraintUseCase.class);
        authentication = mock(Authentication.class);
        controller = new FormationProximityConstraintController(useCase);

        when(authentication.getName()).thenReturn("director");
    }

    @Test
    void createMapsSynchronousIllegalArgumentExceptionToBadRequest() {
        when(useCase.create(
                "director",
                1L,
                1L,
                1L,
                1L,
                10L,
                20L,
                new BigDecimal("1.5")))
                .thenThrow(new IllegalArgumentException("Distancia inválida"));

        StepVerifier.create(controller.create(
                        authentication,
                        1L,
                        1L,
                        1L,
                        1L,
                        new FormationProximityConstraintController.CreateConstraintRequest(
                                10L, 20L, new BigDecimal("1.5"))))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException exception =
                            assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                })
                .verify();
    }

    @Test
    void updateMapsSynchronousIllegalArgumentExceptionToBadRequest() {
        when(useCase.update(
                "director",
                1L,
                1L,
                1L,
                1L,
                5L,
                new BigDecimal("1.5"),
                true))
                .thenThrow(new IllegalArgumentException("Distancia inválida"));

        StepVerifier.create(controller.update(
                        authentication,
                        1L,
                        1L,
                        1L,
                        1L,
                        5L,
                        new FormationProximityConstraintController.UpdateConstraintRequest(
                                new BigDecimal("1.5"), true)))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException exception =
                            assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                })
                .verify();
    }

    @Test
    void updateMapsEmptyResultToNotFound() {
        when(useCase.update(
                "director",
                1L,
                1L,
                1L,
                1L,
                5L,
                new BigDecimal("1.5"),
                true))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.update(
                        authentication,
                        1L,
                        1L,
                        1L,
                        1L,
                        5L,
                        new FormationProximityConstraintController.UpdateConstraintRequest(
                                new BigDecimal("1.5"), true)))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException exception =
                            assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
                })
                .verify();
    }
}
