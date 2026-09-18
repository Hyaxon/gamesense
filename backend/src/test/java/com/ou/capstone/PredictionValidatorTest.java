package com.ou.capstone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

// Verifies PredictionValidator including the exact valid/invalid examples. Stays in sync with what Python/JS are testing against.
class PredictionValidatorTest {

    @Test
    void validPredictionPasses() {
        Prediction p = new Prediction("ELO", "Thunder", 0.72);
        assertTrue(PredictionValidator.isValid(p));
    }

    @Test
    void emptyMethodAndOverOneConfidenceFails() {
        // mirrors the invalid example Hyde provided
        Prediction p = new Prediction("", "Thunder", 1.99);
        assertFalse(PredictionValidator.isValid(p));
    }

    @ParameterizedTest
    @CsvSource({
        "ELO, Thunder, 0.0",   // lower boundary — valid
        "ELO, Thunder, 1.0",   // upper boundary — valid
        "ELO, Thunder, -0.01", // just under 0 — invalid
        "ELO, Thunder, 1.01"   // just over 1 — invalid
    })
    void confidenceBoundaries(String method, String winner, double confidence) {
        Prediction p = new Prediction(method, winner, confidence);
        boolean expected = confidence >= 0.0 && confidence <= 1.0;
        assertEquals(expected, PredictionValidator.isValid(p));
    }

    @Test
    void blankPredictedWinnerFails() {
        Prediction p = new Prediction("ELO", "  ", 0.5);
        assertFalse(PredictionValidator.isValid(p));
    }
}
