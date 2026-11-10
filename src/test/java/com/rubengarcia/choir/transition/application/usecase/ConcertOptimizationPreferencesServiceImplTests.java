package com.rubengarcia.choir.transition.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.service.ResourceAuthorizationService;
import com.rubengarcia.choir.transition.application.port.out.ConcertOptimizationPreferencesPort;
import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ConcertOptimizationPreferencesServiceImplTests {

    private static final String EMAIL = "director@example.com";
    private static final long CHOIR_ID = 10L;
    private static final long CONCERT_ID = 20L;

    @Mock
    private ConcertOptimizationPreferencesPort preferencesPort;

    @Mock
    private ResourceAuthorizationService authorizationService;

    @InjectMocks
    private ConcertOptimizationPreferencesServiceImpl service;

    @Test
    void returnsDefaultsWithoutPersistingWhenPreferencesDoNotExist() {
        when(authorizationService.requireOwnedConcert(
                EMAIL, CHOIR_ID, CONCERT_ID)).thenReturn(Mono.empty());
        when(preferencesPort.findByConcertId(CONCERT_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.get(EMAIL, CHOIR_ID, CONCERT_ID))
                .assertNext(preferences -> assertThat(preferences)
                        .isEqualTo(ConcertOptimizationPreferences.defaults()))
                .verifyComplete();

        verify(preferencesPort).findByConcertId(CONCERT_ID);
        verify(preferencesPort, never()).save(
                CONCERT_ID, ConcertOptimizationPreferences.defaults());
    }

    @Test
    void returnsPersistedPreferencesWhenTheyExist() {
        ConcertOptimizationPreferences stored =
                new ConcertOptimizationPreferences(50, 30, 15, 5);

        when(authorizationService.requireOwnedConcert(
                EMAIL, CHOIR_ID, CONCERT_ID)).thenReturn(Mono.empty());
        when(preferencesPort.findByConcertId(CONCERT_ID))
                .thenReturn(Mono.just(stored));

        StepVerifier.create(service.get(EMAIL, CHOIR_ID, CONCERT_ID))
                .expectNext(stored)
                .verifyComplete();

        verify(preferencesPort).findByConcertId(CONCERT_ID);
    }

    @Test
    void savesPreferencesAfterAuthorization() {
        ConcertOptimizationPreferences requested =
                new ConcertOptimizationPreferences(40, 30, 20, 10);

        when(authorizationService.requireOwnedConcert(
                EMAIL, CHOIR_ID, CONCERT_ID)).thenReturn(Mono.empty());
        when(preferencesPort.save(CONCERT_ID, requested))
                .thenReturn(Mono.just(requested));

        StepVerifier.create(service.update(
                        EMAIL, CHOIR_ID, CONCERT_ID, requested))
                .expectNext(requested)
                .verifyComplete();

        verify(authorizationService).requireOwnedConcert(
                EMAIL, CHOIR_ID, CONCERT_ID);
        verify(preferencesPort).save(CONCERT_ID, requested);
    }

    @Test
    void doesNotReadPreferencesWhenAuthorizationFails() {
        RuntimeException authorizationError =
                new RuntimeException("Unauthorized");

        when(authorizationService.requireOwnedConcert(
                EMAIL, CHOIR_ID, CONCERT_ID))
                .thenReturn(Mono.error(authorizationError));

        StepVerifier.create(service.get(EMAIL, CHOIR_ID, CONCERT_ID))
                .expectErrorMatches(error -> error == authorizationError)
                .verify();

        verify(preferencesPort, never()).findByConcertId(CONCERT_ID);
    }

    @Test
    void doesNotSavePreferencesWhenAuthorizationFails() {
        RuntimeException authorizationError =
                new RuntimeException("Unauthorized");
        ConcertOptimizationPreferences requested =
                new ConcertOptimizationPreferences(40, 30, 20, 10);

        when(authorizationService.requireOwnedConcert(
                EMAIL, CHOIR_ID, CONCERT_ID))
                .thenReturn(Mono.error(authorizationError));

        StepVerifier.create(service.update(
                        EMAIL, CHOIR_ID, CONCERT_ID, requested))
                .expectErrorMatches(error -> error == authorizationError)
                .verify();

        verify(preferencesPort, never()).save(CONCERT_ID, requested);
    }
}
