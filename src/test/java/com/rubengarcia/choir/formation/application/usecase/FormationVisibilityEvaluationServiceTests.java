package com.rubengarcia.choir.formation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationPlanningPersistencePort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluation;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluationStatus;
import com.rubengarcia.choir.formation.domain.VisibilityNotEvaluable;
import com.rubengarcia.choir.formation.domain.VisibilityNotEvaluableReason;
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
class FormationVisibilityEvaluationServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;
    private static final long FORMATION_ID = 4L;
    private static final long PROTECTED = 10L;
    private static final long OBSTRUCTOR = 20L;

    @Mock
    private FormationVisibilityConstraintPort constraintPort;

    @Mock
    private FormationCatalogPort catalog;

    @Mock
    private FormationPlanningPersistencePort planning;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    private FormationVisibilityEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new FormationVisibilityEvaluationService(
                constraintPort,
                catalog,
                planning,
                new FormationOccurrenceContextAuthorizer(occurrenceAuthorization, catalog));
    }

    @Test
    void unauthorizedDirectorDoesNotReadConstraintsRosterOrDraft() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new IllegalStateException("denegado")));

        assertThatThrownBy(() -> evaluate())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(constraintPort, planning, catalog);
    }

    @Test
    void formationOutsideTheOccurrenceIsRejectedBeforeReadingAnything() {
        Formation other = mock(Formation.class);
        when(other.getId()).thenReturn(99L);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(other));

        assertThatThrownBy(() -> evaluate())
                .isInstanceOf(FormationNotFoundException.class);

        verifyNoInteractions(constraintPort, planning);
    }

    @Test
    void unpublishedConcertHasNoRosterSoEveryPositionIsNotEvaluable() {
        authorize();
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(constraint(1L)));
        when(catalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.just(draft(
                        position(100L, PROTECTED, "5", "20", 160),
                        position(200L, OBSTRUCTOR, "5", "40", 180))));
        when(planning.vocalAssignmentsForPlanning(CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.empty());

        VisibilityEvaluation result = evaluate();

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.notEvaluable())
                .extracting(VisibilityNotEvaluable::reason)
                .contains(VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER);
    }

    @Test
    void publishedRosterEvaluatesDraftPositionsAgainstActiveConstraints() {
        authorize();
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(constraint(1L)));
        when(catalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.just(draft(
                        position(100L, PROTECTED, "5", "20", 160),
                        position(200L, OBSTRUCTOR, "5", "40", 180))));
        when(planning.vocalAssignmentsForPlanning(CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(
                        assignment(PROTECTED), assignment(OBSTRUCTOR), assignment(null)));

        VisibilityEvaluation result = evaluate();

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.obstructingSingerId()).isEqualTo(OBSTRUCTOR));
    }

    // ------------------------------------------------------------------

    private void authorize() {
        Formation formation = mock(Formation.class);
        when(formation.getId()).thenReturn(FORMATION_ID);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(formation));
    }

    private VisibilityEvaluation evaluate() {
        return service.evaluate(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID)
                .block();
    }

    private static FormationCatalogPort.DraftDetails draft(FormationPosition... positions) {
        return new FormationCatalogPort.DraftDetails(
                mock(Formation.class), mock(FormationVersion.class), List.of(positions));
    }

    private static FormationVisibilityConstraint constraint(long id) {
        return new FormationVisibilityConstraint(
                id, FORMATION_ID, PROTECTED, true, LocalDateTime.of(2026, 11, 22, 10, 0));
    }

    private static VocalAssignment assignment(Long singerId) {
        return new VocalAssignment(CONCERT_ID, REPERTOIRE_ITEM_ID, singerId,
                "Cantante", "ALTO", 1L, "Sección", 1);
    }

    private static FormationPosition position(
            long id, long singerId, String x, String y, Integer height) {
        FormationPosition position = new FormationPosition();
        position.setId(id);
        position.setSingerId(singerId);
        position.setXCoordinate(new BigDecimal(x));
        position.setYCoordinate(new BigDecimal(y));
        position.setHeightCmSnapshot(height);
        return position;
    }
}
