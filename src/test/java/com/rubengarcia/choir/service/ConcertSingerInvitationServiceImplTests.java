package com.rubengarcia.choir.service;

import com.rubengarcia.choir.repository.ConcertSingerInvitationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertSingerInvitationServiceImplTests {

    @Mock
    private ConcertSingerInvitationRepository repository;

    @Mock
    private ResourceAuthorizationService authorizationService;

    @InjectMocks
    private ConcertSingerInvitationServiceImpl service;

    @Test
    void createsInvitationForPublishedConcertAndStoresOnlyTokenHash() {
        when(authorizationService.requireOwnedConcert("director@test.com", 1L, 2L))
                .thenReturn(Mono.empty());
        when(repository.findConcertStatus(1L, 2L))
                .thenReturn(Mono.just("PUBLISHED"));
        when(repository.findActiveSinger(1L, 3L))
                .thenReturn(Mono.just(3L));
        when(repository.insertInvitation(eq(1L), eq(2L), eq(3L), anyString(), isNull()))
                .thenReturn(Mono.just(42L));

        StepVerifier.create(service.create("director@test.com", 1L, 2L, 3L, null))
                .assertNext(invitation -> {
                    assertThat(invitation.invitationId()).isEqualTo(42L);
                    assertThat(invitation.concertId()).isEqualTo(2L);
                    assertThat(invitation.singerId()).isEqualTo(3L);
                    assertThat(invitation.token()).isNotBlank();
                    assertThat(invitation.token()).hasSize(43);
                    assertThat(invitation.expiresAt()).isNull();

                    ArgumentCaptor<String> hashCaptor =
                            ArgumentCaptor.forClass(String.class);
                    verify(repository).insertInvitation(
                            eq(1L), eq(2L), eq(3L), hashCaptor.capture(), isNull());
                    assertThat(hashCaptor.getValue()).hasSize(64);
                    assertThat(hashCaptor.getValue()).isNotEqualTo(invitation.token());
                })
                .verifyComplete();
    }

    @Test
    void refusesInvitationForUnpublishedConcert() {
        when(authorizationService.requireOwnedConcert("director@test.com", 1L, 2L))
                .thenReturn(Mono.empty());
        when(repository.findConcertStatus(1L, 2L))
                .thenReturn(Mono.just("PLANNED"));

        StepVerifier.create(service.create("director@test.com", 1L, 2L, 3L, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                })
                .verify();

        verify(repository, never()).insertInvitation(
                anyLong(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void refusesInvitationForInactiveOrMissingSinger() {
        when(authorizationService.requireOwnedConcert("director@test.com", 1L, 2L))
                .thenReturn(Mono.empty());
        when(repository.findConcertStatus(1L, 2L))
                .thenReturn(Mono.just("PUBLISHED"));
        when(repository.findActiveSinger(1L, 3L)).thenReturn(Mono.empty());

        StepVerifier.create(service.create("director@test.com", 1L, 2L, 3L, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();

        verify(repository, never()).insertInvitation(
                anyLong(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void refusesExpirationDateInThePast() {
        StepVerifier.create(service.create(
                        "director@test.com", 1L, 2L, 3L,
                        LocalDateTime.now().minusMinutes(1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        verifyNoInteractions(repository, authorizationService);
    }

    @Test
    void revokesInvitationOnlyAfterAuthorization() {
        when(authorizationService.requireOwnedConcert("director@test.com", 1L, 2L))
                .thenReturn(Mono.empty());
        when(repository.revoke(1L, 2L, 42L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.revoke("director@test.com", 1L, 2L, 42L))
                .verifyComplete();

        verify(repository).revoke(1L, 2L, 42L);
    }

    @Test
    void reportsMissingInvitationWhenRevocationUpdatesNoRows() {
        when(authorizationService.requireOwnedConcert("director@test.com", 1L, 2L))
                .thenReturn(Mono.empty());
        when(repository.revoke(1L, 2L, 42L)).thenReturn(Mono.just(0));

        StepVerifier.create(service.revoke("director@test.com", 1L, 2L, 42L))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void rejectsBlankPublicToken() {
        StepVerifier.create(service.viewByToken(" "))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnknownOrRevokedToken() {
        when(repository.findUsableInvitation(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.viewByToken("unknown-token"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void loadsConcertDetailsAndSnapshotsForUsableInvitation() {
        when(repository.findUsableInvitation(anyString()))
                .thenReturn(Mono.just(new ConcertSingerInvitationRepository.InvitationMeta(
                        42L, 2L, 3L, 1L)));
        when(repository.findConcertDetails(1L, 2L))
                .thenReturn(Mono.just(new ConcertSingerInvitationRepository.ConcertDetails(
                        "Concierto", LocalDate.of(2026, 12, 1), "Sala")));
        when(repository.findInvitedSingerSnapshotName(2L, 3L))
                .thenReturn(Mono.just("Coreuta histórico"));
        when(repository.findRepertoire(1L, 2L))
                .thenReturn(Mono.just(List.of()));
        when(repository.findCast(2L, 3L))
                .thenReturn(Mono.just(List.of()));
        when(repository.findVocalAssignments(2L, 3L))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.viewByToken("valid-token"))
                .assertNext(view -> {
                    assertThat(view.concertId()).isEqualTo(2L);
                    assertThat(view.title()).isEqualTo("Concierto");
                    assertThat(view.venue()).isEqualTo("Sala");
                    assertThat(view.invitedSingerName()).isEqualTo("Coreuta histórico");
                })
                .verifyComplete();

        verify(repository).findRepertoire(1L, 2L);
        verify(repository).findCast(2L, 3L);
        verify(repository).findVocalAssignments(2L, 3L);
    }

    @Test
    void rejectsInvitationViewWhenInvitedSingerHasNoSnapshot() {
        when(repository.findUsableInvitation(anyString()))
                .thenReturn(Mono.just(new ConcertSingerInvitationRepository.InvitationMeta(
                        42L, 2L, 3L, 1L)));
        when(repository.findConcertDetails(1L, 2L))
                .thenReturn(Mono.just(new ConcertSingerInvitationRepository.ConcertDetails(
                        "Concierto", LocalDate.of(2026, 12, 1), "Sala")));
        when(repository.findInvitedSingerSnapshotName(2L, 3L))
                .thenReturn(Mono.empty());
        when(repository.findRepertoire(1L, 2L)).thenReturn(Mono.just(List.of()));
        when(repository.findCast(2L, 3L)).thenReturn(Mono.just(List.of()));
        when(repository.findVocalAssignments(2L, 3L))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.viewByToken("valid-token"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }
}
