package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.domain.VocalSection;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import com.rubengarcia.choir.repository.VocalSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocalSectionServiceTests {

    @Mock
    private DirectorRepository directorRepository;

    @Mock
    private ChoirRepository choirRepository;

    @Mock
    private VocalSectionRepository vocalSectionRepository;

    private VocalSectionService service;

    @BeforeEach
    void setUp() {
        service = new VocalSectionService(
                directorRepository,
                choirRepository,
                vocalSectionRepository
        );
    }

    private void authenticateDirector() {
        Director director = mock(Director.class);
        when(director.getId()).thenReturn(10L);
        when(directorRepository.findByEmailIgnoreCase("director@example.com"))
                .thenReturn(Mono.just(director));
    }

    @Test
    void directorCannotListAnotherDirectorsChoirSections() {
        authenticateDirector();

        Choir anotherChoir = mock(Choir.class);
        when(anotherChoir.getDirectorId()).thenReturn(99L);
        when(choirRepository.findById(2L)).thenReturn(Mono.just(anotherChoir));

        StepVerifier.create(service.list("director@example.com", 2L, false))
                .expectErrorSatisfies(error ->
                        assertEquals(HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();

        verifyNoInteractions(vocalSectionRepository);
    }

    @Test
    void directorCannotModifySectionFromAnotherChoir() {
        authenticateDirector();

        Choir anotherChoir = mock(Choir.class);
        when(anotherChoir.getDirectorId()).thenReturn(99L);
        when(choirRepository.findById(2L)).thenReturn(Mono.just(anotherChoir));

        StepVerifier.create(service.update(
                        "director@example.com", 2L, 50L, "Tenores", 1, true))
                .expectErrorSatisfies(error ->
                        assertEquals(HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();

        verify(vocalSectionRepository, never()).findById(any(Long.class));
        verify(vocalSectionRepository, never()).save(any(VocalSection.class));
    }

    @Test
    void directorCannotDeactivateSectionFromAnotherChoir() {
        authenticateDirector();

        Choir anotherChoir = mock(Choir.class);
        when(anotherChoir.getDirectorId()).thenReturn(99L);
        when(choirRepository.findById(2L)).thenReturn(Mono.just(anotherChoir));

        StepVerifier.create(service.deactivate("director@example.com", 2L, 50L))
                .expectErrorSatisfies(error ->
                        assertEquals(HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()))
                .verify();

        verify(vocalSectionRepository, never()).findById(any(Long.class));
        verify(vocalSectionRepository, never()).save(any(VocalSection.class));
    }

    @Test
    void directorCanCreateSectionInOwnChoir() {
        authenticateDirector();

        Choir ownedChoir = mock(Choir.class);
        when(ownedChoir.getId()).thenReturn(1L);
        when(ownedChoir.getDirectorId()).thenReturn(10L);
        when(choirRepository.findById(1L)).thenReturn(Mono.just(ownedChoir));
        when(vocalSectionRepository.save(any(VocalSection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(
                        "director@example.com", 1L, "  Tenores  ", 1))
                .assertNext(section -> {
                    assertEquals(1L, section.getChoirId());
                    assertEquals("Tenores", section.getName());
                    assertEquals(1, section.getSortOrder());
                    assertTrue(section.isActive());
                })
                .verifyComplete();
    }
}
