package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reglas geométricas del escenario. Unidad: centímetros. Toda la comprobación se hace con fracciones
 * exactas ({@link Q}, sobre {@link BigInteger}): no hay redondeos ni aproximaciones de punto flotante.
 *
 * <p>Pertenencia de un elemento rectangular R al escenario P (polígono simple). Se cumple si:</p>
 * <ol>
 *   <li>cada aresta de R queda en P cerrado: se divide cada aresta de R en los puntos donde toca o corta
 *       aristas de P (vértices de P sobre la arista, cortes propios) y el punto medio de cada subsegmento
 *       está dentro o sobre P;</li>
 *   <li>ninguna arista de P entra en el interior abierto de R: se divide cada arista de P en los puntos
 *       donde toca o corta aristas de R (esquinas de R sobre la arista, cortes propios) y ningún punto
 *       medio de subsegmento queda estrictamente dentro de R.</li>
 * </ol>
 * <p>Si ambas condiciones se cumplen, el interior de R no contiene exterior de P. Las esquinas de R
 * deben estar dentro o sobre P; la condición 1 las incluye. Los contactos colineales y tangenciales se
 * resuelven por el mismo mecanismo de subdivisión, sin casos particulares.</p>
 */
public final class StageGeometryValidator {

    private StageGeometryValidator() {
    }

    public static void validate(
            BigDecimal widthCm,
            BigDecimal depthCm,
            StageBoundary boundary,
            List<StageAccess> accesses,
            List<StageElement> elements) {
        validateVerticesWithinDimensions(widthCm, depthCm, boundary);
        List<Pt> polygon = toPoints(boundary);
        validateSimplePolygon(polygon);
        for (StageAccess access : accesses) {
            validateAccessOnContour(access, polygon);
        }
        for (StageElement element : elements) {
            validateElementWithinStage(element, polygon, widthCm, depthCm);
        }
        validateNoOverlaps(elements);
    }

    // ------------------------------------------------------------------ reglas

    private static void validateVerticesWithinDimensions(
            BigDecimal widthCm, BigDecimal depthCm, StageBoundary boundary) {
        for (StageBoundaryPoint vertex : boundary.vertices()) {
            if (vertex.x().signum() < 0 || vertex.x().compareTo(widthCm) > 0
                    || vertex.y().signum() < 0 || vertex.y().compareTo(depthCm) > 0) {
                throw new StageGeometryException("Un vértice del contorno queda fuera de las dimensiones del escenario");
            }
        }
    }

