package com.rubengarcia.choir.scene.application.usecase;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.scene.application.port.out.StageSnapshotPersistencePort;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageElement;
import com.rubengarcia.choir.scene.domain.StageElementType;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StageGeometryQueryServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long CONCERT_ID = 2L;
    private static final long REPERTOIRE_ITEM_ID = 3L;

    @Mock
    private StageSnapshotPersistencePort snapshots;

    @Mock
    private StageAuthorizationPort authorization;

    @InjectMocks
    private StageGeometryQueryService service;

    @Test
    void geometryExposesSnapshotIdentityVersionAndElevations() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.empty());
        when(snapshots.findForInclusion(CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.just(snapshotWithStep()));

        StepVerifier.create(service.geometry(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .assertNext(geometry -> {
                    org.junit.jupiter.api.Assertions.assertEquals(77L, geometry.snapshotId());
                    org.junit.jupiter.api.Assertions.assertEquals(1, geometry.version());
                    org.junit.jupiter.api.Assertions.assertEquals(0,
                            new BigDecimal("15").compareTo(geometry.elements().get(0).elevationCm()));
                })
                .verifyComplete();
    }

    @Test
    void foreignOccurrenceIsRejectedBeforeReadingTheSnapshot() {
        when(authorization.requireOwnedOccurrence(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        StepVerifier.create(service.geometry(EMAIL, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID))
                .expectError(ResponseStatusException.class)
                .verify();

        verifyNoInteractions(snapshots);
    }

    private static StageSnapshot snapshotWithStep() {
        StageBoundary rectangle = new StageBoundary(List.of(
                new StageBoundaryPoint(bd(0), bd(0)),
                new StageBoundaryPoint(bd(800), bd(0)),
                new StageBoundaryPoint(bd(800), bd(600)),
                new StageBoundaryPoint(bd(0), bd(600))));
        return new StageSnapshot(77L, CHOIR_ID, CONCERT_ID, REPERTOIRE_ITEM_ID, 10L,
                "Escenario", bd(800), bd(600), rectangle, List.of(),
                List.of(new StageElement("Escalón", StageElementType.STEP, bd(100), bd(100),
                        bd(200), bd(30), bd(15))),
                LocalDateTime.of(2026, 11, 26, 10, 0));
    }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value);
    }
}
