package com.ou.capstone.prediction;

import java.time.Instant;

public record SimulationOutcome(
    String id,
    PredictionMethod method,
    int trials,
    double homeWinProbability,
    double awayWinProbability,
    Instant simulatedAt) {}
