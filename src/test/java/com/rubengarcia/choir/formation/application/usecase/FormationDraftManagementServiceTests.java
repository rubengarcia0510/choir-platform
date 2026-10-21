package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class FormationDraftManagementServiceTests {

    private static final String DIRECTOR_EMAIL = "director@example.test";
    private static final String OTHER_EMAIL = "other@example.test";

    private static final long CHOIR_ID = 10L;
    private static final long CONCERT_ID = 20L;
    private static final long REPERTOIRE_ITEM_ID = 30L;
    private static final long FORMATION_ID = 40L;
    private static final long VERSION_ID = 50L;
    private static final long SINGER_ID = 60L;

    @Mock
    private FormationCatalogPort formationCatalog;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    @InjectMocks
    private FormationDraftManagementService service;

    @Test
    void authorizesOccurrenceBeforeListingFormations() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(formationCatalog.findForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.list(
                        DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, formationCatalog);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(formationCatalog).findForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
    }

    @Test
    void doesNotListWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                OTHER_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(IllegalStateException::new));

        StepVerifier.create(service.list(
                        OTHER_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectError(IllegalStateException.class)
                .verify();

        verify(formationCatalog, never()).findForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
    }

    @Test
    void authorizesOccurrenceBeforeCreatingFormationAndDraft() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(formationCatalog.createWithInitialDraft(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                "Ensayo", "Descripción"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.create(
                        DIRECTOR_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        "Ensayo",
                        "Descripción"))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, formationCatalog);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(formationCatalog).createWithInitialDraft(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                "Ensayo", "Descripción");
    }

    @Test
    void doesNotCreateWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                OTHER_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(IllegalStateException::new));

        StepVerifier.create(service.create(
                        OTHER_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        "Ensayo",
                        "Descripción"))
                .expectError(IllegalStateException.class)
                .verify();

        verify(formationCatalog, never()).createWithInitialDraft(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                "Ensayo", "Descripción");
    }

    @Test
    void authorizesOccurrenceBeforeReadingDraft() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(formationCatalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.getDraft(
                        DIRECTOR_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, formationCatalog);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(formationCatalog).findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID);
    }

    @Test
    void doesNotReadDraftWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                OTHER_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(IllegalStateException::new));

        StepVerifier.create(service.getDraft(
                        OTHER_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID))
                .expectError(IllegalStateException.class)
                .verify();

        verify(formationCatalog, never()).findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID);
    }

    @Test
    void authorizesOccurrenceBeforeReplacingDraftPositions() {
        List<FormationCatalogPort.PositionUpdate> positions =
                List.of(validPosition(SINGER_ID));

        when(occurrenceAuthorization.requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(formationCatalog.replaceDraftPositions(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                positions))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.replaceDraftPositions(
                        DIRECTOR_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        VERSION_ID,
                        positions))
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, formationCatalog);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(formationCatalog).replaceDraftPositions(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                positions);
    }

    @Test
    void doesNotReplacePositionsWhenOccurrenceAuthorizationFails() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                OTHER_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(IllegalStateException::new));

        StepVerifier.create(service.replaceDraftPositions(
                        OTHER_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        VERSION_ID,
                        List.of(validPosition(SINGER_ID))))
                .expectError(IllegalStateException.class)
                .verify();

        verify(formationCatalog, never()).replaceDraftPositions(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                List.of(validPosition(SINGER_ID)));
    }

    @Test
    void rejectsNullPositionsListBeforeCallingDependencies() {
        assertInvalidPositions(null);
    }

    @Test
    void rejectsNullPositionEntryBeforeCallingDependencies() {
        assertInvalidPositions(Collections.singletonList(null));
    }

    @Test
    void rejectsNullSingerIdBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        null, "Soprano", BigDecimal.TEN, BigDecimal.TEN)));
    }

    @Test
    void rejectsNonPositiveSingerIdBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        0L, "Soprano", BigDecimal.TEN, BigDecimal.TEN)));
    }

    @Test
    void rejectsDuplicateSingerIdsBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                validPosition(SINGER_ID),
                validPosition(SINGER_ID)));
    }

    @Test
    void rejectsNullXCoordinateBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", null, BigDecimal.TEN)));
    }

    @Test
    void rejectsXCoordinateBelowZeroBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", BigDecimal.valueOf(-1), BigDecimal.TEN)));
    }

    @Test
    void rejectsXCoordinateAboveOneHundredBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", BigDecimal.valueOf(101), BigDecimal.TEN)));
    }

    @Test
    void rejectsNullYCoordinateBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", BigDecimal.TEN, null)));
    }

    @Test
    void rejectsYCoordinateBelowZeroBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", BigDecimal.TEN, BigDecimal.valueOf(-1))));
    }

    @Test
    void rejectsYCoordinateAboveOneHundredBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", BigDecimal.TEN, BigDecimal.valueOf(101))));
    }

    @Test
    void rejectsLabelLongerThanEightyCharactersBeforeCallingDependencies() {
        assertInvalidPositions(List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID,
                        "X".repeat(81),
                        BigDecimal.TEN,
                        BigDecimal.TEN)));
    }

    @Test
    void acceptsCoordinatesAtBothBoundaries() {
        List<FormationCatalogPort.PositionUpdate> positions = List.of(
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID, "Soprano", BigDecimal.ZERO, BigDecimal.ZERO),
                new FormationCatalogPort.PositionUpdate(
                        SINGER_ID + 1, "Tenor",
                        BigDecimal.valueOf(100), BigDecimal.valueOf(100)));

        when(occurrenceAuthorization.requireOwnedOccurrence(
                DIRECTOR_EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(formationCatalog.replaceDraftPositions(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                positions))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.replaceDraftPositions(
                        DIRECTOR_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        VERSION_ID,
                        positions))
                .verifyComplete();

        verify(formationCatalog).replaceDraftPositions(
                CHOIR_ID,
                CONCERT_ID,
                REPERTOIRE_ITEM_ID,
                FORMATION_ID,
                VERSION_ID,
                positions);
    }

    private void assertInvalidPositions(
            List<FormationCatalogPort.PositionUpdate> positions) {

        StepVerifier.create(service.replaceDraftPositions(
                        DIRECTOR_EMAIL,
                        CHOIR_ID,
                        CONCERT_ID,
                        REPERTOIRE_ITEM_ID,
                        FORMATION_ID,
                        VERSION_ID,
                        positions))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(formationCatalog, occurrenceAuthorization);
    }

    private FormationCatalogPort.PositionUpdate validPosition(long singerId) {
        return new FormationCatalogPort.PositionUpdate(
                singerId,
                "Soprano",
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(75));
    }
}
