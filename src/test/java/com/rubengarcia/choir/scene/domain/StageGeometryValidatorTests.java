package com.rubengarcia.choir.scene.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageGeometryValidatorTests {

    private static final long CHOIR_ID = 1L;

    @Test
    void validRectangularStageWithAllElementTypesIsAccepted() {
        assertDoesNotThrow(() -> template(
                rectangle(800, 600),
                List.of(access("Puerta", p(300, 0), p(400, 0))),
                List.of(
                        element("Banco", StageElementType.BENCH, 100, 100, 200, 40, 0),
                        element("Piano", StageElementType.PIANO, 400, 300, 120, 100, 0),
                        element("Mueble", StageElementType.FURNITURE, 600, 100, 100, 60, 0),
                        element("Pilar", StageElementType.OBSTACLE, 350, 450, 30, 30, 0),
                        element("Escalón", StageElementType.STEP, 100, 500, 200, 30, 10),
                        element("Plataforma", StageElementType.PLATFORM, 400, 520, 200, 60, 40))));
    }

    @Test
    void nonPositiveDimensionsAreRejected() {
        assertThrows(StageGeometryException.class, () -> new StageTemplate(
                null, CHOIR_ID, "Escenario", BigDecimal.ZERO, new BigDecimal("600"),
                rectangle(800, 600), List.of(), List.of(), false));
    }

    @Test
    void contourWithFewerThanThreeVerticesIsRejected() {
        assertThrows(StageGeometryException.class,
                () -> new StageBoundary(List.of(p(0, 0), p(100, 0))));
    }

    @Test
    void selfIntersectingContourIsRejected() {
        // Área no nula: la diagonal (0,0)-(300,300) corta la arista (300,0)-(0,200) en (120,120).
        StageGeometryException ex = null;
        try {
            template(new StageBoundary(List.of(p(0, 0), p(300, 300), p(300, 0), p(0, 200))),
                    List.of(), List.of());
        } catch (StageGeometryException error) {
            ex = error;
        }
        assertTrue(ex != null && ex.getMessage().contains("autointersectado"),
                "debe rechazarse por autointersección, no por otra regla");
    }

    @Test
    void zeroAreaContourIsRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                new StageBoundary(List.of(p(0, 0), p(100, 0), p(200, 0))),
                List.of(), List.of()));
    }

    @Test
    void verticesOutsideDimensionsAreRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                new StageBoundary(List.of(p(0, 0), p(900, 0), p(800, 600), p(0, 600))),
                List.of(), List.of()));
    }

    @Test
    void elementOutsideTheStageIsRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                rectangle(800, 600), List.of(),
                List.of(element("Banco", StageElementType.BENCH, 700, 100, 200, 40, 0))));
    }

    @Test
    void overlappingSurfacesAreRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                rectangle(800, 600), List.of(),
                List.of(
                        element("Banco", StageElementType.BENCH, 100, 100, 200, 40, 0),
                        element("Piano", StageElementType.PIANO, 150, 120, 100, 100, 0))));
    }

    @Test
    void adjacentSurfacesAreAccepted() {
        assertDoesNotThrow(() -> template(
                rectangle(800, 600), List.of(),
                List.of(
                        element("Banco", StageElementType.BENCH, 100, 100, 200, 40, 0),
                        element("Mueble", StageElementType.FURNITURE, 300, 100, 100, 40, 0))));
    }

    @Test
    void elementTouchingTheWallIsAccepted() {
        assertDoesNotThrow(() -> template(
                rectangle(800, 600), List.of(),
                List.of(element("Banco", StageElementType.BENCH, 0, 100, 200, 40, 0))));
    }

    @Test
    void stepWithoutElevationIsRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                rectangle(800, 600), List.of(),
                List.of(element("Escalón", StageElementType.STEP, 100, 100, 200, 30, 0))));
    }

    @Test
    void negativeElevationIsRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                rectangle(800, 600), List.of(),
                List.of(element("Escalón", StageElementType.STEP, 100, 100, 200, 30, -5))));
    }

    @Test
    void elevationOnNonElevatedTypeIsRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                rectangle(800, 600), List.of(),
                List.of(element("Banco", StageElementType.BENCH, 100, 100, 200, 40, 10))));
    }

    @Test
    void stepsAndPlatformsWithDifferentElevationsAreAccepted() {
        assertDoesNotThrow(() -> template(
                rectangle(800, 600), List.of(),
                List.of(
                        element("Escalón 1", StageElementType.STEP, 100, 100, 200, 30, 10),
                        element("Escalón 2", StageElementType.STEP, 100, 130, 200, 30, 20),
                        element("Plataforma", StageElementType.PLATFORM, 400, 100, 200, 60, 40))));
    }

    @Test
    void accessNotOnTheContourIsRejected() {
        assertThrows(StageGeometryException.class, () -> template(
                rectangle(800, 600),
                List.of(access("Interior", p(100, 100), p(200, 100))),
                List.of()));
    }

    @Test
    void accessOnTheContourIsAccepted() {
        assertDoesNotThrow(() -> template(
                rectangle(800, 600),
                List.of(access("Puerta", p(300, 0), p(400, 0))),
                List.of()));
    }

    @Test
    void elementCrossingAConcavityOfTheContourIsRejected() {
        StageBoundary lShape = new StageBoundary(List.of(
                p(0, 0), p(400, 0), p(400, 200), p(200, 200), p(200, 400), p(0, 400)));

        assertThrows(StageGeometryException.class, () -> template(
                lShape, List.of(),
                List.of(element("Diagonal", StageElementType.OBSTACLE, 100, 100, 200, 200, 0))));
    }

    // ------------------------------------------------------------------

    private static StageTemplate template(
            StageBoundary boundary, List<StageAccess> accesses, List<StageElement> elements) {
        return new StageTemplate(null, CHOIR_ID, "Escenario", new BigDecimal("800"),
                new BigDecimal("600"), boundary, accesses, elements, false);
    }

    private static StageBoundary rectangle(int width, int depth) {
        return new StageBoundary(List.of(p(0, 0), p(width, 0), p(width, depth), p(0, depth)));
    }

    private static StageBoundaryPoint p(int x, int y) {
        return new StageBoundaryPoint(new BigDecimal(x), new BigDecimal(y));
    }

    private static StageAccess access(String name, StageBoundaryPoint start, StageBoundaryPoint end) {
        return new StageAccess(name, start, end);
    }

    private static StageElement element(
            String name, StageElementType type, int x, int y, int width, int depth, int elevation) {
        return new StageElement(name, type, new BigDecimal(x), new BigDecimal(y),
                new BigDecimal(width), new BigDecimal(depth), new BigDecimal(elevation));
    }
}
