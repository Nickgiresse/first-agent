package com.firstagent.backend.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringSimilarityTest {

    @Test
    void identicalValuesScoreOne() {
        assertThat(StringSimilarity.similarity("Jean", "Jean")).isEqualTo(1.0);
    }

    @Test
    void accentsAndCaseAreIgnored() {
        assertThat(StringSimilarity.similarity("Kämga", "KAMGA")).isEqualTo(1.0);
    }

    @Test
    void minorTypoStillScoresHigh() {
        assertThat(StringSimilarity.similarity("Ngono", "Ngomo")).isGreaterThan(0.75);
    }

    @Test
    void unrelatedValuesScoreLow() {
        assertThat(StringSimilarity.similarity("Jean Nkeng", "Paul Biya")).isLessThan(0.5);
    }

    @Test
    void blankValueScoresZeroAgainstNonBlank() {
        assertThat(StringSimilarity.similarity("", "Jean")).isEqualTo(0.0);
    }
}