    private static void validateSimplePolygon(List<Pt> v) {
        int n = v.size();
        if (shoelace(v).signum() == 0) {
            throw new StageGeometryException("El contorno tiene área nula");
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int k = (j + 1) % n;
            if (cross(v.get(i), v.get(j), v.get(k)).signum() == 0
                    && dot(v.get(j), v.get(i), v.get(k)).signum() > 0) {
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

    private static void validateAccessOnContour(StageAccess access, List<Pt> polygon) {
        Pt start = Pt.of(access.start());
        Pt end = Pt.of(access.end());
        for (int i = 0; i < polygon.size(); i++) {
            Pt a = polygon.get(i);
            Pt b = polygon.get((i + 1) % polygon.size());
            if (onSegment(a, b, start) && onSegment(a, b, end)) {
                return;
            }
        }
        throw new StageGeometryException("El acceso debe estar sobre una arista del contorno");
    }

    private static void validateElementWithinStage(
            StageElement element, List<Pt> polygon, BigDecimal widthCm, BigDecimal depthCm) {
        BigDecimal x1 = element.x().add(element.widthCm());
        BigDecimal y1 = element.y().add(element.depthCm());
        if (element.x().signum() < 0 || x1.compareTo(widthCm) > 0
                || element.y().signum() < 0 || y1.compareTo(depthCm) > 0) {
            throw new StageGeometryException("El elemento " + element.name() + " queda fuera del escenario");
        }
        List<Pt> rectangle = rectangle(element);
        for (Pt corner : rectangle) {
            if (!insideOrOn(polygon, corner)) {
                throw new StageGeometryException("El elemento " + element.name() + " queda fuera del contorno");
            }
        }
        for (int i = 0; i < 4; i++) {
            Pt a = rectangle.get(i);
            Pt b = rectangle.get((i + 1) % 4);
            for (Pt mid : midpoints(a, b, cutsOnSegment(a, b, polygon))) {
                if (!insideOrOn(polygon, mid)) {
                    throw new StageGeometryException("El elemento " + element.name() + " cruza el contorno");
                }
            }
        }
        for (int i = 0; i < polygon.size(); i++) {
            Pt u = polygon.get(i);
            Pt w = polygon.get((i + 1) % polygon.size());
            for (Pt mid : midpoints(u, w, cutsOnPolygonEdge(u, w, rectangle))) {
                if (strictlyInside(rectangle, mid)) {
                    throw new StageGeometryException("El contorno invade la superficie del elemento " + element.name());
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
        BigDecimal overlapX = min(a.x().add(a.widthCm()), b.x().add(b.widthCm())).subtract(max(a.x(), b.x()));
        BigDecimal overlapY = min(a.y().add(a.depthCm()), b.y().add(b.depthCm())).subtract(max(a.y(), b.y()));
        return overlapX.signum() > 0 && overlapY.signum() > 0;
    }

    // ------------------------------------------------------------ subdivisión

    /** Puntos de corte sobre el segmento ab: extremos, vértices de P sobre ab y cortes propios con P. */
    private static List<Pt> cutsOnSegment(Pt a, Pt b, List<Pt> polygon) {
        List<Pt> cuts = new ArrayList<>();
        cuts.add(a);
        cuts.add(b);
        for (Pt v : polygon) {
            if (onSegment(a, b, v)) {
                cuts.add(v);
            }
        }
        for (int j = 0; j < polygon.size(); j++) {
            Pt u = polygon.get(j);
            Pt w = polygon.get((j + 1) % polygon.size());
            if (properIntersection(a, b, u, w)) {
                cuts.add(intersectionPoint(a, b, u, w));
            }
        }
        return cuts;
    }

    /** Puntos de corte sobre la arista uw de P: extremos, esquinas de R sobre uw y cortes propios con R. */
    private static List<Pt> cutsOnPolygonEdge(Pt u, Pt w, List<Pt> rectangle) {
        List<Pt> cuts = new ArrayList<>();
        cuts.add(u);
        cuts.add(w);
        for (Pt corner : rectangle) {
            if (onSegment(u, w, corner)) {
                cuts.add(corner);
            }
        }
        for (int i = 0; i < 4; i++) {
            Pt a = rectangle.get(i);
            Pt b = rectangle.get((i + 1) % 4);
            if (properIntersection(u, w, a, b)) {
                cuts.add(intersectionPoint(u, w, a, b));
            }
        }
        return cuts;
    }

    /** Ordena los cortes a lo largo de ab, elimina repetidos y devuelve los puntos medios de cada tramo. */
    private static List<Pt> midpoints(Pt a, Pt b, List<Pt> cuts) {
        Q dx = b.x().sub(a.x());
        Q dy = b.y().sub(a.y());
        List<Pt> sorted = new ArrayList<>(cuts);
        sorted.sort(Comparator.comparing(p -> p.x().sub(a.x()).mul(dx).add(p.y().sub(a.y()).mul(dy)),
                Q::compareTo));
        List<Pt> mids = new ArrayList<>();
        Pt previous = null;
        for (Pt current : sorted) {
            if (previous != null && !previous.sameAs(current)) {
                mids.add(midpoint(previous, current));
            }
            if (previous == null || !previous.sameAs(current)) {
                previous = current;
            }
        }
        return mids;
    }

    private static Pt midpoint(Pt p, Pt q) {
        Q two = Q.of(2);
        return new Pt(p.x().add(q.x()).div(two), p.y().add(q.y()).div(two));
    }

    // -------------------------------------------------------------- predicados

    private static List<Pt> rectangle(StageElement e) {
        Q x0 = Q.of(e.x());
        Q y0 = Q.of(e.y());
        Q x1 = Q.of(e.x().add(e.widthCm()));
        Q y1 = Q.of(e.y().add(e.depthCm()));
        return List.of(new Pt(x0, y0), new Pt(x1, y0), new Pt(x1, y1), new Pt(x0, y1));
    }

    private static boolean strictlyInside(List<Pt> rectangle, Pt p) {
        Pt min = rectangle.get(0);
        Pt max = rectangle.get(2);
        return min.x().cmp(p.x()) < 0 && p.x().cmp(max.x()) < 0
                && min.y().cmp(p.y()) < 0 && p.y().cmp(max.y()) < 0;
    }

    private static boolean areAdjacent(int i, int j, int n) {
        return j == (i + 1) % n || i == (j + 1) % n;
    }

    private static Q shoelace(List<Pt> v) {
        Q sum = Q.ZERO;
        for (int i = 0; i < v.size(); i++) {
            Pt a = v.get(i);
            Pt b = v.get((i + 1) % v.size());
            sum = sum.add(a.x().mul(b.y()).sub(b.x().mul(a.y())));
        }
        return sum;
    }

    /** Producto vectorial (a - o) x (b - o): 0 si colineales, positivo si b gira a la izquierda de oa. */
    private static Q cross(Pt o, Pt a, Pt b) {
        return a.x().sub(o.x()).mul(b.y().sub(o.y())).sub(a.y().sub(o.y()).mul(b.x().sub(o.x())));
    }

    /** Producto escalar (a - o) . (b - o). */
    private static Q dot(Pt o, Pt a, Pt b) {
        return a.x().sub(o.x()).mul(b.x().sub(o.x())).add(a.y().sub(o.y()).mul(b.y().sub(o.y())));
    }

    private static boolean onSegment(Pt a, Pt b, Pt p) {
        return cross(a, b, p).signum() == 0
                && between(p.x(), a.x(), b.x()) && between(p.y(), a.y(), b.y());
    }

    private static boolean between(Q value, Q a, Q b) {
        return value.cmp(min(a, b)) >= 0 && value.cmp(max(a, b)) <= 0;
    }

    /** Intersección cerrada: incluye contacto en extremos y solapes colineales. */
    private static boolean segmentsIntersect(Pt p1, Pt p2, Pt q1, Pt q2) {
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

    /** Cruce propio: los extremos de cada segmento están en lados opuestos del otro, sin contacto. */
    private static boolean properIntersection(Pt p1, Pt p2, Pt q1, Pt q2) {
        int d1 = cross(p1, p2, q1).signum();
        int d2 = cross(p1, p2, q2).signum();
        int d3 = cross(q1, q2, p1).signum();
        int d4 = cross(q1, q2, p2).signum();
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    /** Punto de corte de dos segmentos que se cruzan propiamente: p1 + (p2 - p1) * t. */
    private static Pt intersectionPoint(Pt p1, Pt p2, Pt q1, Pt q2) {
        Q rx = p2.x().sub(p1.x());
        Q ry = p2.y().sub(p1.y());
        Q sx = q2.x().sub(q1.x());
        Q sy = q2.y().sub(q1.y());
        Q denominator = rx.mul(sy).sub(ry.mul(sx));
        Q numerator = q1.x().sub(p1.x()).mul(sy).sub(q1.y().sub(p1.y()).mul(sx));
        Q t = numerator.div(denominator);
        return new Pt(p1.x().add(rx.mul(t)), p1.y().add(ry.mul(t)));
    }

    /** Punto dentro o sobre P: regla par-impar exacta, con el borde incluido. */
    private static boolean insideOrOn(List<Pt> v, Pt p) {
        int n = v.size();
        for (int i = 0; i < n; i++) {
            if (onSegment(v.get(i), v.get((i + 1) % n), p)) {
                return true;
            }
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Pt a = v.get(i);
            Pt b = v.get(j);
            if (a.y().cmp(p.y()) > 0 != b.y().cmp(p.y()) > 0) {
                Q lhs = p.x().sub(a.x()).mul(b.y().sub(a.y()));
                Q rhs = b.x().sub(a.x()).mul(p.y().sub(a.y()));
                boolean crossesRight = b.y().cmp(a.y()) > 0 ? lhs.cmp(rhs) < 0 : lhs.cmp(rhs) > 0;
                if (crossesRight) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static List<Pt> toPoints(StageBoundary boundary) {
        List<Pt> points = new ArrayList<>();
        for (StageBoundaryPoint vertex : boundary.vertices()) {
            points.add(Pt.of(vertex));
        }
        return points;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static Q min(Q a, Q b) {
        return a.cmp(b) <= 0 ? a : b;
    }

    private static Q max(Q a, Q b) {
        return a.cmp(b) >= 0 ? a : b;
    }

    // ----------------------------------------------------------- aritmética

    /** Punto exacto con coordenadas racionales. */
    private record Pt(Q x, Q y) {

        static Pt of(StageBoundaryPoint p) {
            return new Pt(Q.of(p.x()), Q.of(p.y()));
        }

        boolean sameAs(Pt other) {
            return x.cmp(other.x) == 0 && y.cmp(other.y) == 0;
        }
    }

    /** Fracción exacta n/d con d > 0 y reducida. */
    private record Q(BigInteger n, BigInteger d) implements Comparable<Q> {

        static final Q ZERO = new Q(BigInteger.ZERO, BigInteger.ONE);

        static Q of(BigDecimal v) {
            int scale = v.scale();
            BigInteger numerator = v.unscaledValue();
            BigInteger denominator = BigInteger.ONE;
            if (scale >= 0) {
                denominator = BigInteger.TEN.pow(scale);
            } else {
                numerator = numerator.multiply(BigInteger.TEN.pow(-scale));
            }
            return normalized(numerator, denominator);
        }

        static Q of(long value) {
            return normalized(BigInteger.valueOf(value), BigInteger.ONE);
        }

        private static Q normalized(BigInteger numerator, BigInteger denominator) {
            BigInteger nn = numerator;
            BigInteger dd = denominator;
            if (dd.signum() < 0) {
                nn = nn.negate();
                dd = dd.negate();
            }
            BigInteger g = nn.gcd(dd);
            if (!g.equals(BigInteger.ONE) && g.signum() != 0) {
                nn = nn.divide(g);
                dd = dd.divide(g);
            }
            return new Q(nn, dd);
        }

        Q add(Q o) {
            return normalized(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d));
        }

        Q sub(Q o) {
            return normalized(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d));
        }

        Q mul(Q o) {
            return normalized(n.multiply(o.n), d.multiply(o.d));
        }

        Q div(Q o) {
            return normalized(n.multiply(o.d), d.multiply(o.n));
        }

        int signum() {
            return n.signum();
        }

        int cmp(Q o) {
            return n.multiply(o.d).compareTo(o.n.multiply(d));
        }

        @Override
        public int compareTo(Q o) {
            return cmp(o);
        }
    }
}
