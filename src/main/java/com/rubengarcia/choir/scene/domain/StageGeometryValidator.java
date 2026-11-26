package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reglas geométricas del escenario. Aritmética exacta con {@link BigDecimal}: sin redondeos ni
 * aproximaciones de punto flotante. Unidad: centímetros.
 *
 * <p>Limitación conocida: la comprobación de superficies dentro del contorno cubre esquinas y cruces
 * propios de aristas. Un elemento que toque exactamente un vértice cóncavo sin cruzar aristas se
 * acepta; es un caso de borde que se documenta en CHOIR-153.</p>
 */
public final class StageGeometryValidator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private StageGeometryValidator() {
    }

    public static void validate(
            BigDecimal widthCm,
            BigDecimal depthCm,
            StageBoundary boundary,
            List<StageAccess> accesses,
            List<StageElement> elements) {
        validateVerticesWithinDimensions(widthCm, depthCm, boundary);
        validateSimplePolygon(boundary);
        for (StageAccess access : accesses) {
            validateAccessOnContour(access, boundary);
        }
        for (StageElement element : elements) {
            validateElementWithinStage(element, boundary, widthCm, depthCm);
        }
        validateNoOverlaps(elements);
    }

    private static void validateVerticesWithinDimensions(
            BigDecimal widthCm, BigDecimal depthCm, StageBoundary boundary) {
        for (StageBoundaryPoint vertex : boundary.vertices()) {
            if (vertex.x().compareTo(ZERO) < 0 || vertex.x().compareTo(widthCm) > 0
                    || vertex.y().compareTo(ZERO) < 0 || vertex.y().compareTo(depthCm) > 0) {
                throw new StageGeometryException("Un vértice del contorno queda fuera de las dimensiones del escenario");
            }
        }
    }

    private static void validateSimplePolygon(StageBoundary boundary) {
        List<StageBoundaryPoint> v = boundary.vertices();
        int n = v.size();
        if (shoelace(v).signum() == 0) {
            throw new StageGeometryException("El contorno tiene área nula");
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int k = (j + 1) % n;
            if (cross(v.get(i), v.get(j), v.get(k)).signum() == 0
                    && dot(v.get(i), v.get(j), v.get(k)).signum() > 0) {
                throw new StageGeometryException("El contorno se superpone consigo mismo en una arista consecutiva");
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (areAdjacent(i, j, n)) {
                    continue;
                }
                if (segmentsIntersect(v.get(i), v.get((i + 1) % n), v.get(j), v.get((j + 1) % n))) {
                    throw new StageGeometryException("El contorno es autointersectado");
                }
            }
        }
    }

    private static void validateAccessOnContour(StageAccess access, StageBoundary boundary) {
        List<StageBoundaryPoint> v = boundary.vertices();
        for (int i = 0; i < v.size(); i++) {
            StageBoundaryPoint a = v.get(i);
            StageBoundaryPoint b = v.get((i + 1) % v.size());
            if (onSegment(a, b, access.start()) && onSegment(a, b, access.end())) {
                return;
            }
        }
        throw new StageGeometryException("El acceso debe estar sobre una arista del contorno");
    }

    private static void validateElementWithinStage(
            StageElement element, StageBoundary boundary, BigDecimal widthCm, BigDecimal depthCm) {
        List<StageBoundaryPoint> corners = corners(element);
        for (StageBoundaryPoint corner : corners) {
            if (corner.x().compareTo(ZERO) < 0 || corner.x().compareTo(widthCm) > 0
                    || corner.y().compareTo(ZERO) < 0 || corner.y().compareTo(depthCm) > 0) {
                throw new StageGeometryException("El elemento " + element.name() + " queda fuera del escenario");
            }
            if (!insideOrOn(boundary.vertices(), corner)) {
                throw new StageGeometryException("El elemento " + element.name() + " queda fuera del contorno");
            }
        }
        List<StageBoundaryPoint> v = boundary.vertices();
        for (int i = 0; i < corners.size(); i++) {
            StageBoundaryPoint a = corners.get(i);
            StageBoundaryPoint b = corners.get((i + 1) % corners.size());
            for (int j = 0; j < v.size(); j++) {
                if (properIntersection(a, b, v.get(j), v.get((j + 1) % v.size()))) {
                    throw new StageGeometryException("El elemento " + element.name() + " cruza el contorno");
                }
            }
        }
    }

    private static void validateNoOverlaps(List<StageElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            for (int j = i + 1; j < elements.size(); j++) {
                if (overlapsWithArea(elements.get(i), elements.get(j))) {
                    throw new StageGeometryException("Las superficies de "
                            + elements.get(i).name() + " y " + elements.get(j).name() + " se solapan");
                }
            }
        }
    }

    private static boolean overlapsWithArea(StageElement a, StageElement b) {
        BigDecimal overlapX = min(a.x().add(a.widthCm()), b.x().add(b.widthCm()))
                .subtract(max(a.x(), b.x()));
        BigDecimal overlapY = min(a.y().add(a.depthCm()), b.y().add(b.depthCm()))
                .subtract(max(a.y(), b.y()));
        return overlapX.signum() > 0 && overlapY.signum() > 0;
    }

    private static List<StageBoundaryPoint> corners(StageElement e) {
        BigDecimal x1 = e.x().add(e.widthCm());
        BigDecimal y1 = e.y().add(e.depthCm());
        return List.of(
                new StageBoundaryPoint(e.x(), e.y()),
                new StageBoundaryPoint(x1, e.y()),
                new StageBoundaryPoint(x1, y1),
                new StageBoundaryPoint(e.x(), y1));
    }

    private static boolean areAdjacent(int i, int j, int n) {
        return j == (i + 1) % n || i == (j + 1) % n;
    }

    private static BigDecimal shoelace(List<StageBoundaryPoint> v) {
        BigDecimal sum = ZERO;
        for (int i = 0; i < v.size(); i++) {
            StageBoundaryPoint a = v.get(i);
            StageBoundaryPoint b = v.get((i + 1) % v.size());
            sum = sum.add(a.x().multiply(b.y()).subtract(b.x().multiply(a.y())));
        }
        return sum;
    }

    /** Producto vectorial (a - o) x (b - o). Signo: 0 colineal, positivo antihorario. */
    private static BigDecimal cross(StageBoundaryPoint o, StageBoundaryPoint a, StageBoundaryPoint b) {
        return a.x().subtract(o.x()).multiply(b.y().subtract(o.y()))
                .subtract(a.y().subtract(o.y()).multiply(b.x().subtract(o.x())));
    }

    /** Producto escalar (a - o) . (b - o). */
    private static BigDecimal dot(StageBoundaryPoint o, StageBoundaryPoint a, StageBoundaryPoint b) {
        return a.x().subtract(o.x()).multiply(b.x().subtract(o.x()))
                .add(a.y().subtract(o.y()).multiply(b.y().subtract(o.y())));
    }

    private static boolean onSegment(StageBoundaryPoint a, StageBoundaryPoint b, StageBoundaryPoint p) {
        return cross(a, b, p).signum() == 0
                && p.x().compareTo(min(a.x(), b.x())) >= 0 && p.x().compareTo(max(a.x(), b.x())) <= 0
                && p.y().compareTo(min(a.y(), b.y())) >= 0 && p.y().compareTo(max(a.y(), b.y())) <= 0;
    }

    /** Intersección cerrada: incluye contacto en extremos y solapes colineales. */
    private static boolean segmentsIntersect(
            StageBoundaryPoint p1, StageBoundaryPoint p2, StageBoundaryPoint q1, StageBoundaryPoint q2) {
        int d1 = cross(p1, p2, q1).signum();
        int d2 = cross(p1, p2, q2).signum();
        int d3 = cross(q1, q2, p1).signum();
        int d4 = cross(q1, q2, p2).signum();
        if (d1 * d2 < 0 && d3 * d4 < 0) {
            return true;
        }
        return (d1 == 0 && onSegment(p1, p2, q1))
                || (d2 == 0 && onSegment(p1, p2, q2))
                || (d3 == 0 && onSegment(q1, q2, p1))
                || (d4 == 0 && onSegment(q1, q2, p2));
    }

    /** Cruce propio: los extremos están en lados opuestos de ambas rectas, sin contacto. */
    private static boolean properIntersection(
            StageBoundaryPoint p1, StageBoundaryPoint p2, StageBoundaryPoint q1, StageBoundaryPoint q2) {
        int d1 = cross(p1, p2, q1).signum();
        int d2 = cross(p1, p2, q2).signum();
        int d3 = cross(q1, q2, p1).signum();
        int d4 = cross(q1, q2, p2).signum();
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    /** Punto dentro o sobre el polígono (regla par-impar exacta, con borde incluido). */
    private static boolean insideOrOn(List<StageBoundaryPoint> v, StageBoundaryPoint p) {
        int n = v.size();
        for (int i = 0; i < n; i++) {
            if (onSegment(v.get(i), v.get((i + 1) % n), p)) {
                return true;
            }
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            StageBoundaryPoint a = v.get(i);
            StageBoundaryPoint b = v.get(j);
            if ((a.y().compareTo(p.y()) > 0) != (b.y().compareTo(p.y()) > 0)) {
                BigDecimal lhs = p.x().subtract(a.x()).multiply(b.y().subtract(a.y()));
                BigDecimal rhs = b.x().subtract(a.x()).multiply(p.y().subtract(a.y()));
                boolean crossesRight = b.y().compareTo(a.y()) > 0 ? lhs.compareTo(rhs) < 0 : lhs.compareTo(rhs) > 0;
                if (crossesRight) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
