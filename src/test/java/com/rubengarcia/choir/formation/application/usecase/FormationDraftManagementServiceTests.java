package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class FormationDraftManagementServiceTests {

    @Mock
    private FormationCatalogPort formationCatalog;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    @InjectMocks
    private FormationDraftManagementService service;

    @Test
    void authorizesOccurrenceBeforeListingFormations() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                "director@example.test", 10L, 20L, 30L))
                .thenReturn(Mono.empty());
        when(formationCatalog.findForOccurrence(10L, 20L, 30L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.list(
                        "director@example.test", 10L, 20L, 30L))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, formationCatalog);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                "director@example.test", 10L, 20L, 30L);
        order.verify(formationCatalog).findForOccurrence(10L, 20L, 30L);
    }

    @Test
    void doesNotListWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                "other@example.test", 10L, 20L, 30L))
                .thenReturn(Mono.error(IllegalStateException::new));

        StepVerifier.create(service.list(
                        "other@example.test", 10L, 20L, 30L))
                .expectError(IllegalStateException.class)
                .verify();

        verify(formationCatalog, never()).findForOccurrence(10L, 20L, 30L);
    }

    @Test
    void authorizesOccurrenceBeforeCreatingFormationAndDraft() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                "director@example.test", 10L, 20L, 30L))
                .thenReturn(Mono.empty());
        when(formationCatalog.createWithInitialDraft(
                10L, 20L, 30L, "Ensayo", "Descripción"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.create(
                        "director@example.test",
                        10L,
                        20L,
                        30L,
                        "Ensayo",
                        "Descripción"))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, formationCatalog);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                "director@example.test", 10L, 20L, 30L);
        order.verify(formationCatalog).createWithInitialDraft(
                10L, 20L, 30L, "Ensayo", "Descripción");
    }

    @Test
    void doesNotCreateWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                "other@example.test", 10L, 20L, 30L))
                .thenReturn(Mono.error(IllegalStateException::new));

        StepVerifier.create(service.create(
                        "other@example.test",
                        10L,
                        20L,
                        30L,
                        "Ensayo",
                        "Descripción"))
                .expectError(IllegalStateException.class)
                .verify();

        verify(formationCatalog, never()).createWithInitialDraft(
                10L, 20L, 30L, "Ensayo", "Descripción");
    }
}
