package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MobilityRegionTests {

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void acceptsRegionAndKeepsEveryBound() {
        MobilityRegion region = new MobilityRegion(n("1.5"), n("4.25"), n("0"), n("10"));

        assertEquals(n("1.5"), region.minX());
        assertEquals(n("4.25"), region.maxX());
        assertEquals(n("0"), region.minY());
        assertEquals(n("10"), region.maxY());
    }

    @Test
    void acceptsTheFullScaleAndEveryBoundaryValue() {
        assertDoesNotThrow(() -> new MobilityRegion(n("0"), n("100"), n("0"), n("100")));
        assertDoesNotThrow(() -> new MobilityRegion(n("0"), n("0.001"), n("99.999"), n("100")));
    }

    @Test
    void rejectsNullBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(null, n("10"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), null, n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), null, n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("0"), null));
    }

    @Test
    void rejectsValuesBelowZeroOnEachBound() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("-0.001"), n("10"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("-0.001"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("-1"), n("10"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("-1"), n("10")));
    }

    @Test
    void rejectsValuesAboveHundredOnEachBound() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("100.001"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("0"), n("100.001")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("101"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("0"), n("101")));
    }

    @Test
    void rejectsEqualOrInvertedXBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("5"), n("5"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("6"), n("5"), n("0"), n("10")));
    }

    @Test
    void rejectsEqualOrInvertedYBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("5"), n("5")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("10"), n("6"), n("5")));
    }

    @Test
    void rejectsEqualXAtTheScaleEdges() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("0"), n("0"), n("0"), n("10")));
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("100"), n("100"), n("0"), n("10")));
    }

    @Test
    void comparesDecimalsByValueNotByScale() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("5.0"), n("5.000"), n("0"), n("10")));
    }
}
