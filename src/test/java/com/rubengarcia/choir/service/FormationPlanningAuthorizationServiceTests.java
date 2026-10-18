package com.rubengarcia.choir.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormationPlanningAuthorizationServiceTests {

    @Mock
    private FormationPlanningService planningService;

    @Mock
    private ResourceAuthorizationService authorizationService;

    @InjectMocks
    private FormationPlanningAuthorizationServiceImpl service;

    @Test
    void authorizesFormationVersionBeforeReadingPositions() {
        when(authorizationService.requireOwnedFormationVersion(
                "director@example.test", 10L, 20L)).thenReturn(Mono.empty());
        when(planningService.positionsForFormationVersion(20L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.positionsForFormationVersion(
                        "director@example.test", 10L, 20L))
                .verifyComplete();

        InOrder order = inOrder(authorizationService, planningService);
        order.verify(authorizationService)
                .requireOwnedFormationVersion("director@example.test", 10L, 20L);
        order.verify(planningService).positionsForFormationVersion(20L);
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
    void authorizesConcertWorkBeforeReadingAssignments() {
        when(authorizationService.requireOwnedConcertWork(
                "director@example.test", 10L, 30L, 40L))
                .thenReturn(Mono.empty());
        when(planningService.vocalAssignmentsForPlanning(30L, 40L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.vocalAssignmentsForPlanning(
                        "director@example.test", 10L, 30L, 40L))
                .verifyComplete();

        InOrder order = inOrder(authorizationService, planningService);
        order.verify(authorizationService).requireOwnedConcertWork(
                "director@example.test", 10L, 30L, 40L);
        order.verify(planningService).vocalAssignmentsForPlanning(30L, 40L);
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

        InOrder order = inOrder(authorizationService, planningService);
        order.verify(authorizationService)
                .requireOwnedTransitionVersion("director@example.test", 10L, 50L);
        order.verify(planningService).positionsForTransitionVersion(50L);
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

        InOrder order = inOrder(authorizationService, planningService);
        order.verify(authorizationService)
                .requireOwnedPosition("director@example.test", 10L, 60L);
        order.verify(planningService).updateDraftPosition(
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
