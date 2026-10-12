package com.ou.capstone.prediction.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.prediction.Matchup;
import com.ou.capstone.prediction.ModelExecutionRequest;
import com.ou.capstone.prediction.ModelExecutionResult;
import com.ou.capstone.prediction.PredictionResponseDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PredictionOrchestrationResultTest {
  private ModelExecutionOutcome.Success success;
  private ModelExecutionOutcome.Failure failure;
  private Matchup matchup;
  private PredictionSnapshot snapshot;

  @BeforeEach
  void loadExecution() throws Exception {
    Path directory = Path.of("../shared/examples/model-execution");
    ModelExecutionRequest request =
        new ObjectMapper()
            .readValue(
                directory.resolve("simulation-request.json").toFile(), ModelExecutionRequest.class);
    ModelExecutionResult result =
        (ModelExecutionResult)
            new PredictionResponseDecoder()
                .decode(Files.readString(directory.resolve("simulation-result.json")), request);
    success = new ModelExecutionOutcome.Success(request.executionId(), request.modelId(), result);
    failure =
        new ModelExecutionOutcome.Failure(
            "b1b2c3d4-e5f6-4789-8abc-1234567890ab",
            request.modelId(),
            ModelExecutionOutcome.FailureKind.TIMEOUT,
            "Execution timed out.",
            null);
    matchup = request.matchup();
    snapshot = new PredictionSnapshot(request.dataSnapshotId(), matchup.season());
  }

  @Test
  void retainsMixedOutcomesAndRepeatedModelIdsInOrder() {
    List<ModelExecutionOutcome> supplied = new ArrayList<>(List.of(success, failure));
    var result = new PredictionOrchestrationResult(null, matchup, snapshot, supplied);
    supplied.clear();
    assertThat(result.gameId()).isNull();
    assertThat(result.outcomes()).containsExactly(success, failure);
    assertThatThrownBy(() -> result.outcomes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void retainsAllFailedExecutionsAndScheduledGameIdentity() {
    String gameId = "9c1e2d3b-4a5f-4e6d-8b7c-1a2b3c4d5e6f";
    var result = new PredictionOrchestrationResult(gameId, matchup, snapshot, List.of(failure));
    assertThat(result.gameId()).isEqualTo(gameId);
    assertThat(result.outcomes()).containsExactly(failure);
  }

  @Test
  void rejectsMismatchedSnapshotOrMatchup() {
    assertThatThrownBy(
            () ->
                new PredictionOrchestrationResult(
                    null,
                    matchup,
                    new PredictionSnapshot(snapshot.dataSnapshotId(), 2026),
                    List.of(failure)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PredictionOrchestrationResult(
                    null,
                    matchup,
                    new PredictionSnapshot("other-snapshot", matchup.season()),
                    List.of(success)))
        .isInstanceOf(IllegalArgumentException.class);
    Matchup reversed =
        new Matchup(
            matchup.awayTeamId(), matchup.homeTeamId(), matchup.season(), matchup.isNeutralSite());
    assertThatThrownBy(
            () -> new PredictionOrchestrationResult(null, reversed, snapshot, List.of(success)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
