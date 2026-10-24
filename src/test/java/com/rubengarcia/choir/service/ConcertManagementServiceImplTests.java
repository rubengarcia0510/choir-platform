package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Concert;
import com.rubengarcia.choir.repository.ConcertRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertManagementServiceImplTests {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private ResourceAuthorizationService authorizationService;

    @InjectMocks
    private ConcertManagementServiceImpl service;

    @Test
    void createRequiresOwnershipAndPersistsConcertInConfigurationState() {
        Concert saved = new Concert();
        saved.setChoirId(10L);
        saved.setTitle("Concierto de primavera");
        saved.setConcertDate(LocalDate.of(2027, 10, 15));
        saved.setStatus("PLANNED");

        when(authorizationService.requireOwnedChoir("director@test.com", 10L))
                .thenReturn(Mono.empty());
        when(concertRepository.save(any(Concert.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.create(
                        "director@test.com", 10L,
                        " Concierto de primavera ",
                        LocalDate.of(2027, 10, 15), "  Teatro  "))
                .assertNext(concert -> {
                    assertThat(concert.getChoirId()).isEqualTo(10L);
                    assertThat(concert.getTitle()).isEqualTo("Concierto de primavera");
                    assertThat(concert.getConcertDate())
                            .isEqualTo(LocalDate.of(2027, 10, 15));
                    assertThat(concert.getStatus()).isEqualTo("PLANNED");
                })
                .verifyComplete();

        ArgumentCaptor<Concert> captor = ArgumentCaptor.forClass(Concert.class);
        verify(concertRepository).save(captor.capture());
        assertThat(captor.getValue().getVenue()).isEqualTo("Teatro");
    }

    @Test
    void createDoesNotPersistWhenDirectorDoesNotOwnChoir() {
        when(authorizationService.requireOwnedChoir("other@test.com", 10L))
                .thenReturn(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND)));

        StepVerifier.create(service.create(
                        "other@test.com", 10L, "Concierto",
                        LocalDate.of(2027, 10, 15), null))
                .expectError(ResponseStatusException.class)
                .verify();

        verifyNoInteractions(concertRepository);
    }

    @Test
    void updateChangesConcertDetailsWhenConcertIsConfigurable() {
        Concert existing = new Concert();
        existing.setChoirId(10L);
        existing.setTitle("Título anterior");
        existing.setConcertDate(LocalDate.of(2027, 10, 15));
        existing.setStatus("PLANNED");

        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepository.findById(20L)).thenReturn(Mono.just(existing));
        when(concertRepository.updateIfStatus(
                10L, 20L, "PLANNED", "Nuevo título",
                LocalDate.of(2027, 11, 20), "Auditorio", "REHEARSAL"))
                .thenReturn(Mono.just(1));

        StepVerifier.create(service.update(
                        "director@test.com", 10L, 20L,
                        "Nuevo título", LocalDate.of(2027, 11, 20),
                        "Auditorio", "REHEARSAL"))
                .assertNext(concert -> {
                    assertThat(concert.getTitle()).isEqualTo("Nuevo título");
                    assertThat(concert.getConcertDate())
                            .isEqualTo(LocalDate.of(2027, 11, 20));
                    assertThat(concert.getVenue()).isEqualTo("Auditorio");
                    assertThat(concert.getStatus()).isEqualTo("REHEARSAL");
                })
                .verifyComplete();

        verify(concertRepository, never()).save(any(Concert.class));
    }

    @Test
    void updateRejectsWhenConcertStatusChangesAfterItWasRead() {
        Concert existing = new Concert();
        existing.setChoirId(10L);
        existing.setTitle("Título anterior");
        existing.setStatus("PLANNED");

        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepository.findById(20L)).thenReturn(Mono.just(existing));
        when(concertRepository.updateIfStatus(
                10L, 20L, "PLANNED", "Título nuevo",
                LocalDate.of(2027, 11, 20), null, "PLANNED"))
                .thenReturn(Mono.just(0));

        StepVerifier.create(service.update(
                        "director@test.com", 10L, 20L,
                        "Título nuevo", LocalDate.of(2027, 11, 20),
                        null, "PLANNED"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                })
                .verify();

        verify(concertRepository, never()).save(any(Concert.class));
    }

    @Test
    void updateRejectsUnsupportedStatus() {
        Concert existing = new Concert();
        existing.setChoirId(10L);
        existing.setTitle("Concierto");
        existing.setStatus("PLANNED");

        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepository.findById(20L)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.update(
                        "director@test.com", 10L, 20L,
                        "Concierto", LocalDate.of(2027, 10, 15),
                        null, "PUBLISHED"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        verify(concertRepository, never()).save(any(Concert.class));
    }

    @Test
    void archiveChangesStatusToArchived() {
        Concert existing = new Concert();
        existing.setChoirId(10L);
        existing.setTitle("Concierto");
        existing.setStatus("PLANNED");

        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepository.findById(20L)).thenReturn(Mono.just(existing));
        when(concertRepository.archiveIfStatus(10L, 20L, "PLANNED"))
                .thenReturn(Mono.just(1));

        StepVerifier.create(service.archive("director@test.com", 10L, 20L))
                .verifyComplete();

        verify(concertRepository).archiveIfStatus(10L, 20L, "PLANNED");
        verify(concertRepository, never()).save(any(Concert.class));
    }
}
