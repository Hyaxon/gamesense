package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SinglePredictionResult(
        String id,
        PredictionMethod method,
        String predictedWinnerTeamId,
        double confidence,
        Instant generatedAt,
        Integer predictedHomeScore,
        Integer predictedAwayScore) {
}