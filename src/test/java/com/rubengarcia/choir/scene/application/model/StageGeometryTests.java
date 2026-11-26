package com.rubengarcia.choir.scene.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageElement;
import com.rubengarcia.choir.scene.domain.StageElementType;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageGeometryTests {

    @Test
    void fromMapsSnapshotIdentityDimensionsAndElevationsWithVersionOne() {
        StageSnapshot snapshot = persisted(77L);

        StageGeometry geometry = StageGeometry.from(snapshot);

        assertEquals(77L, geometry.snapshotId());
        assertEquals(1, geometry.version());
        assertEquals(new BigDecimal("800"), geometry.widthCm());
        assertEquals(new BigDecimal("600"), geometry.depthCm());
        assertEquals(snapshot.boundary(), geometry.boundary());
        assertEquals(0, new BigDecimal("15").compareTo(geometry.elements().get(0).elevationCm()));
    }

    @Test
    void unpersistedSnapshotCannotBeExposedAsGeometry() {
        assertThrows(NullPointerException.class, () -> StageGeometry.from(persisted(null)));
    }

    @Test
    void geometryCollectionsCannotBeModifiedFromOutside() {
        StageGeometry geometry = StageGeometry.from(persisted(77L));

        assertThrows(UnsupportedOperationException.class, () -> geometry.elements().add(null));
        assertThrows(UnsupportedOperationException.class, () -> geometry.accesses().add(null));
    }

    private static StageSnapshot persisted(Long id) {
        StageBoundary rectangle = new StageBoundary(List.of(
                new StageBoundaryPoint(bd(0), bd(0)),
                new StageBoundaryPoint(bd(800), bd(0)),
                new StageBoundaryPoint(bd(800), bd(600)),
                new StageBoundaryPoint(bd(0), bd(600))));
        return new StageSnapshot(id, 1L, 2L, 3L, 10L, "Escenario", bd(800), bd(600), rectangle,
                List.of(), List.of(new StageElement("Escalón", StageElementType.STEP,
                        bd(100), bd(100), bd(200), bd(30), bd(15))),
                LocalDateTime.of(2026, 11, 26, 10, 0));
    }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value);
    }
}
