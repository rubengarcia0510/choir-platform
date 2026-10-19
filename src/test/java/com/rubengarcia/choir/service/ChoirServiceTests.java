package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChoirServiceTests {

    @Mock
    private ChoirRepository choirRepository;

    @Mock
    private DirectorRepository directorRepository;

    @Mock
    private Director director;

    @InjectMocks
    private ChoirServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(director.getId()).thenReturn(10L);
        lenient().when(directorRepository.findByEmailIgnoreCase("director@example.com"))
                .thenReturn(Mono.just(director));
    }

    @Test
    void directorCanListOnlyOwnActiveChoirsByDefault() {
        Choir ownedChoir = mock(Choir.class);

        when(choirRepository.findByDirectorIdAndArchivedFalse(10L))
                .thenReturn(Flux.just(ownedChoir));

        StepVerifier.create(service.list("director@example.com", false))
                .expectNext(ownedChoir)
                .verifyComplete();

        verify(choirRepository).findByDirectorIdAndArchivedFalse(10L);
        verify(choirRepository, never()).findByDirectorId(any(Long.class));
    }

    @Test
    void directorCanIncludeArchivedChoirsWhenRequested() {
        Choir archivedChoir = mock(Choir.class);
        when(choirRepository.findByDirectorId(10L))
                .thenReturn(Flux.just(archivedChoir));

        StepVerifier.create(service.list("director@example.com", true))
                .expectNext(archivedChoir)
                .verifyComplete();

        verify(choirRepository).findByDirectorId(10L);
        verify(choirRepository, never())
                .findByDirectorIdAndArchivedFalse(any(Long.class));
    }

    @Test
    void directorCannotReadAnotherDirectorsChoir() {
        when(choirRepository.findByIdAndDirectorId(20L, 10L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.get("director@example.com", 20L))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();
    }

    @Test
    void directorCanCreateChoirWithNormalizedValues() {
        when(choirRepository.save(any(Choir.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(
                        "director@example.com", "  Coro nuevo  ", "  Descripción  "))
                .assertNext(choir -> {
                    assertEquals(10L, choir.getDirectorId());
                    assertEquals("Coro nuevo", choir.getName());
                    assertEquals("Descripción", choir.getDescription());
                    assertFalse(choir.isArchived());
                })
                .verifyComplete();

        verify(choirRepository).save(any(Choir.class));
    }

    @Test
    void directorCanUpdateOwnChoir() {
        Choir choir = mock(Choir.class);
        when(choir.isArchived()).thenReturn(false);
        when(choirRepository.findByIdAndDirectorId(20L, 10L))
                .thenReturn(Mono.just(choir));
        when(choirRepository.save(choir)).thenReturn(Mono.just(choir));

        StepVerifier.create(service.update(
                        "director@example.com", 20L,
                        "  Nombre actualizado  ", "  Nueva descripción  "))
                .expectNext(choir)
                .verifyComplete();

        verify(choir).setName("Nombre actualizado");
        verify(choir).setDescription("Nueva descripción");
        verify(choirRepository).save(choir);
    }

    @Test
    void directorCannotUpdateArchivedChoir() {
        Choir choir = mock(Choir.class);
        when(choir.isArchived()).thenReturn(true);
        when(choirRepository.findByIdAndDirectorId(20L, 10L))
                .thenReturn(Mono.just(choir));

        StepVerifier.create(service.update(
                        "director@example.com", 20L, "Nombre", "Descripción"))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.CONFLICT,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();

        verify(choirRepository, never()).save(any(Choir.class));
    }

    @Test
    void directorCanArchiveOwnChoir() {
        Choir choir = mock(Choir.class);
        when(choir.isArchived()).thenReturn(false);
        when(choirRepository.findByIdAndDirectorId(20L, 10L))
                .thenReturn(Mono.just(choir));
        when(choirRepository.save(choir)).thenReturn(Mono.just(choir));

        StepVerifier.create(service.archive("director@example.com", 20L))
                .verifyComplete();

        verify(choir).setArchived(true);
        verify(choirRepository).save(choir);
    }

    @Test
    void archivingAlreadyArchivedChoirDoesNotSaveAgain() {
        Choir choir = mock(Choir.class);
        when(choir.isArchived()).thenReturn(true);
        when(choirRepository.findByIdAndDirectorId(20L, 10L))
                .thenReturn(Mono.just(choir));

        StepVerifier.create(service.archive("director@example.com", 20L))
                .verifyComplete();

        verify(choirRepository, never()).save(any(Choir.class));
    }

    @Test
    void missingDirectorCannotCreateChoir() {
        when(directorRepository.findByEmailIgnoreCase("unknown@example.com"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.create(
                        "unknown@example.com", "Coro", null))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();

        verifyNoInteractions(choirRepository);
    }

    @Test
    void directorCannotUpdateAnotherDirectorsChoir() {
        when(choirRepository.findByIdAndDirectorId(99L, 10L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.update(
                        "director@example.com", 99L, "Nombre cambiado", "Descripción"))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException response
                                && response.getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();

        verify(choirRepository, never()).save(any(Choir.class));
    }

    @Test
    void directorCannotArchiveAnotherDirectorsChoir() {
        when(choirRepository.findByIdAndDirectorId(99L, 10L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.archive("director@example.com", 99L))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException response
                                && response.getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();

        verify(choirRepository, never()).save(any(Choir.class));
    }
}
