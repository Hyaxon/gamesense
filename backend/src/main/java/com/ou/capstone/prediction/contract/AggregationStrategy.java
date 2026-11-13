package com.ou.capstone.prediction.contract;

/**
 * Approaches for combining several model predictions for one matchup into a single aggregate.
 *
 * <p>Names the combination approach only. PredictionAggregator owns the weights and chooses which
 * results contribute.
 */
public enum AggregationStrategy {
  AVERAGE,
  WEIGHTED_AVERAGE,
  MAJORITY_VOTE
}
