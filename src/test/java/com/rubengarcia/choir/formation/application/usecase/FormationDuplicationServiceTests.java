package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormationDuplicationServiceTests {

    @Mock
    private FormationDuplicationPort duplicationPort;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    @InjectMocks
    private FormationDuplicationService service;

    @Test
    void authorizesOccurrenceBeforeDuplicatingFormation() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                "director@example.com", 1L, 2L, 3L))
                .thenReturn(Mono.empty());

        when(duplicationPort.duplicate(
                1L, 2L, 3L, 4L, null, "Copia", "Descripción"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.duplicate(
                        "director@example.com",
                        1L,
                        2L,
                        3L,
                        4L,
                        null,
                        "Copia",
                        "Descripción"))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, duplicationPort);
        order.verify(occurrenceAuthorization)
                .requireOwnedOccurrence("director@example.com", 1L, 2L, 3L);
        order.verify(duplicationPort)
                .duplicate(1L, 2L, 3L, 4L, null, "Copia", "Descripción");
    }

    @Test
    void doesNotDuplicateWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                "director@example.com", 1L, 2L, 3L))
                .thenReturn(Mono.error(
                        IllegalStateException::new));

        StepVerifier.create(service.duplicate(
                        "director@example.com",
                        1L,
                        2L,
                        3L,
                        4L,
                        null,
                        "Copia",
                        "Descripción"))
                .expectError(IllegalStateException.class)
                .verify();

        verify(duplicationPort, never()).duplicate(
                1L, 2L, 3L, 4L, null, "Copia", "Descripción");
    }
}
