package com.rubengarcia.choir.formation.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FormationConstraintModelTests {

    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("0"), new BigDecimal("10"),
            new BigDecimal("0"), new BigDecimal("10"));

    @Test
    void mobilityConstraintRequiresFormationSingerAndRegion() {
        assertThrows(IllegalArgumentException.class, () ->
                new FormationMobilityConstraint(null, null, 2L, REGION, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                new FormationMobilityConstraint(null, 1L, null, REGION, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                new FormationMobilityConstraint(null, 1L, 2L, null, true, null));
    }

    @Test
    void mobilityConstraintAllowsMissingPersistenceFieldsBeforeSaving() {
        FormationMobilityConstraint constraint =
                new FormationMobilityConstraint(null, 1L, 2L, REGION, false, null);

        assertFalse(constraint.active());
        assertEquals(null, constraint.id());
    }

    @Test
    void visibilityConstraintRequiresFormationAndProtectedSinger() {
        assertThrows(IllegalArgumentException.class, () ->
                new FormationVisibilityConstraint(null, null, 2L, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                new FormationVisibilityConstraint(null, 1L, null, true, null));
    }

    @Test
    void mobilityAndVisibilityModelsDoNotShareFields() {
        List<String> mobility = componentNames(FormationMobilityConstraint.class);
        List<String> visibility = componentNames(FormationVisibilityConstraint.class);

        assertTrue(mobility.contains("region"));
        assertFalse(mobility.contains("protectedSingerId"));
        assertTrue(visibility.contains("protectedSingerId"));
        assertFalse(visibility.contains("region"));
        assertFalse(visibility.stream().anyMatch(name -> name.toLowerCase().contains("height")));
        assertFalse(visibility.stream().anyMatch(name -> name.toLowerCase().contains("orientation")));
    }

    @Test
    void mobilityRegionHasOnlyTheFourRectangularBoundsAndNoZCoordinate() {
        assertEquals(List.of("minX", "maxX", "minY", "maxY"),
                componentNames(MobilityRegion.class));
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toList());
    }
}
