package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.domain.Singer;
import com.rubengarcia.choir.domain.VocalSection;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import com.rubengarcia.choir.repository.SingerRepository;
import com.rubengarcia.choir.repository.VocalSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoicePartMigrationServiceTests {

    private static final String EMAIL = "director@example.com";
    private static final Long CHOIR_ID = 1L;
    private static final Long SECTION_ID = 3L;

    @Mock
    private DirectorRepository directorRepository;

    @Mock
    private ChoirRepository choirRepository;

    @Mock
    private SingerRepository singerRepository;

    @Mock
    private VocalSectionRepository vocalSectionRepository;

    private VoicePartMigrationService service;

    @BeforeEach
    void setUp() {
        service = new VoicePartMigrationService(
                directorRepository,
                choirRepository,
                singerRepository,
                vocalSectionRepository
        );
    }

    private void authenticateOwnedChoir() {
        Director director = mock(Director.class);
        when(director.getId()).thenReturn(10L);
        when(directorRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Mono.just(director));

        Choir choir = mock(Choir.class);
        when(choir.getId()).thenReturn(CHOIR_ID);
        when(choir.getDirectorId()).thenReturn(10L);
        when(choirRepository.findById(CHOIR_ID))
                .thenReturn(Mono.just(choir));
    }

    private VocalSection activeSection(Long choirId) {
        VocalSection section = new VocalSection();
        section.setChoirId(choirId);
        section.setActive(true);
        return section;
    }

    @Test
    void confirmationIsRequiredBeforeAnyRepositoryAccess() {
        StepVerifier.create(service.confirm(
                        EMAIL,
                        CHOIR_ID,
                        false,
                        List.of(new VoicePartMigrationService.MappingConfirmation(
                                "Tenor", SECTION_ID
                        ))
                ))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.BAD_REQUEST,
                                ((ResponseStatusException) error).getStatusCode()
                        ))
                .verify();

        verifyNoInteractions(
                directorRepository,
                choirRepository,
                singerRepository,
                vocalSectionRepository
        );
    }

    @Test
    void confirmationRejectsEmptyMappings() {
        StepVerifier.create(service.confirm(
                        EMAIL, CHOIR_ID, true, List.of()
                ))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.BAD_REQUEST,
                                ((ResponseStatusException) error).getStatusCode()
                        ))
                .verify();

        verifyNoInteractions(
                directorRepository,
                choirRepository,
                singerRepository,
                vocalSectionRepository
        );
    }

    @Test
    void directorCannotMigrateSingersFromAnotherChoir() {
        Director director = mock(Director.class);
        when(director.getId()).thenReturn(10L);
        when(directorRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Mono.just(director));

        Choir anotherChoir = mock(Choir.class);
        when(anotherChoir.getDirectorId()).thenReturn(99L);
        when(choirRepository.findById(CHOIR_ID))
                .thenReturn(Mono.just(anotherChoir));

        StepVerifier.create(service.confirm(
                        EMAIL,
                        CHOIR_ID,
                        true,
                        List.of(new VoicePartMigrationService.MappingConfirmation(
                                "Tenor", SECTION_ID
                        ))
                ))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.NOT_FOUND,
                                ((ResponseStatusException) error).getStatusCode()
                        ))
                .verify();

        verifyNoInteractions(singerRepository, vocalSectionRepository);
    }

    @Test
    void rejectsSectionBelongingToAnotherChoir() {
        authenticateOwnedChoir();

        when(vocalSectionRepository.findById(SECTION_ID))
                .thenReturn(Mono.just(activeSection(99L)));

        StepVerifier.create(service.confirm(
                        EMAIL,
                        CHOIR_ID,
                        true,
                        List.of(new VoicePartMigrationService.MappingConfirmation(
                                "Tenor", SECTION_ID
                        ))
                ))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.BAD_REQUEST,
                                ((ResponseStatusException) error).getStatusCode()
                        ))
                .verify();

        verifyNoInteractions(singerRepository);
    }

    @Test
    void migratesOnlyMatchingSingersWithoutExistingSectionAndPreservesLegacyValue() {
        authenticateOwnedChoir();

        when(vocalSectionRepository.findById(SECTION_ID))
                .thenReturn(Mono.just(activeSection(CHOIR_ID)));

        Singer unassignedTenor = mock(Singer.class);
        when(unassignedTenor.getVoicePart()).thenReturn("Tenor");
        when(unassignedTenor.getVoiceSectionId()).thenReturn(null);

        Singer alreadyAssignedTenor = mock(Singer.class);
        when(alreadyAssignedTenor.getVoicePart()).thenReturn("Tenor");
        when(alreadyAssignedTenor.getVoiceSectionId()).thenReturn(8L);

        Singer unassignedSoprano = mock(Singer.class);
        when(unassignedSoprano.getVoicePart()).thenReturn("Soprano");

        when(singerRepository.findByChoirId(CHOIR_ID))
                .thenReturn(Flux.just(
                        unassignedTenor,
                        alreadyAssignedTenor,
                        unassignedSoprano
                ));

        when(singerRepository.save(any(Singer.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.confirm(
                        EMAIL,
                        CHOIR_ID,
                        true,
                        List.of(new VoicePartMigrationService.MappingConfirmation(
                                "Tenor", SECTION_ID
                        ))
                ))
                .assertNext(result -> {
                    assertEquals(CHOIR_ID, result.choirId());
                    assertEquals(1L, result.migratedSingerCount());
                    assertEquals(1, result.confirmedMappingCount());
                })
                .verifyComplete();

        verify(unassignedTenor).setVoiceSectionId(SECTION_ID);
        verify(singerRepository).save(unassignedTenor);

        verify(alreadyAssignedTenor, never())
                .setVoiceSectionId(any());
        verify(unassignedSoprano, never())
                .setVoiceSectionId(any());

        verify(unassignedTenor, never()).setVoicePart(any());
        verify(alreadyAssignedTenor, never()).setVoicePart(any());
        verify(unassignedSoprano, never()).setVoicePart(any());

        verify(singerRepository, times(1)).save(any(Singer.class));
    }

    @Test
    void rejectsDuplicateMappingsForSameLegacyValue() {
        StepVerifier.create(service.confirm(
                        EMAIL,
                        CHOIR_ID,
                        true,
                        List.of(
                                new VoicePartMigrationService.MappingConfirmation(
                                        "Tenor", 3L
                                ),
                                new VoicePartMigrationService.MappingConfirmation(
                                        "Tenor", 4L
                                )
                        )
                ))
                .expectErrorSatisfies(error ->
                        assertEquals(
                                HttpStatus.BAD_REQUEST,
                                ((ResponseStatusException) error).getStatusCode()
                        ))
                .verify();

        verifyNoInteractions(
                directorRepository,
                choirRepository,
                singerRepository,
                vocalSectionRepository
        );
    }

    @Test
    void profileSuggestsUniqueNormalizedMatchButRequiresConfirmation() {
        authenticateOwnedChoir();

        Singer tenor = mock(Singer.class);
        when(tenor.getVoicePart()).thenReturn(" tenor ");

        VocalSection section = mock(VocalSection.class);
        when(section.getId()).thenReturn(SECTION_ID);
        when(section.getName()).thenReturn("Tenor");
        when(section.isActive()).thenReturn(true);

        when(singerRepository.findByChoirId(CHOIR_ID))
                .thenReturn(Flux.just(tenor));
        when(vocalSectionRepository.findByChoirIdOrderBySortOrderAscNameAsc(
                CHOIR_ID
        )).thenReturn(Flux.just(section));

        StepVerifier.create(service.profile(EMAIL, CHOIR_ID))
                .assertNext(profile -> {
                    assertEquals(1, profile.totalSingers());
                    assertEquals(1, profile.values().size());

                    var value = profile.values().get(0);
                    assertEquals(" tenor ", value.legacyValue());
                    assertEquals(1L, value.singerCount());
                    assertEquals(SECTION_ID, value.suggestedSectionId());
                    assertEquals(false, value.ambiguous());
                    assertEquals(false, value.requiresConfirmation());
                })
                .verifyComplete();

        verify(tenor, never()).setVoiceSectionId(any());
        verify(singerRepository, never()).save(any(Singer.class));
    }
}
