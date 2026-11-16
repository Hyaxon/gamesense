package com.ou.capstone.prediction.results;

import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.ExecutionMetadata;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.ModelDescriptor;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.contract.SimulationOutcome;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrationResult;
import java.time.Instant;
import java.util.List;

/** Builds orchestration results for prediction storage and Games API tests. */
public final class PredictionResultFixtures {
  public static final PredictionSnapshot SNAPSHOT = new PredictionSnapshot("snapshot-2026", 2026);
  public static final Instant GENERATED_AT = Instant.parse("2026-09-01T12:00:00Z");

  private PredictionResultFixtures() {}

  public static Matchup matchup(String homeTeamId, String awayTeamId) {
    return new Matchup(homeTeamId, awayTeamId, SNAPSHOT.season(), false);
  }

  public static ModelExecutionOutcome.Success deterministic(
      Matchup matchup, String modelId, String displayName, PredictionMethod method) {
    return success(matchup, modelId, displayName, method, ExecutionKind.DETERMINISTIC, null);
  }

  public static ModelExecutionOutcome.Success simulation(
      Matchup matchup, String modelId, String displayName, PredictionMethod method) {
    SimulationOutcome simulation =
        new SimulationOutcome(modelId + "-simulation", method, 1000, 0.6, 0.4, GENERATED_AT);
    return success(matchup, modelId, displayName, method, ExecutionKind.SIMULATION, simulation);
  }

  public static ModelExecutionOutcome.Failure failure(String modelId) {
    return new ModelExecutionOutcome.Failure(
        modelId + "-execution",
        modelId,
        ModelExecutionOutcome.FailureKind.MODEL_ERROR,
        "Model failed.",
        new ErrorResponse(ErrorCode.PREDICTION_FAILED, "Model failed.", null, null));
  }

  public static PredictionOrchestrationResult result(
      String gameId, Matchup matchup, ModelExecutionOutcome... outcomes) {
    return new PredictionOrchestrationResult(gameId, matchup, SNAPSHOT, List.of(outcomes));
  }

  private static ModelExecutionOutcome.Success success(
      Matchup matchup,
      String modelId,
      String displayName,
      PredictionMethod method,
      ExecutionKind kind,
      SimulationOutcome simulation) {
    String executionId = modelId + "-execution";
    ModelExecutionResult result =
        new ModelExecutionResult(
            "SUCCESS",
            executionId,
            new ModelDescriptor(modelId, displayName, method, "1", List.of(kind), null),
            matchup,
            new SinglePredictionResult(
                modelId + "-prediction",
                method,
                matchup.homeTeamId(),
                0.6,
                GENERATED_AT,
                null,
                null),
            new ExecutionMetadata(
                kind, SNAPSHOT.dataSnapshotId(), GENERATED_AT, GENERATED_AT, 1.0, null),
            simulation,
            null,
            null);
    return new ModelExecutionOutcome.Success(executionId, modelId, result);
  }
}
