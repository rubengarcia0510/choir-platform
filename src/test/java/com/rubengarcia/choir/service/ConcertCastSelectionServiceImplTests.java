package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Singer;
import com.rubengarcia.choir.repository.ConcertCastSelectionRepository;
import com.rubengarcia.choir.repository.SingerRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertCastSelectionServiceImplTests {

    @Mock
    private ResourceAuthorizationService authorizationService;

    @Mock
    private SingerRepository singerRepository;

    @Mock
    private ConcertCastSelectionRepository selectionRepository;

    @InjectMocks
    private ConcertCastSelectionServiceImpl service;

    @Test
    void replacesSelectionForOwnedConcertWithActiveChoirSingers() {
        Singer singer = mock(Singer.class);
        when(singer.getId()).thenReturn(30L);
        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(singerRepository.findByChoirIdAndActiveTrue(10L))
                .thenReturn(Flux.just(singer));
        when(selectionRepository.replaceSelection(10L, 20L, List.of(30L)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.replaceSelection(
                        "director@test.com", 10L, 20L, List.of(30L)))
                .verifyComplete();

        verify(selectionRepository).replaceSelection(10L, 20L, List.of(30L));
    }

    @Test
    void rejectsDuplicateSingerIdsWithoutReplacingSelection() {
        StepVerifier.create(service.replaceSelection(
                        "director@test.com", 10L, 20L, List.of(30L, 30L)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        verifyNoInteractions(
                authorizationService, singerRepository, selectionRepository);
    }

    @Test
    void rejectsSingerNotActiveInChoirWithoutReplacingSelection() {
        when(authorizationService.requireOwnedConcert(
                "director@test.com", 10L, 20L)).thenReturn(Mono.empty());
        when(singerRepository.findByChoirIdAndActiveTrue(10L))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.replaceSelection(
                        "director@test.com", 10L, 20L, List.of(30L)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        verifyNoInteractions(selectionRepository);
    }
}
