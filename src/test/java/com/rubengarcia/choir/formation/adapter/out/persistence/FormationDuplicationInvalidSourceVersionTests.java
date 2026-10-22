package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FormationDuplicationInvalidSourceVersionTests {

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
    }

    @Test
    void rejectsUnknownExplicitVersionWithoutCreatingRecords() {
        givenSourceVersions(version(101L, 10L, 1, "PUBLISHED"));

        assertThatThrownBy(() -> duplicate(999L).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe o no está publicada");

        verify(formationRepository, never()).save(any(FormationEntity.class));
        verify(versionRepository, never()).save(any(FormationVersionEntity.class));
        verify(positionRepository, never()).findByFormationVersionId(any());
    }

    @Test
    void rejectsPublishedVersionIdThatDoesNotBelongToSourceFormation() {
        givenSourceVersions(
                version(201L, 20L, 1, "PUBLISHED"));

        assertThatThrownBy(() -> duplicate(201L).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe o no está publicada");

        verify(formationRepository, never()).save(any(FormationEntity.class));
        verify(versionRepository, never()).save(any(FormationVersionEntity.class));
        verify(positionRepository, never()).findByFormationVersionId(any());
    }

    @Test
    void rejectsAutomaticSelectionWhenOnlyDraftAndArchivedVersionsExist() {
        givenSourceVersions(
                version(102L, 10L, 2, "DRAFT"),
                version(103L, 10L, 3, "ARCHIVED"));

        assertThatThrownBy(() -> duplicate(null).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene una versión publicada");

        verify(formationRepository, never()).save(any(FormationEntity.class));
        verify(versionRepository, never()).save(any(FormationVersionEntity.class));
        verify(positionRepository, never()).findByFormationVersionId(any());
    }

    private Mono<FormationDuplicationPort.CreatedFormation> duplicate(
            Long sourceVersionId) {
        return duplicationAdapter.duplicate(
                1L, 2L, 3L, 10L, sourceVersionId, "Copia", "Prueba");
    }

    private void givenSourceVersions(FormationVersionEntity... versions) {
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
