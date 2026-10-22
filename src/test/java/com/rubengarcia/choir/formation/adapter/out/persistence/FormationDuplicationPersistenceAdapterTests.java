package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class FormationDuplicationPersistenceAdapterTests {

    @Mock
    private FormationRepository formationRepository;

    @Mock
    private FormationVersionRepository versionRepository;

    @Mock
    private FormationPositionRepository positionRepository;

    @Mock
    private FormationEntity sourceFormation;

    @Mock
    private FormationVersionEntity sourceVersion;

    @InjectMocks
    private FormationDuplicationPersistenceAdapter duplicationAdapter;

    @Test
    void rejectsSourceFormationOutsideRequestedRepertoireScope() {
        when(formationRepository.findByChoirIdAndConcertIdAndRepertoireItemId(
                1L, 2L, 3L))
                .thenReturn(Flux.just(sourceFormation));
        when(sourceFormation.getId()).thenReturn(99L);

        assertThatThrownBy(() -> duplicationAdapter.duplicate(
                        1L, 2L, 3L, 10L, null, "Copia", "Prueba")
                .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "no pertenece a esta inclusión de repertorio");

        verify(versionRepository, never()).findByFormationId(any());
        verify(formationRepository, never()).save(any(FormationEntity.class));
    }

    @Test
    void rejectsMissingSourceFormation() {
        when(formationRepository.findByChoirIdAndConcertIdAndRepertoireItemId(
                1L, 2L, 3L))
                .thenReturn(Flux.empty());

        assertThatThrownBy(() -> duplicationAdapter.duplicate(
                        1L, 2L, 3L, 10L, null, "Copia", "Prueba")
                .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "no pertenece a esta inclusión de repertorio");

        verify(versionRepository, never()).findByFormationId(any());
        verify(formationRepository, never()).save(any(FormationEntity.class));
    }

    @Test
    void rejectsExplicitSourceVersionThatIsNotPublished() {
        when(formationRepository.findByChoirIdAndConcertIdAndRepertoireItemId(
                1L, 2L, 3L))
                .thenReturn(Flux.just(sourceFormation));
        when(sourceFormation.getId()).thenReturn(10L);
        when(versionRepository.findByFormationId(10L))
                .thenReturn(Flux.just(sourceVersion));
        when(sourceVersion.getId()).thenReturn(20L);
        when(sourceVersion.getStatus()).thenReturn("DRAFT");

        assertThatThrownBy(() -> duplicationAdapter.duplicate(
                        1L, 2L, 3L, 10L, 20L, "Copia", "Prueba")
                .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "no existe o no está publicada");

        verify(formationRepository, never()).save(any(FormationEntity.class));
    }
}
