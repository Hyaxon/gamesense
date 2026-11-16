package com.ou.capstone.prediction.results;

import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrationResult;
import java.util.List;
import java.util.Objects;

/**
 * Stored predictions for one game, embedded in its GameResponse.
 *
 * <p>Only successful model results are kept, so a failed model does not hide the others. A game is
 * AVAILABLE when at least one model succeeded and UNAVAILABLE when none did. The shared schema
 * reserves an optional aggregate field, which is added here once the aggregation calculation is
 * connected (#137).
 */
public record GamePredictions(PredictionState state, List<ModelPrediction> models) {
  public GamePredictions {
    Objects.requireNonNull(state, "state");
    models = List.copyOf(models);
    if ((state == PredictionState.AVAILABLE) == models.isEmpty()) {
      throw new IllegalArgumentException("Only available predictions contain model results.");
    }
  }

  public static GamePredictions pending() {
    return new GamePredictions(PredictionState.PENDING, List.of());
  }

  public static GamePredictions unavailable() {
    return new GamePredictions(PredictionState.UNAVAILABLE, List.of());
  }

  /** Keeps the successful results in execution order and drops failures. */
  public static GamePredictions from(PredictionOrchestrationResult result) {
    List<ModelPrediction> models =
        result.outcomes().stream()
            .filter(ModelExecutionOutcome.Success.class::isInstance)
            .map(ModelExecutionOutcome.Success.class::cast)
            .map(
                success ->
                    new ModelPrediction(
                        success.modelId(),
                        success.result().model().displayName(),
                        success.result().prediction(),
                        success.result().simulation()))
            .toList();
    return models.isEmpty()
        ? unavailable()
        : new GamePredictions(PredictionState.AVAILABLE, models);
  }
}
