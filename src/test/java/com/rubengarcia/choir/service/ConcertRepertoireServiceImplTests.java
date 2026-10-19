package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.ConcertRepertoire;
import com.rubengarcia.choir.domain.RepertoireItem;
import com.rubengarcia.choir.repository.ConcertRepertoireRepository;
import com.rubengarcia.choir.repository.RepertoireItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertRepertoireServiceImplTests {

    @Mock
    private ResourceAuthorizationService authorizationService;

    @Mock
    private ConcertRepertoireRepository concertRepertoireRepository;

    @Mock
    private RepertoireItemRepository repertoireItemRepository;

    @InjectMocks
    private ConcertRepertoireServiceImpl service;

    @Test
    void replacesRepertoireInSubmittedOrderWithSongDetails() {
        RepertoireItem firstItem = mock(RepertoireItem.class);
        RepertoireItem secondItem = mock(RepertoireItem.class);
        when(firstItem.getId()).thenReturn(30L);
        when(secondItem.getId()).thenReturn(31L);
        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepertoireRepository.findStatusForUpdate(10L, 20L))
                .thenReturn(Mono.just("REHEARSAL"));
        when(repertoireItemRepository.findByChoirId(10L))
                .thenReturn(Flux.just(firstItem, secondItem));
        when(concertRepertoireRepository.deleteByConcertId(20L))
                .thenReturn(Mono.just(0L));
        when(concertRepertoireRepository.insert(any(ConcertRepertoire.class)))
                .thenAnswer(invocation ->
                        Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.replaceRepertoire(
                        "director@test.com",
                        10L,
                        20L,
                        List.of(
                                new ConcertRepertoireService.SongSelection(
                                        31L, 5, "Piano"),
                                new ConcertRepertoireService.SongSelection(
                                        30L, null, null))))
                .verifyComplete();

        verify(concertRepertoireRepository).insert(argThat(item ->
                item.getRepertoireItemId().equals(31L)
                        && item.getOrderIndex() == 0
                        && item.getDurationMinutes() == 5
                        && item.getStageRequirements().equals("Piano")));
        verify(concertRepertoireRepository).insert(argThat(item ->
                item.getRepertoireItemId().equals(30L)
                        && item.getOrderIndex() == 1
                        && item.getDurationMinutes() == null));
    }

    @Test
    void rejectsSongsFromOtherChoirsBeforeReplacingRepertoire() {
        RepertoireItem validItem = mock(RepertoireItem.class);
        when(validItem.getId()).thenReturn(30L);
        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepertoireRepository.findStatusForUpdate(10L, 20L))
                .thenReturn(Mono.just("PLANNED"));
        when(repertoireItemRepository.findByChoirId(10L))
                .thenReturn(Flux.just(validItem));

        StepVerifier.create(service.replaceRepertoire(
                        "director@test.com",
                        10L,
                        20L,
                        List.of(new ConcertRepertoireService.SongSelection(
                                99L, null, null))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        verify(concertRepertoireRepository, never()).deleteByConcertId(20L);
    }

    @Test
    void rejectsEditingPublishedConcerts() {
        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(concertRepertoireRepository.findStatusForUpdate(10L, 20L))
                .thenReturn(Mono.just("PUBLISHED"));

        StepVerifier.create(service.replaceRepertoire(
                        "director@test.com",
                        10L,
                        20L,
                        List.of()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                })
                .verify();

        verifyNoInteractions(repertoireItemRepository);
        verify(concertRepertoireRepository, never()).deleteByConcertId(20L);
    }
}
