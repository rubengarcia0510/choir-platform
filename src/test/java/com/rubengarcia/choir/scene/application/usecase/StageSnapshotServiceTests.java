package com.rubengarcia.choir.scene.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.scene.application.exception.StageSnapshotAlreadyAssociatedException;
import com.rubengarcia.choir.scene.application.exception.StageSnapshotNotFoundException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateArchivedException;
import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.scene.application.port.out.StageSnapshotPersistencePort;
import com.rubengarcia.choir.scene.application.port.out.StageTemplatePersistencePort;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StageSnapshotServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;
    private static final long STAGE_ID = 10L;

    @Mock
    private StageTemplatePersistencePort templates;

    @Mock
    private StageSnapshotPersistencePort snapshots;

    @Mock
    private StageAuthorizationPort authorization;

    @InjectMocks
    private StageSnapshotService service;

    @Test
    void associateAuthorizesTheOccurrenceBeforeWritingAFullCopy() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.just(template(false)));
        when(snapshots.findForInclusion(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID)).thenReturn(Mono.empty());
        when(snapshots.insert(any(StageSnapshot.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.associate(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, STAGE_ID))
                .assertNext(snapshot -> {
                    assertEquals(CONCERT_ID, snapshot.concertId());
                    assertEquals(REPERTOIRE_ITEM_ID, snapshot.repertoireItemId());
                    assertEquals(STAGE_ID, snapshot.sourceTemplateId());
                    assertEquals("Escenario", snapshot.name());
                })
                .verifyComplete();

        InOrder order = inOrder(authorization, templates, snapshots);
        order.verify(authorization).requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
        order.verify(templates).findById(CHOIR_ID, STAGE_ID);
        order.verify(snapshots).insert(any(StageSnapshot.class));
    }

    @Test
    void foreignOccurrenceNeverReachesPersistence() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        StepVerifier.create(service.associate(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, STAGE_ID))
                .expectError(ResponseStatusException.class)
                .verify();

        verifyNoInteractions(templates, snapshots);
    }

    @Test
    void archivedTemplateCannotBeAssociated() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.just(template(true)));

        StepVerifier.create(service.associate(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, STAGE_ID))
                .expectError(StageTemplateArchivedException.class)
                .verify();

        verify(snapshots, never()).insert(any());
    }

    @Test
    void secondSnapshotForTheSameInclusionIsRejected() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.just(template(false)));
        when(snapshots.findForInclusion(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.just(snapshot()));

        StepVerifier.create(service.associate(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, STAGE_ID))
                .expectError(StageSnapshotAlreadyAssociatedException.class)
                .verify();

        verify(snapshots, never()).insert(any());
    }

    @Test
    void findSnapshotIsScopedToTheRequestedInclusion() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(snapshots.findForInclusion(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.just(snapshot()));

        StepVerifier.create(service.findSnapshot(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectNextMatches(snapshot -> snapshot.repertoireItemId() == REPERTOIRE_ITEM_ID)
                .verifyComplete();

        verify(snapshots).findForInclusion(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID);
    }

    @Test
    void findSnapshotReportsNotFoundForAnInclusionWithoutScenario() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(snapshots.findForInclusion(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.findSnapshot(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectError(StageSnapshotNotFoundException.class)
                .verify();
    }

    @Test
    void invalidInputIsRejectedBeforeAnyAuthorizationOrPersistence() {
        StepVerifier.create(service.associate(EMAIL, CHOIR_ID, CONCERT_ID, 0L, STAGE_ID))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(authorization, templates, snapshots);
    }

    private static StageTemplate template(boolean archived) {
        return new StageTemplate(STAGE_ID, CHOIR_ID, "Escenario", bd(800), bd(600),
                rectangle(), List.of(), List.of(), archived);
    }

    private static StageSnapshot snapshot() {
        return new StageSnapshot(77L, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, STAGE_ID,
                "Escenario", bd(800), bd(600), rectangle(), List.of(), List.of(),
                LocalDateTime.of(2026, 11, 26, 10, 0));
    }

    private static StageBoundary rectangle() {
        return new StageBoundary(List.of(
                new StageBoundaryPoint(bd(0), bd(0)),
                new StageBoundaryPoint(bd(800), bd(0)),
                new StageBoundaryPoint(bd(800), bd(600)),
                new StageBoundaryPoint(bd(0), bd(600))));
    }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value);
    }
}
