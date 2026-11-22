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
    void acceptsRegionWithAllBoundsAndKeepsThem() {
        MobilityRegion region = new MobilityRegion(n("1.5"), n("4.25"), n("-2"), n("0"));

        assertEquals(n("1.5"), region.minX());
        assertEquals(n("4.25"), region.maxX());
        assertEquals(n("-2"), region.minY());
        assertEquals(n("0"), region.maxY());
    }

    @Test
    void acceptsBoundsOutsideAnyAssumedRangeBecauseNoRangeIsDefined() {
        assertDoesNotThrow(() -> new MobilityRegion(
                n("-100000.001"), n("100000.999"), n("-0.001"), n("0.001")));
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
    void comparesDecimalsByValueNotByScale() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobilityRegion(n("5.0"), n("5.000"), n("0"), n("10")));
    }
}
