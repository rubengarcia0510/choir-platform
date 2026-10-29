package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionLifecycleAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVersionLifecyclePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormationVersionLifecycleUseCaseTests {

    private static final String EMAIL = "director@example.com";
    private static final long CHOIR_ID = 10L;
    private static final long FORMATION_ID = 20L;
    private static final long VERSION_ID = 30L;

    @Mock
    private FormationVersionLifecyclePort lifecyclePort;

    @Mock
    private FormationVersionLifecycleAuthorizationPort authorizationPort;

    @InjectMocks
    private FormationVersionLifecycleUseCaseImpl useCase;

    @Test
    void authorizesBeforeCreatingDraft() {
        when(authorizationPort.requireOwnedChoir(EMAIL, CHOIR_ID))
                .thenReturn(Mono.empty());
        when(lifecyclePort.createDraft(
                CHOIR_ID, FORMATION_ID, "Nueva revisión"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.createDraft(
                        EMAIL, CHOIR_ID, FORMATION_ID, "Nueva revisión"))
                .verifyComplete();

        InOrder order = inOrder(authorizationPort, lifecyclePort);
        order.verify(authorizationPort).requireOwnedChoir(EMAIL, CHOIR_ID);
        order.verify(lifecyclePort).createDraft(
                CHOIR_ID, FORMATION_ID, "Nueva revisión");
    }

    @Test
    void doesNotCreateDraftWhenAuthorizationFails() {
        when(authorizationPort.requireOwnedChoir(EMAIL, CHOIR_ID))
                .thenReturn(denied());

        StepVerifier.create(useCase.createDraft(
                        EMAIL, CHOIR_ID, FORMATION_ID, "No autorizado"))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(authorizationPort).requireOwnedChoir(EMAIL, CHOIR_ID);
        verify(lifecyclePort, never()).createDraft(
                CHOIR_ID, FORMATION_ID, "No autorizado");
    }

    @Test
    void doesNotListVersionsWhenAuthorizationFails() {
        when(authorizationPort.requireOwnedChoir(EMAIL, CHOIR_ID))
                .thenReturn(denied());

        StepVerifier.create(useCase.list(EMAIL, CHOIR_ID, FORMATION_ID))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(authorizationPort).requireOwnedChoir(EMAIL, CHOIR_ID);
        verify(lifecyclePort, never()).list(CHOIR_ID, FORMATION_ID);
    }

    @Test
    void doesNotReadPublishedVersionWhenAuthorizationFails() {
        when(authorizationPort.requireOwnedChoir(EMAIL, CHOIR_ID))
                .thenReturn(denied());

        StepVerifier.create(useCase.published(
                        EMAIL, CHOIR_ID, FORMATION_ID))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(authorizationPort).requireOwnedChoir(EMAIL, CHOIR_ID);
        verify(lifecyclePort, never()).published(CHOIR_ID, FORMATION_ID);
    }

    @Test
    void doesNotPublishVersionWhenAuthorizationFails() {
        when(authorizationPort.requireOwnedChoir(EMAIL, CHOIR_ID))
                .thenReturn(denied());

        StepVerifier.create(useCase.publish(
                        EMAIL, CHOIR_ID, FORMATION_ID, VERSION_ID))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(authorizationPort).requireOwnedChoir(EMAIL, CHOIR_ID);
        verify(lifecyclePort, never()).publish(
                CHOIR_ID, FORMATION_ID, VERSION_ID);
    }

    private Mono<Void> denied() {
        return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "No autorizado"));
    }
}
