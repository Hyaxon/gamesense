package com.ou.capstone;

// Checks whether a Prediction is well-formed before it gets used downstream (stored, displayed, or compared against other methods).
public class PredictionValidator {

    public static boolean isValid(Prediction p) {
        return hasMethod(p) && hasPredictedWinner(p) && hasValidConfidence(p);
    }

    // isBlank() catches both null-string-as-empty and whitespace-only values
    private static boolean hasMethod(Prediction p) {
        return p.method() != null && !p.method().isBlank();
    }

    private static boolean hasPredictedWinner(Prediction p) {
        return p.predictedWinner() != null && !p.predictedWinner().isBlank();
    }

    // Inclusive bounds: 0.0 (no confidence) and 1.0 (certain) are both legitimate values
    private static boolean hasValidConfidence(Prediction p) {
        return p.confidenceScore() >= 0.0 && p.confidenceScore() <= 1.0;
    }
}
