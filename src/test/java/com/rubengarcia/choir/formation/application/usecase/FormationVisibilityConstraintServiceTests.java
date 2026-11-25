package com.rubengarcia.choir.formation.application.usecase;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FormationVisibilityConstraintServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;
    private static final long FORMATION_ID = 4L;
    private static final long CONSTRAINT_ID = 5L;
    private static final long PROTECTED_SINGER_ID = 6L;

    @Mock
    private FormationVisibilityConstraintPort constraintPort;

    @Mock
    private FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    @InjectMocks
    private FormationVisibilityConstraintService service;

    @Test
    void listAuthorizesAndDelegatesWithFullContext() {
        FormationVisibilityConstraint constraint = constraint(true);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(constraintPort.findForFormation(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .thenReturn(Flux.just(constraint));

        StepVerifier.create(service.list(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID))
                .expectNext(constraint)
                .verifyComplete();
    }

    @Test
    void createAuthorizesBeforeDelegating() {
        FormationVisibilityConstraint created = constraint(true);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(constraintPort.create(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID, PROTECTED_SINGER_ID))
                .thenReturn(Mono.just(created));

        StepVerifier.create(service.create(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, PROTECTED_SINGER_ID))
                .expectNext(created)
                .verifyComplete();

        InOrder order = inOrder(occurrenceAuthorization, constraintPort);
        order.verify(occurrenceAuthorization).requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(constraintPort).create(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID, PROTECTED_SINGER_ID);
    }

    @Test
    void unauthorizedDirectorNeverReachesThePort() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        StepVerifier.create(service.create(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, PROTECTED_SINGER_ID))
                .expectErrorSatisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    org.junit.jupiter.api.Assertions.assertEquals(
                            HttpStatus.NOT_FOUND, status.getStatusCode());
                })
                .verify();

        verifyNoInteractions(constraintPort);
    }

    @Test
    void updatePassesEveryContextIdentifierAndTheActiveFlag() {
        FormationVisibilityConstraint updated = constraint(false);
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(constraintPort.update(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID, CONSTRAINT_ID, false))
                .thenReturn(Mono.just(updated));

        StepVerifier.create(service.update(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, CONSTRAINT_ID, false))
                .expectNext(updated)
                .verifyComplete();

        verify(constraintPort).update(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID, CONSTRAINT_ID, false);
    }

    @Test
    void deleteReturnsThePortResultWhenTheConstraintIsMissing() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(constraintPort.delete(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID, CONSTRAINT_ID))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.delete(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, CONSTRAINT_ID))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void portValidationErrorsArePropagatedUnchanged() {
        when(occurrenceAuthorization.requireOwnedOccurrence(
                EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(constraintPort.create(
                CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, FORMATION_ID, PROTECTED_SINGER_ID))
                .thenReturn(Mono.error(new IllegalArgumentException("coreuta no asignado")));

        StepVerifier.create(service.create(
                        EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID,
                        FORMATION_ID, PROTECTED_SINGER_ID))
                .expectErrorMessage("coreuta no asignado")
                .verify();
    }

    private static FormationVisibilityConstraint constraint(boolean active) {
        return new FormationVisibilityConstraint(
                CONSTRAINT_ID, FORMATION_ID, PROTECTED_SINGER_ID, active,
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
