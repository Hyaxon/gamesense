package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Standard winner prediction produced by every execution kind.
 *
 * <p>Confidence belongs to the selected team; predicted scores are optional.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SinglePredictionResult(
    String id,
    PredictionMethod method,
    String predictedWinnerTeamId,
    double confidence,
    Instant generatedAt,
    Integer predictedHomeScore,
    Integer predictedAwayScore) {}
