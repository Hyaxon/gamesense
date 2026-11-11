package com.ou.capstone.prediction.orchestration;

import com.ou.capstone.prediction.contract.AggregatedPredictionOutcome;
import com.ou.capstone.prediction.contract.AggregationStrategy;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Combines the successful model predictions for one matchup into a single aggregate prediction.
 *
 * <p>The aggregator skips failed executions, which stay in the orchestration result. This class
 * does not persist outcomes, map responses, or decide whether a partial failure is acceptable.
 */
public final class PredictionAggregator {
  private final Map<String, Double> weightsByModelId;

  /** Weights must be nonnegative and finite. A model with no weight counts as weight zero. */
  public PredictionAggregator(Map<String, Double> weightsByModelId) {
    Objects.requireNonNull(weightsByModelId, "weightsByModelId");
    weightsByModelId.forEach(
        (modelId, weight) -> {
          if (modelId == null || weight == null || !Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException(
                "Model weights must be nonnegative finite numbers keyed by model ID.");
          }
        });
    this.weightsByModelId = Map.copyOf(weightsByModelId);
  }

  /**
   * Builds an aggregator that weights each model by how far its backtest accuracy beats a coin
   * flip.
   *
   * <p>Each weight is max(accuracy - 0.5, 0). Pass only the models the backtest ranked. A model
   * left out gets no weight.
   */
  public static PredictionAggregator fromAccuracy(Map<String, Double> accuracyByModelId) {
    Objects.requireNonNull(accuracyByModelId, "accuracyByModelId");
    Map<String, Double> weights = new HashMap<>();
    accuracyByModelId.forEach(
        (modelId, accuracy) -> {
          if (accuracy == null || !Double.isFinite(accuracy) || accuracy < 0 || accuracy > 1) {
            throw new IllegalArgumentException("Model accuracy must be between 0 and 1.");
          }
          weights.put(modelId, Math.max(accuracy - 0.5, 0));
        });
    return new PredictionAggregator(weights);
  }

  /** Returns empty when no execution succeeded. */
  public Optional<AggregatedPredictionOutcome> aggregate(
      PredictionOrchestrationResult result, AggregationStrategy strategy) {
    Matchup matchup = result.matchup();
    List<Contribution> contributions =
        result.outcomes().stream()
            .filter(ModelExecutionOutcome.Success.class::isInstance)
            .map(ModelExecutionOutcome.Success.class::cast)
            .map(success -> Contribution.of(success, matchup.homeTeamId()))
            .toList();
    if (contributions.isEmpty()) {
      return Optional.empty();
    }
    Decision decision =
        switch (strategy) {
          case AVERAGE -> average(contributions);
          case WEIGHTED_AVERAGE -> weightedAverage(contributions);
          case MAJORITY_VOTE -> majorityVote(contributions);
        };
    return Optional.of(
        new AggregatedPredictionOutcome(
            UUID.randomUUID().toString(),
            decision.strategy(),
            decision.contributors().stream().map(Contribution::predictionId).toList(),
            decision.isHomeWin() ? matchup.homeTeamId() : matchup.awayTeamId(),
            decision.confidence(),
            Instant.now()));
  }

  private static Decision average(List<Contribution> contributions) {
    return combine(AggregationStrategy.AVERAGE, contributions, contribution -> 1.0);
  }

  private Decision weightedAverage(List<Contribution> contributions) {
    ToDoubleFunction<Contribution> weight =
        contribution -> weightsByModelId.getOrDefault(contribution.modelId(), 0.0);
    List<Contribution> weighted =
        contributions.stream()
            .filter(contribution -> weight.applyAsDouble(contribution) > 0)
            .toList();
    if (weighted.isEmpty()) {
      return average(contributions);
    }
    return combine(AggregationStrategy.WEIGHTED_AVERAGE, weighted, weight);
  }

  private static Decision majorityVote(List<Contribution> contributions) {
    long homeVotes =
        contributions.stream()
            .filter(contribution -> contribution.homeWinProbability() >= 0.5)
            .count();
    long awayVotes = contributions.size() - homeVotes;
    if (homeVotes == awayVotes) {
      return new Decision(
          AggregationStrategy.MAJORITY_VOTE,
          contributions,
          average(contributions).isHomeWin(),
          0.5);
    }
    return new Decision(
        AggregationStrategy.MAJORITY_VOTE,
        contributions,
        homeVotes > awayVotes,
        (double) Math.max(homeVotes, awayVotes) / contributions.size());
  }

  private static Decision combine(
      AggregationStrategy strategy,
      List<Contribution> contributors,
      ToDoubleFunction<Contribution> weight) {
    double weightedSum = 0;
    double totalWeight = 0;
    for (Contribution contributor : contributors) {
      double contributorWeight = weight.applyAsDouble(contributor);
      weightedSum += contributorWeight * contributor.homeWinProbability();
      totalWeight += contributorWeight;
    }
    double homeWinProbability = weightedSum / totalWeight;
    boolean isHomeWin = homeWinProbability >= 0.5;
    return new Decision(
        strategy, contributors, isHomeWin, isHomeWin ? homeWinProbability : 1 - homeWinProbability);
  }

  private record Contribution(String modelId, String predictionId, double homeWinProbability) {
    static Contribution of(ModelExecutionOutcome.Success success, String homeTeamId) {
      SinglePredictionResult prediction = success.result().prediction();
      double confidence = prediction.confidence();
      return new Contribution(
          success.modelId(),
          prediction.id(),
          homeTeamId.equals(prediction.predictedWinnerTeamId()) ? confidence : 1 - confidence);
    }
  }

  private record Decision(
      AggregationStrategy strategy,
      List<Contribution> contributors,
      boolean isHomeWin,
      double confidence) {}
}
