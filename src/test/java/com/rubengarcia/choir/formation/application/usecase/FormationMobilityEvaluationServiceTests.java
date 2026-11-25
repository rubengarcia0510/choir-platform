package com.rubengarcia.choir.formation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationVersionNotFoundException;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationMobilityConstraintPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import com.rubengarcia.choir.formation.domain.MobilityBound;
import com.rubengarcia.choir.formation.domain.MobilityEvaluation;
import com.rubengarcia.choir.formation.domain.MobilityEvaluationStatus;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FormationMobilityEvaluationServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;
    private static final long FORMATION_ID = 4L;
    private static final long SINGER_ID = 10L;

    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1.000"), new BigDecimal("8.000"),
            new BigDecimal("0.000"), new BigDecimal("10.000"));

    @Mock
    private FormationMobilityConstraintPort constraintPort;

    @Mock
    private FormationCatalogPort catalog;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    private FormationMobilityEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new FormationMobilityEvaluationService(
                constraintPort,
                catalog,
                new FormationOccurrenceContextAuthorizer(occurrenceAuthorization, catalog));
    }

    @Test
    void unauthorizedDirectorDoesNotReadConstraintsOrDraft() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new IllegalStateException("denegado")));

        assertThatThrownBy(() -> service.evaluate(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID)
                .block())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(constraintPort, catalog);
    }

    @Test
    void formationOutsideTheOccurrenceIsRejectedBeforeReadingConstraints() {
        Formation other = mock(Formation.class);
        when(other.getId()).thenReturn(99L);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(other));

        assertThatThrownBy(() -> service.evaluate(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID)
                .block())
                .isInstanceOf(FormationNotFoundException.class);

        verifyNoInteractions(constraintPort);
    }

    @Test
    void missingDraftPropagatesFormationVersionNotFoundException() {
        authorize();
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(constraint(1L, true)));
        when(catalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.empty());

        assertThatThrownBy(() -> evaluate())
                .isInstanceOf(FormationVersionNotFoundException.class);
    }

    @Test
    void evaluatesDraftPositionsOfThisFormationAgainstActiveConstraints() {
        authorize();
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(constraint(1L, true)));
        when(catalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.just(new FormationCatalogPort.DraftDetails(
                        mock(Formation.class),
                        mock(FormationVersion.class),
                        List.of(position(100L, SINGER_ID, "9.000", "5.000")))));

        MobilityEvaluation evaluation = evaluate();

        assertThat(evaluation.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(evaluation.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.positionId()).isEqualTo(100L);
            assertThat(finding.violatedBounds()).containsExactly(MobilityBound.X_ABOVE_MAX);
        });
    }

    private void authorize() {
        Formation formation = mock(Formation.class);
        when(formation.getId()).thenReturn(FORMATION_ID);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(formation));
    }

    private MobilityEvaluation evaluate() {
        return service.evaluate(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID)
                .block();
    }

    private static FormationMobilityConstraint constraint(long id, boolean active) {
        return new FormationMobilityConstraint(
                id, FORMATION_ID, SINGER_ID, REGION, active,
                LocalDateTime.of(2026, 11, 22, 10, 0));
    }

    private static FormationPosition position(long id, long singerId, String x, String y) {
        FormationPosition position = new FormationPosition();
        position.setId(id);
        position.setSingerId(singerId);
        position.setXCoordinate(new BigDecimal(x));
        position.setYCoordinate(new BigDecimal(y));
        return position;
    }
}
