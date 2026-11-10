package com.rubengarcia.choir.transition.domain;

public record ConcertOptimizationPreferences(
        int distance,
        int crossings,
        int compactness,
        int sectionContinuity) {

    public static final int DEFAULT_WEIGHT = 25;

    public ConcertOptimizationPreferences {
        validateWeight("distance", distance);
        validateWeight("crossings", crossings);
        validateWeight("compactness", compactness);
        validateWeight("sectionContinuity", sectionContinuity);

        int total = distance + crossings + compactness + sectionContinuity;
        if (total != 100) {
            throw new IllegalArgumentException(
                    "La suma de las preferencias debe ser exactamente 100");
        }
    }

    public static ConcertOptimizationPreferences defaults() {
        return new ConcertOptimizationPreferences(
                DEFAULT_WEIGHT,
                DEFAULT_WEIGHT,
                DEFAULT_WEIGHT,
                DEFAULT_WEIGHT);
    }

    private static void validateWeight(String name, int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(
                    "El peso " + name + " debe estar entre 0 y 100");
        }
    }
}
