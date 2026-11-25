package com.rubengarcia.choir.formation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.exception.PhysicalConstraintConflictException;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationMobilityConstraintPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import io.r2dbc.spi.R2dbcException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FormationMobilityConstraintServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;
    private static final long FORMATION_ID = 4L;
    private static final long OTHER_FORMATION_ID = 99L;
    private static final long SINGER_ID = 10L;
    private static final long CONSTRAINT_ID = 5L;

    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1.250"),
            new BigDecimal("8.750"),
            new BigDecimal("0.500"),
            new BigDecimal("12.000"));

    @Mock
    private FormationMobilityConstraintPort constraintPort;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    @Mock
    private FormationCatalogPort catalog;

    private FormationMobilityConstraintService service;

    @BeforeEach
    void setUp() {
        service = new FormationMobilityConstraintService(
                constraintPort,
                new FormationOccurrenceContextAuthorizer(occurrenceAuthorization, catalog));
    }

    @Test
    void listReturnsOnlyActiveConstraintsAfterAuthorizingTheFormation() {
        Formation formation = mock(Formation.class);
        when(formation.getId()).thenReturn(FORMATION_ID);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(formation));
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(
                        constraint(CONSTRAINT_ID, true),
                        constraint(6L, false)));

        var listed = service.list(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID)
                .collectList()
                .block();

        assertThat(listed).extracting(FormationMobilityConstraint::id)
                .containsExactly(CONSTRAINT_ID);
    }

    @Test
    void rejectsFormationThatDoesNotBelongToTheOccurrence() {
        Formation other = mock(Formation.class);
        when(other.getId()).thenReturn(OTHER_FORMATION_ID);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(other));

        assertThatThrownBy(() -> service.create(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, SINGER_ID, REGION)
                .block())
                .isInstanceOf(FormationNotFoundException.class);

        verifyNoInteractions(constraintPort);
    }

    @Test
    void unauthorizedDirectorDoesNotReachCatalogOrPersistence() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        assertThatThrownBy(() -> service.update(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, CONSTRAINT_ID, REGION, true)
                .block())
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(catalog, constraintPort);
    }

    @Test
    void createTranslatesUniqueViolationIntoConflictWithoutDatabaseDetails() {
        authorizeFormation();
        when(constraintPort.create(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                FORMATION_ID, SINGER_ID, REGION))
                .thenReturn(Mono.error(uniqueViolation()));

        assertThatThrownBy(() -> service.create(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, SINGER_ID, REGION)
                .block())
                .isInstanceOf(PhysicalConstraintConflictException.class)
                .hasMessageNotContaining("23505")
                .hasMessageNotContaining("SQL");
    }

    @Test
    void createKeepsNonUniqueIntegrityErrorsAsTheyAre() {
        authorizeFormation();
        DataIntegrityViolationException checkViolation =
                new DataIntegrityViolationException(
                        "check",
                        new R2dbcException("check", "23514", 0) {});
        when(constraintPort.create(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                FORMATION_ID, SINGER_ID, REGION))
                .thenReturn(Mono.error(checkViolation));

        assertThatThrownBy(() -> service.create(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, SINGER_ID, REGION)
                .block())
                .isSameAs(checkViolation);
    }

    @Test
    void updateReturnsEmptyWhenConstraintIsNotInTheAuthorizedContext() {
        authorizeFormation();
        when(constraintPort.update(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                FORMATION_ID, CONSTRAINT_ID, REGION, true))
                .thenReturn(Mono.empty());

        assertThat(service.update(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, CONSTRAINT_ID, REGION, true)
                .block()).isNull();
    }

    @Test
    void deleteDelegatesToPortAfterAuthorization() {
        authorizeFormation();
        when(constraintPort.delete(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                FORMATION_ID, CONSTRAINT_ID))
                .thenReturn(Mono.just(true));

        assertThat(service.delete(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, CONSTRAINT_ID)
                .block()).isTrue();
    }

    private void authorizeFormation() {
        Formation formation = mock(Formation.class);
        when(formation.getId()).thenReturn(FORMATION_ID);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(catalog.findForOccurrence(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Flux.just(formation));
    }

    private static DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException(
                "duplicate",
                new R2dbcException("duplicate", "23505", 0) {});
    }

    private static FormationMobilityConstraint constraint(long id, boolean active) {
        return new FormationMobilityConstraint(
                id,
                FORMATION_ID,
                SINGER_ID,
                REGION,
                active,
                LocalDateTime.of(2026, 11, 22, 10, 0));
    }
}
