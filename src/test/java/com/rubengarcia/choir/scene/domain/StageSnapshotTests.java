package com.rubengarcia.choir.scene.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageSnapshotTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);

    @Test
    void snapshotKeepsTheFullGeometryAndIsNotAffectedByLaterTemplateChanges() {
        StageTemplate original = template(10L, "Escenario", 800, false);
        StageSnapshot snapshot = StageSnapshot.of(original, 2L, 3L, NOW);

        StageTemplate changed = new StageTemplate(10L, 1L, "Otro escenario",
                new BigDecimal("900"), new BigDecimal("700"),
                original.boundary(), List.of(), List.of(), false);

        assertEquals("Escenario", snapshot.name());
        assertEquals(new BigDecimal("800"), snapshot.widthCm());
        assertEquals(10L, snapshot.sourceTemplateId());
        assertEquals("Otro escenario", changed.name());
    }

    @Test
    void snapshotPreservesStepAndPlatformElevations() {
        StageTemplate template = new StageTemplate(10L, 1L, "Escenario",
                new BigDecimal("800"), new BigDecimal("600"),
                rectangle(800, 600), List.of(), List.of(
                        new StageElement("Escalón", StageElementType.STEP, bd(100), bd(100),
                                bd(200), bd(30), bd(15)),
                        new StageElement("Plataforma", StageElementType.PLATFORM, bd(400), bd(100),
                                bd(200), bd(60), bd(40))),
                false);

        StageSnapshot snapshot = StageSnapshot.of(template, 2L, 3L, NOW);

        assertEquals(0, bd(15).compareTo(snapshot.elements().get(0).elevationCm()));
        assertEquals(0, bd(40).compareTo(snapshot.elements().get(1).elevationCm()));
    }

    @Test
    void unpersistedTemplateCannotBeSnapshotted() {
        assertThrows(NullPointerException.class,
                () -> StageSnapshot.of(template(null, "Escenario", 800, false), 2L, 3L, NOW));
    }

    @Test
    void archivedTemplateCannotBeSnapshotted() {
        assertThrows(IllegalStateException.class,
                () -> StageSnapshot.of(template(10L, "Escenario", 800, true), 2L, 3L, NOW));
    }

    @Test
    void snapshotRejectsNonPositiveIdentifiers() {
        assertThrows(StageGeometryException.class,
                () -> StageSnapshot.of(template(10L, "Escenario", 800, false), 0L, 3L, NOW));
    }

    @Test
    void archiveReturnsACopyWithoutChangingTheOriginal() {
        StageTemplate active = template(10L, "Escenario", 800, false);

        StageTemplate archived = active.archive();

        assertEquals(false, active.archived());
        assertEquals(true, archived.archived());
        assertEquals(active.name(), archived.name());
    }

    @Test
    void snapshotListsCannotBeModifiedFromOutside() {
        StageSnapshot snapshot = StageSnapshot.of(template(10L, "Escenario", 800, false), 2L, 3L, NOW);

        assertThrows(UnsupportedOperationException.class, () -> snapshot.elements().add(null));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.accesses().add(null));
    }

    private static StageTemplate template(Long id, String name, int width, boolean archived) {
        return new StageTemplate(id, 1L, name, bd(width), bd(600),
                rectangle(width, 600), List.of(), List.of(), archived);
    }

    private static StageBoundary rectangle(int width, int depth) {
        return new StageBoundary(List.of(
                new StageBoundaryPoint(bd(0), bd(0)),
                new StageBoundaryPoint(bd(width), bd(0)),
                new StageBoundaryPoint(bd(width), bd(depth)),
                new StageBoundaryPoint(bd(0), bd(depth))));
    }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value);
    }
}
