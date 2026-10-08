package com.ou.capstone.prediction;

import java.time.Instant;

/**
 * Repeated-trial results supplementing the standard prediction for a simulation execution.
 *
 * <p>The decoder checks trial count, probability totals, and agreement with the predicted winner.
 */
public record SimulationOutcome(
    String id,
    PredictionMethod method,
    int trials,
    double homeWinProbability,
    double awayWinProbability,
    Instant simulatedAt) {}
