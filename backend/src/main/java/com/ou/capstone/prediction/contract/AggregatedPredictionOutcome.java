package com.ou.capstone.prediction.contract;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The combined winner prediction for one matchup, built from single prediction results.
 *
 * <p>Contributor IDs refer to single prediction results in the same matchup response; confidence
 * belongs to the selected team.
 */
public record AggregatedPredictionOutcome(
    String id,
    AggregationStrategy aggregationStrategy,
    List<String> contributingResultIds,
    String predictedWinnerTeamId,
    double confidence,
    Instant generatedAt) {
  public AggregatedPredictionOutcome {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(aggregationStrategy, "aggregationStrategy");
    Objects.requireNonNull(contributingResultIds, "contributingResultIds");
    Objects.requireNonNull(predictedWinnerTeamId, "predictedWinnerTeamId");
    Objects.requireNonNull(generatedAt, "generatedAt");
    contributingResultIds = List.copyOf(contributingResultIds);
    if (contributingResultIds.isEmpty()) {
      throw new IllegalArgumentException("An aggregate needs at least one contributor.");
    }
    if (new HashSet<>(contributingResultIds).size() != contributingResultIds.size()) {
      throw new IllegalArgumentException("Contributors must be unique.");
    }
    if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("Confidence must be between 0 and 1.");
    }
  }
}
