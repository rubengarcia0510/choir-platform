package com.rubengarcia.choir.formation.application.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationProximityConstraintPort;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FormationProximityConstraintServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;
    private static final long FORMATION_ID = 4L;
    private static final long CONSTRAINT_ID = 5L;

    @Mock
    private FormationProximityConstraintPort constraintPort;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    @InjectMocks
    private FormationProximityConstraintService service;

    @Test
    void createAuthorizesAndNormalizesSingerIds() {
        BigDecimal maxDistance = new BigDecimal("5.000");

        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());

        when(constraintPort.create(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                10L,
                20L,
                maxDistance))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.create(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        20L,
                        10L,
                        maxDistance))
                .verifyComplete();

        verify(occurrenceAuthorization).requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);

        verify(constraintPort).create(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                10L,
                20L,
                maxDistance);
    }

    @Test
    void listAuthorizesBeforeReadingConstraints() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());

        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.list(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID))
                .verifyComplete();

        verify(occurrenceAuthorization).requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);

        verify(constraintPort).findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID);
    }

    @Test
    void updateAuthorizesAndDelegatesAllParameters() {
        BigDecimal maxDistance = new BigDecimal("8.500");

        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());

        when(constraintPort.update(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                CONSTRAINT_ID,
                maxDistance,
                false))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.update(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        CONSTRAINT_ID,
                        maxDistance,
                        false))
                .verifyComplete();

        verify(constraintPort).update(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                CONSTRAINT_ID,
                maxDistance,
                false);
    }

    @Test
    void deleteAuthorizesAndReturnsPortResult() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());

        when(constraintPort.delete(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                CONSTRAINT_ID))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.delete(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        CONSTRAINT_ID))
                .expectNext(true)
                .verifyComplete();

        verify(constraintPort).delete(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                CONSTRAINT_ID);
    }

    @Test
    void createDoesNotAccessPortWhenAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(
                        () -> new IllegalStateException("No autorizado")));

        StepVerifier.create(service.create(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        10L,
                        20L,
                        new BigDecimal("5.000")))
                .expectErrorMessage("No autorizado")
                .verify();

        verifyNoInteractions(constraintPort);
    }

    @Test
    void createRejectsInvalidSingerPairsBeforeAuthorization() {
        assertThrows(IllegalArgumentException.class, () ->
                service.create(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        10L,
                        10L,
                        new BigDecimal("5.000")));

        assertThrows(IllegalArgumentException.class, () ->
                service.create(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        0L,
                        20L,
                        new BigDecimal("5.000")));

        verifyNoInteractions(occurrenceAuthorization, constraintPort);
    }

    @Test
    void createAndUpdateRejectInvalidMaximumDistance() {
        assertThrows(IllegalArgumentException.class, () ->
                service.create(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        10L,
                        20L,
                        BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class, () ->
                service.create(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        10L,
                        20L,
                        null));

        assertThrows(IllegalArgumentException.class, () ->
                service.update(
                        EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        CONSTRAINT_ID,
                        new BigDecimal("-1"),
                        true));

        verifyNoInteractions(occurrenceAuthorization, constraintPort);
    }
}
