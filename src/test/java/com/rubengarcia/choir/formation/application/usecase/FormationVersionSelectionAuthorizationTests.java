package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationPlanningResourceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVersionSelectionPersistencePort;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import com.rubengarcia.choir.formation.domain.FormationVersionSelectionHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormationVersionSelectionAuthorizationTests {

    private static final String EMAIL = "director@example.test";
    private static final long CHOIR_ID = 10L;
    private static final long CONCERT_ID = 20L;
    private static final long REPERTOIRE_ITEM_ID = 30L;
    private static final long FORMATION_ID = 40L;
    private static final long VERSION_ID = 50L;

    @Mock
    private FormationVersionSelectionPersistencePort persistencePort;

    @Mock
    private FormationPlanningResourceAuthorizationPort authorizationPort;

    @InjectMocks
    private FormationVersionSelectionUseCaseImpl useCase;

    @Test
    void authorizesConcertWorkBeforeReadingCurrentSelection() {
        FormationVersionSelection selection = new FormationVersionSelection(
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                LocalDateTime.now());

        when(authorizationPort.requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(persistencePort.findSelection(CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.just(selection));

        StepVerifier.create(useCase.findSelection(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectNext(selection)
                .verifyComplete();

        InOrder order = inOrder(authorizationPort, persistencePort);
        order.verify(authorizationPort).requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(persistencePort).findSelection(
                CONCERT_ID, REPERTOIRE_ITEM_ID);
    }

    @Test
    void doesNotReadCurrentSelectionWhenAuthorizationFails() {
        RuntimeException denied = new RuntimeException("not authorized");

        when(authorizationPort.requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(denied));

        StepVerifier.create(useCase.findSelection(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectErrorMatches(error -> error == denied)
                .verify();

        verifyNoInteractions(persistencePort);
    }

    @Test
    void authorizesConcertWorkBeforeChangingSelection() {
        FormationVersionSelection selection = new FormationVersionSelection(
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                LocalDateTime.now());

        when(authorizationPort.requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(persistencePort.selectPublishedVersion(
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                EMAIL))
                .thenReturn(Mono.just(selection));

        StepVerifier.create(useCase.selectPublishedVersion(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        VERSION_ID))
                .expectNext(selection)
                .verifyComplete();

        InOrder order = inOrder(authorizationPort, persistencePort);
        order.verify(authorizationPort).requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(persistencePort).selectPublishedVersion(
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                EMAIL);
    }

    @Test
    void doesNotChangeSelectionWhenAuthorizationFails() {
        RuntimeException denied = new RuntimeException("not authorized");

        when(authorizationPort.requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(denied));

        StepVerifier.create(useCase.selectPublishedVersion(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        VERSION_ID))
                .expectErrorMatches(error -> error == denied)
                .verify();

        verifyNoInteractions(persistencePort);
    }

    @Test
    void authorizesConcertWorkBeforeReadingSelectionHistory() {
        FormationVersionSelectionHistory history =
                new FormationVersionSelectionHistory(
                        1L,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        null,
                        null,
                        FORMATION_ID,
                        VERSION_ID,
                        EMAIL,
                        LocalDateTime.now());

        when(authorizationPort.requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(persistencePort.findSelectionHistory(
                CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(history));

        StepVerifier.create(useCase.findSelectionHistory(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectNext(history)
                .verifyComplete();

        InOrder order = inOrder(authorizationPort, persistencePort);
        order.verify(authorizationPort).requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(persistencePort).findSelectionHistory(
                CONCERT_ID, REPERTOIRE_ITEM_ID);
    }

    @Test
    void doesNotReadSelectionHistoryWhenAuthorizationFails() {
        RuntimeException denied = new RuntimeException("not authorized");

        when(authorizationPort.requireOwnedConcertWork(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(denied));

        StepVerifier.create(useCase.findSelectionHistory(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectErrorMatches(error -> error == denied)
                .verify();

        verifyNoInteractions(persistencePort);
    }
}
