package com.ou.capstone;

// Simple data holder for one prediction result. Using a record here since this is just data
public record Prediction(
    String method,          // e.g. "ELO", "Monte Carlo" — which algorithm produced this
    String predictedWinner, // team name the method picked to win
    double confidenceScore  // 0.0-1.0, how sure the method is
) {}
