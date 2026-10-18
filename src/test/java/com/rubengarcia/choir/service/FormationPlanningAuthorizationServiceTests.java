package com.rubengarcia.choir.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

class FormationPlanningAuthorizationServiceTests {

    private FormationPlanningService planningService;
    private ResourceAuthorizationService authorizationService;
    private FormationPlanningAuthorizationService service;

    @BeforeEach
    void setUp() {
        planningService = mock(FormationPlanningService.class);
        authorizationService = mock(ResourceAuthorizationService.class);
        service = new FormationPlanningAuthorizationServiceImpl(
                planningService, authorizationService);
    }

    @Test
    void authorizesFormationVersionBeforeReadingPositions() {
        when(authorizationService.requireOwnedFormationVersion(
                "director@example.test", 10L, 20L)).thenReturn(Mono.empty());
        when(planningService.positionsForFormationVersion(20L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.positionsForFormationVersion(
                        "director@example.test", 10L, 20L))
                .verifyComplete();

        verify(authorizationService)
                .requireOwnedFormationVersion("director@example.test", 10L, 20L);
        verify(planningService).positionsForFormationVersion(20L);
    }

    @Test
    void doesNotReadFormationPositionsWhenAuthorizationFails() {
        RuntimeException denied = new RuntimeException("not authorized");
        when(authorizationService.requireOwnedFormationVersion(
                "other@example.test", 10L, 20L)).thenReturn(Mono.error(denied));

        StepVerifier.create(service.positionsForFormationVersion(
                        "other@example.test", 10L, 20L))
                .expectErrorMatches(error -> error == denied)
                .verify();

        verify(planningService, never()).positionsForFormationVersion(20L);
    }

    @Test
    void authorizesConcertAndRepertoireItemBeforeReadingAssignments() {
        when(authorizationService.requireOwnedConcertWork(
                "director@example.test", 10L, 30L, 40L))
                .thenReturn(Mono.empty());
        when(planningService.vocalAssignmentsForPlanning(30L, 40L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.vocalAssignmentsForPlanning(
                        "director@example.test", 10L, 30L, 40L))
                .verifyComplete();

        verify(authorizationService).requireOwnedConcertWork(
                "director@example.test", 10L, 30L, 40L);
        verify(planningService).vocalAssignmentsForPlanning(30L, 40L);
    }

    @Test
    void authorizesTransitionVersionBeforeReadingPositions() {
        when(authorizationService.requireOwnedTransitionVersion(
                "director@example.test", 10L, 50L)).thenReturn(Mono.empty());
        when(planningService.positionsForTransitionVersion(50L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.positionsForTransitionVersion(
                        "director@example.test", 10L, 50L))
                .verifyComplete();

        verify(authorizationService).requireOwnedTransitionVersion(
                "director@example.test", 10L, 50L);
        verify(planningService).positionsForTransitionVersion(50L);
    }

    @Test
    void authorizesPositionBeforeUpdatingDraft() {
        when(authorizationService.requireOwnedPosition(
                "director@example.test", 10L, 60L)).thenReturn(Mono.empty());
        when(planningService.updateDraftPosition(
                60L, "A1", BigDecimal.ONE, BigDecimal.ZERO))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.updateDraftPosition(
                        "director@example.test", 10L, 60L, "A1",
                        BigDecimal.ONE, BigDecimal.ZERO))
                .verifyComplete();

        verify(authorizationService)
                .requireOwnedPosition("director@example.test", 10L, 60L);
        verify(planningService).updateDraftPosition(
                60L, "A1", BigDecimal.ONE, BigDecimal.ZERO);
    }

    @Test
    void doesNotUpdatePositionWhenAuthorizationFails() {
        RuntimeException denied = new RuntimeException("not authorized");
        when(authorizationService.requireOwnedPosition(
                "other@example.test", 10L, 60L)).thenReturn(Mono.error(denied));

        StepVerifier.create(service.updateDraftPosition(
                        "other@example.test", 10L, 60L, "A1",
                        BigDecimal.ONE, BigDecimal.ZERO))
                .expectErrorMatches(error -> error == denied)
                .verify();

        verify(planningService, never()).updateDraftPosition(
                60L, "A1", BigDecimal.ONE, BigDecimal.ZERO);
    }
}
