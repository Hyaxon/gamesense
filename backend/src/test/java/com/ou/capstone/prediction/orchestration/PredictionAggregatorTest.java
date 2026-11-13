package com.ou.capstone.prediction.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.prediction.contract.AggregatedPredictionOutcome;
import com.ou.capstone.prediction.contract.AggregationStrategy;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.ExecutionMetadata;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.ModelDescriptor;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PredictionAggregatorTest {
  private static final String HOME = "0e7a1c2b-3d4e-4f5a-8b6c-7d8e9f0a1b2c";
  private static final String AWAY = "1f8b2d3c-4e5f-4a6b-9c7d-8e9f0a1b2c3d";
  private static final Matchup MATCHUP = new Matchup(HOME, AWAY, 2025, false);
  private static final PredictionSnapshot SNAPSHOT = new PredictionSnapshot("snapshot-2025", 2025);

  private final PredictionAggregator aggregator =
      new PredictionAggregator(Map.of("elo-v1", 3.0, "glicko-v1", 1.0, "zero-v1", 0.0));

  @ParameterizedTest
  @CsvSource({
    // Home probabilities 0.6 and 0.8 average to 0.7.
    "AVERAGE, 0.7",
    // Weights 3 and 1 give (3 * 0.6 + 0.8) / 4 = 0.65.
    "WEIGHTED_AVERAGE, 0.65",
    "MAJORITY_VOTE, 1.0"
  })
  void unanimousHomePicksHomeUnderEveryStrategy(AggregationStrategy strategy, double confidence) {
    AggregatedPredictionOutcome outcome =
        aggregate(
            strategy,
            success("elo-v1", "elo", HOME, 0.6),
            success("glicko-v1", "glicko", HOME, 0.8));
    assertThat(outcome.aggregationStrategy()).isEqualTo(strategy);
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(HOME);
    assertThat(outcome.confidence()).isCloseTo(confidence, within(1e-9));
    assertThat(outcome.contributingResultIds()).containsExactly("elo", "glicko");
    assertThat(outcome.id()).isNotNull();
    assertThat(outcome.generatedAt()).isNotNull();
  }

  @Test
  void awayPickConvertsToHomeProbabilityBeforeAveraging() {
    // Home probabilities 0.6 and 1 - 0.8 = 0.2 average to 0.4, so away wins at 0.6.
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.AVERAGE,
            success("elo-v1", "elo", HOME, 0.6),
            success("glicko-v1", "glicko", AWAY, 0.8));
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(AWAY);
    assertThat(outcome.confidence()).isCloseTo(0.6, within(1e-9));
  }

  @Test
  void weightsCanFlipTheAverageWinner() {
    ModelExecutionOutcome elo = success("elo-v1", "elo", AWAY, 0.7);
    ModelExecutionOutcome glicko = success("glicko-v1", "glicko", HOME, 0.9);

    // Home probabilities 0.3 and 0.9 average to 0.6.
    AggregatedPredictionOutcome average = aggregate(AggregationStrategy.AVERAGE, elo, glicko);
    assertThat(average.predictedWinnerTeamId()).isEqualTo(HOME);
    assertThat(average.confidence()).isCloseTo(0.6, within(1e-9));

    // Weights 3 and 1 give (3 * 0.3 + 0.9) / 4 = 0.45 for home, so away wins at 0.55.
    AggregatedPredictionOutcome weighted =
        aggregate(AggregationStrategy.WEIGHTED_AVERAGE, elo, glicko);
    assertThat(weighted.aggregationStrategy()).isEqualTo(AggregationStrategy.WEIGHTED_AVERAGE);
    assertThat(weighted.predictedWinnerTeamId()).isEqualTo(AWAY);
    assertThat(weighted.confidence()).isCloseTo(0.55, within(1e-9));
  }

  @Test
  void weightedAverageExcludesZeroAndMissingWeights() {
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.WEIGHTED_AVERAGE,
            success("elo-v1", "elo", HOME, 0.8),
            success("zero-v1", "zero", AWAY, 0.9),
            success("coin-flip-v1", "coin", AWAY, 0.9));
    assertThat(outcome.aggregationStrategy()).isEqualTo(AggregationStrategy.WEIGHTED_AVERAGE);
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(HOME);
    assertThat(outcome.confidence()).isCloseTo(0.8, within(1e-9));
    assertThat(outcome.contributingResultIds()).containsExactly("elo");
  }

  @Test
  void weightedAverageWithoutPositiveWeightsFallsBackToAverage() {
    // Home probabilities 0.6 and 0.2 average to 0.4.
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.WEIGHTED_AVERAGE,
            success("zero-v1", "zero", HOME, 0.6),
            success("coin-flip-v1", "coin", AWAY, 0.8));
    assertThat(outcome.aggregationStrategy()).isEqualTo(AggregationStrategy.AVERAGE);
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(AWAY);
    assertThat(outcome.confidence()).isCloseTo(0.6, within(1e-9));
    assertThat(outcome.contributingResultIds()).containsExactly("zero", "coin");
  }

  @Test
  void allFailedExecutionsProduceNoAggregate() {
    PredictionOrchestrationResult result = result(failure("elo-v1"), failure("glicko-v1"));
    for (AggregationStrategy strategy : AggregationStrategy.values()) {
      assertThat(aggregator.aggregate(result, strategy)).isEmpty();
    }
  }

  @Test
  void partialFailureAggregatesOnlySuccesses() {
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.AVERAGE,
            success("elo-v1", "elo", HOME, 0.7),
            failure("glicko-v1"),
            success("coin-flip-v1", "coin", HOME, 0.9));
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(HOME);
    assertThat(outcome.confidence()).isCloseTo(0.8, within(1e-9));
    assertThat(outcome.contributingResultIds()).containsExactly("elo", "coin");
  }

  @Test
  void majorityVoteCountsVotesNotConfidence() {
    // Averaging would pick away: (0.55 + 0.55 + 0.01) / 3 = 0.37 for home.
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.MAJORITY_VOTE,
            success("elo-v1", "elo", HOME, 0.55),
            success("glicko-v1", "glicko", HOME, 0.55),
            success("coin-flip-v1", "coin", AWAY, 0.99));
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(HOME);
    assertThat(outcome.confidence()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(outcome.contributingResultIds()).containsExactly("elo", "glicko", "coin");
  }

  @Test
  void majorityVoteTieIsDecidedByAverageProbability() {
    // Home probabilities 0.6 and 0.1 average to 0.35, so away breaks the 1-1 tie.
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.MAJORITY_VOTE,
            success("elo-v1", "elo", HOME, 0.6),
            success("coin-flip-v1", "coin", AWAY, 0.9));
    assertThat(outcome.aggregationStrategy()).isEqualTo(AggregationStrategy.MAJORITY_VOTE);
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(AWAY);
    assertThat(outcome.confidence()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void exactEvenAverageFavorsHome() {
    // Home probabilities 0.75 and 0.25 average to exactly 0.5.
    AggregatedPredictionOutcome outcome =
        aggregate(
            AggregationStrategy.AVERAGE,
            success("elo-v1", "elo", HOME, 0.75),
            success("glicko-v1", "glicko", AWAY, 0.75));
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(HOME);
    assertThat(outcome.confidence()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void repeatedAggregationIsDeterministic() {
    PredictionOrchestrationResult result =
        result(
            success("elo-v1", "elo", AWAY, 0.7),
            success("elo-v1", "elo-again", HOME, 0.6),
            success("glicko-v1", "glicko", HOME, 0.9));
    for (AggregationStrategy strategy : AggregationStrategy.values()) {
      AggregatedPredictionOutcome first = aggregator.aggregate(result, strategy).orElseThrow();
      AggregatedPredictionOutcome second = aggregator.aggregate(result, strategy).orElseThrow();
      assertThat(second.aggregationStrategy()).isEqualTo(first.aggregationStrategy());
      assertThat(second.predictedWinnerTeamId()).isEqualTo(first.predictedWinnerTeamId());
      assertThat(second.confidence()).isEqualTo(first.confidence());
      assertThat(second.contributingResultIds()).isEqualTo(first.contributingResultIds());
    }
  }

  @Test
  void accuracyWeightsCountOnlyTheMarginOverACoinFlip() {
    PredictionAggregator byAccuracy =
        PredictionAggregator.fromAccuracy(
            Map.of("elo-v1", 0.7, "glicko-v1", 0.6, "coin-flip-v1", 0.5));
    PredictionOrchestrationResult result =
        result(
            success("elo-v1", "elo", AWAY, 0.7),
            success("glicko-v1", "glicko", HOME, 0.8),
            success("coin-flip-v1", "coin", HOME, 0.9));

    // Weights 0.2, 0.1 and 0 give (0.2 * 0.3 + 0.1 * 0.8) / 0.3 = 0.4667 for home.
    AggregatedPredictionOutcome outcome =
        byAccuracy.aggregate(result, AggregationStrategy.WEIGHTED_AVERAGE).orElseThrow();
    assertThat(outcome.aggregationStrategy()).isEqualTo(AggregationStrategy.WEIGHTED_AVERAGE);
    assertThat(outcome.predictedWinnerTeamId()).isEqualTo(AWAY);
    assertThat(outcome.confidence()).isCloseTo(1 - 0.14 / 0.3, within(1e-9));
    assertThat(outcome.contributingResultIds()).containsExactly("elo", "glicko");
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
  void rejectsAccuracyOutsideProbabilityRange(double accuracy) {
    assertThatThrownBy(() -> PredictionAggregator.fromAccuracy(Map.of("elo-v1", accuracy)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void rejectsInvalidWeights(double weight) {
    assertThatThrownBy(() -> new PredictionAggregator(Map.of("elo-v1", weight)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullWeightOrModelId() {
    Map<String, Double> nullWeight = new HashMap<>();
    nullWeight.put("elo-v1", null);
    Map<String, Double> nullModelId = new HashMap<>();
    nullModelId.put(null, 1.0);
    assertThatThrownBy(() -> new PredictionAggregator(nullWeight))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PredictionAggregator(nullModelId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private AggregatedPredictionOutcome aggregate(
      AggregationStrategy strategy, ModelExecutionOutcome... outcomes) {
    return aggregator.aggregate(result(outcomes), strategy).orElseThrow();
  }

  private static PredictionOrchestrationResult result(ModelExecutionOutcome... outcomes) {
    return new PredictionOrchestrationResult(null, MATCHUP, SNAPSHOT, List.of(outcomes));
  }

  private static ModelExecutionOutcome success(
      String modelId, String predictionId, String winnerTeamId, double confidence) {
    String executionId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    ModelExecutionResult result =
        new ModelExecutionResult(
            "SUCCESS",
            executionId,
            new ModelDescriptor(
                modelId,
                modelId,
                PredictionMethod.ELO,
                "1",
                List.of(ExecutionKind.DETERMINISTIC),
                null),
            MATCHUP,
            new SinglePredictionResult(
                predictionId, PredictionMethod.ELO, winnerTeamId, confidence, now, null, null),
            new ExecutionMetadata(
                ExecutionKind.DETERMINISTIC, SNAPSHOT.dataSnapshotId(), now, now, 1.0, null),
            null,
            null,
            null);
    return new ModelExecutionOutcome.Success(executionId, modelId, result);
  }

  private static ModelExecutionOutcome failure(String modelId) {
    return new ModelExecutionOutcome.Failure(
        UUID.randomUUID().toString(),
        modelId,
        ModelExecutionOutcome.FailureKind.TIMEOUT,
        "Execution timed out.",
        null);
  }
}
