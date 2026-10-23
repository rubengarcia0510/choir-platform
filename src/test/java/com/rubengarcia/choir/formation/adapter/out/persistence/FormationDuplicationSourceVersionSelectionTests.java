package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FormationDuplicationSourceVersionSelectionTests {

    @Mock
    private FormationRepository formationRepository;

    @Mock
    private FormationVersionRepository versionRepository;

    @Mock
    private FormationPositionRepository positionRepository;

    @InjectMocks
    private FormationDuplicationPersistenceAdapter duplicationAdapter;

    @BeforeEach
    void setUp() {
        FormationEntity sourceFormation = new FormationEntity();
        sourceFormation.setId(10L);
        sourceFormation.setChoirId(1L);
        sourceFormation.setConcertId(2L);
        sourceFormation.setRepertoireItemId(3L);
        sourceFormation.setName("Formación original");

        when(formationRepository.findByChoirIdAndConcertIdAndRepertoireItemId(
                1L, 2L, 3L))
                .thenReturn(Flux.just(sourceFormation));

        FormationEntity savedFormation = new FormationEntity();
        savedFormation.setId(30L);
        savedFormation.setChoirId(1L);
        savedFormation.setConcertId(2L);
        savedFormation.setRepertoireItemId(3L);
        savedFormation.setName("Copia");

        Mockito.lenient().when(formationRepository.save(any(FormationEntity.class)))
                .thenReturn(Mono.just(savedFormation));

        FormationVersionEntity savedDraft = new FormationVersionEntity();
        savedDraft.setId(40L);
        savedDraft.setFormationId(30L);
        savedDraft.setVersionNumber(1);
        savedDraft.setStatus("DRAFT");

        Mockito.lenient().when(versionRepository.save(any(FormationVersionEntity.class)))
                .thenReturn(Mono.just(savedDraft));

        Mockito.lenient().when(positionRepository.findByFormationVersionId(any()))
                .thenReturn(Flux.empty());
    }

    @Test
    void selectsHighestPublishedVersionInsteadOfDraftWhenSourceVersionIsOmitted() {
        givenVersions(
                version(101L, 10L, 1, "PUBLISHED"),
                version(102L, 10L, 2, "PUBLISHED"),
                version(103L, 10L, 3, "DRAFT"));

        duplicationAdapter.duplicate(
                1L, 2L, 3L, 10L, null, "Copia", "Prueba").block();

        verify(positionRepository).findByFormationVersionId(102L);
        verify(positionRepository, never()).findByFormationVersionId(103L);
    }

    @Test
    void selectsHighestNumberedPublishedVersionWhenSeveralArePublished() {
        givenVersions(
                version(101L, 10L, 1, "PUBLISHED"),
                version(103L, 10L, 3, "PUBLISHED"),
                version(102L, 10L, 2, "PUBLISHED"));

        duplicationAdapter.duplicate(
                1L, 2L, 3L, 10L, null, "Copia", "Prueba").block();

        verify(positionRepository).findByFormationVersionId(103L);
        verify(positionRepository, never()).findByFormationVersionId(101L);
        verify(positionRepository, never()).findByFormationVersionId(102L);
    }

    @Test
    void rejectsDuplicationWhenNoPublishedVersionExists() {
        givenVersions(
                version(101L, 10L, 1, "DRAFT"),
                version(102L, 10L, 2, "DRAFT"));

        assertThatThrownBy(() -> duplicationAdapter.duplicate(
                        1L, 2L, 3L, 10L, null, "Copia", "Prueba")
                .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene una versión publicada");

        verify(formationRepository, never()).save(any(FormationEntity.class));
        verify(positionRepository, never()).findByFormationVersionId(any());
    }

    @Test
    void rejectsExplicitDraftVersionEvenWhenPublishedVersionExists() {
        givenVersions(
                version(101L, 10L, 1, "PUBLISHED"),
                version(103L, 10L, 3, "DRAFT"));

        assertThatThrownBy(() -> duplicationAdapter.duplicate(
                        1L, 2L, 3L, 10L, 103L, "Copia", "Prueba")
                .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe o no está publicada");

        verify(formationRepository, never()).save(any(FormationEntity.class));
        verify(positionRepository, never()).findByFormationVersionId(any());
    }

    private void givenVersions(FormationVersionEntity... versions) {
        when(versionRepository.findByFormationId(10L))
                .thenReturn(Flux.fromArray(versions));
    }

    private FormationVersionEntity version(
            Long id,
            Long formationId,
            int versionNumber,
            String status) {
        FormationVersionEntity version = new FormationVersionEntity();
        version.setId(id);
        version.setFormationId(formationId);
        version.setVersionNumber(versionNumber);
        version.setStatus(status);
        return version;
    }
}
