package com.rubengarcia.choir.transition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ConcertOptimizationPreferencesTests {

    @Test
    void returnsEqualDefaultWeights() {
        ConcertOptimizationPreferences preferences =
                ConcertOptimizationPreferences.defaults();

        assertThat(preferences.distance()).isEqualTo(25);
        assertThat(preferences.crossings()).isEqualTo(25);
        assertThat(preferences.compactness()).isEqualTo(25);
        assertThat(preferences.sectionContinuity()).isEqualTo(25);
    }

    @Test
    void acceptsMinimumAndMaximumWeightValues() {
        ConcertOptimizationPreferences preferences =
                new ConcertOptimizationPreferences(0, 0, 0, 100);

        assertThat(preferences.sectionContinuity()).isEqualTo(100);
        assertThat(preferences.distance()).isZero();
    }

    @Test
    void acceptsAWeightOfOneHundredForAnySingleCriterion() {
        assertThat(new ConcertOptimizationPreferences(100, 0, 0, 0).distance())
                .isEqualTo(100);
        assertThat(new ConcertOptimizationPreferences(0, 100, 0, 0).crossings())
                .isEqualTo(100);
        assertThat(new ConcertOptimizationPreferences(0, 0, 100, 0).compactness())
                .isEqualTo(100);
        assertThat(new ConcertOptimizationPreferences(0, 0, 0, 100)
                .sectionContinuity()).isEqualTo(100);
    }

    @Test
    void rejectsNegativeWeights() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new ConcertOptimizationPreferences(-1, 34, 33, 34));
    }

    @Test
    void rejectsWeightsAboveOneHundred() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new ConcertOptimizationPreferences(101, 0, 0, 0));
    }

    @Test
    void rejectsWeightsWhoseSumIsBelowOneHundred() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new ConcertOptimizationPreferences(25, 25, 25, 24));
    }

    @Test
    void rejectsWeightsWhoseSumIsAboveOneHundred() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new ConcertOptimizationPreferences(25, 25, 25, 26));
    }

    @Test
    void acceptsUnequalWeightsWhenTheirSumIsOneHundred() {
        ConcertOptimizationPreferences preferences =
                new ConcertOptimizationPreferences(50, 30, 15, 5);

        assertThat(preferences.distance()).isEqualTo(50);
        assertThat(preferences.crossings()).isEqualTo(30);
        assertThat(preferences.compactness()).isEqualTo(15);
        assertThat(preferences.sectionContinuity()).isEqualTo(5);
    }
}
