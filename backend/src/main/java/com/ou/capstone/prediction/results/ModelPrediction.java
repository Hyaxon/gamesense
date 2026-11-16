package com.ou.capstone.prediction.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ou.capstone.prediction.contract.SimulationOutcome;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import java.util.Objects;

/**
 * One model's successful prediction for a game, as returned by the Games API.
 *
 * <p>The simulation outcome is present only for simulation models.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelPrediction(
    String modelId,
    String displayName,
    SinglePredictionResult prediction,
    SimulationOutcome simulation) {
  public ModelPrediction {
    Objects.requireNonNull(modelId, "modelId");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(prediction, "prediction");
  }
}
