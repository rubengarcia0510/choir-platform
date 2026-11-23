package com.rubengarcia.choir.formation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationVersionNotFoundException;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityRosterPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluation;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluationStatus;
import com.rubengarcia.choir.formation.domain.VisibilityFinding;
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
    private static final long OTHER = 30L;

    @Mock
    private FormationVisibilityConstraintPort constraintPort;

    @Mock
    private FormationCatalogPort catalog;

    @Mock
    private FormationVisibilityRosterPort rosterPort;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    private FormationVisibilityEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new FormationVisibilityEvaluationService(
                constraintPort,
                catalog,
                rosterPort,
                new FormationOccurrenceContextAuthorizer(occurrenceAuthorization, catalog));
    }

    @Test
    void unauthorizedDirectorDoesNotReadConstraintsRosterOrDraft() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new IllegalStateException("denegado")));

        assertThatThrownBy(() -> evaluate())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(constraintPort, rosterPort, catalog);
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

        verifyNoInteractions(constraintPort, rosterPort);
    }

    @Test
    void emptyActiveRosterMakesEveryPositionNotEvaluable() {
        authorize();
        stubConstraints(constraint(1L));
        stubDraft(position(100L, PROTECTED, "5", "20", 160));
        stubRoster();

        VisibilityEvaluation result = evaluate();

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(reasons(result))
                .contains(VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER);
    }

    @Test
    void activeAssignedSingerWithoutDraftPositionIsNotEvaluable() {
        authorize();
        stubConstraints(constraint(1L));
        stubDraft(position(100L, PROTECTED, "5", "20", 160));
        stubRoster(PROTECTED, OBSTRUCTOR);

        VisibilityEvaluation result = evaluate();

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(reasons(result))
                .containsExactly(VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_DRAFT_POSITION);
    }

    @Test
    void rosterWithAssignedSingersEvaluatesDraftPositions() {
        authorize();
        stubConstraints(constraint(1L));
        stubDraft(
                position(100L, PROTECTED, "5", "20", 160),
                position(200L, OBSTRUCTOR, "5", "40", 180));
        stubRoster(PROTECTED, OBSTRUCTOR);

        VisibilityEvaluation result = evaluate();

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.obstructingSingerId()).isEqualTo(OBSTRUCTOR));
    }

    @Test
    void inactiveSingerOutsideTheRosterCannotObstructEvenWithADraftPosition() {
        authorize();
        stubConstraints(constraint(1L));
        stubDraft(
                position(100L, PROTECTED, "5", "20", 160),
                position(300L, OTHER, "5", "40", 200));
        stubRoster(PROTECTED);

        VisibilityEvaluation result = evaluate();

        assertThat(result.findings()).isEmpty();
        assertThat(reasons(result))
                .contains(VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER);
    }

    @Test
    void missingDraftPropagatesFormationVersionNotFound() {
        authorize();
        stubConstraints(constraint(1L));
        when(catalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.empty());
        stubRoster(PROTECTED);

        assertThatThrownBy(() -> evaluate())
                .isInstanceOf(FormationVersionNotFoundException.class);
    }

    @Test
    void validFindingsAreKeptWhenAnotherComparisonIsNotEvaluable() {
        authorize();
        stubConstraints(constraint(1L));
        stubDraft(
                position(100L, PROTECTED, "5", "20", 160),
                position(200L, OBSTRUCTOR, "5", "40", 180));
        stubRoster(PROTECTED, OBSTRUCTOR, OTHER);

        VisibilityEvaluation result = evaluate();

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.findings()).hasSize(1);
        assertThat(reasons(result))
                .containsExactly(VisibilityNotEvaluableReason.MISSING_OBSTRUCTOR_DRAFT_POSITION);
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

    private void stubConstraints(FormationVisibilityConstraint constraint) {
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(constraint));
    }

    private void stubDraft(FormationPosition... positions) {
        when(catalog.findDraftForOccurrence(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Mono.just(new FormationCatalogPort.DraftDetails(
                        mock(Formation.class), mock(FormationVersion.class), List.of(positions))));
    }

    private void stubRoster(Long... singerIds) {
        when(rosterPort.findActiveAssignedSingerIds(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.fromArray(singerIds));
    }

    private VisibilityEvaluation evaluate() {
        return service.evaluate(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID)
                .block();
    }

    private static List<VisibilityNotEvaluableReason> reasons(VisibilityEvaluation result) {
        return result.notEvaluable().stream()
                .map(VisibilityNotEvaluable::reason)
                .toList();
    }

    private static FormationVisibilityConstraint constraint(long id) {
        return new FormationVisibilityConstraint(
                id, FORMATION_ID, PROTECTED, true, LocalDateTime.of(2026, 11, 22, 10, 0));
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
