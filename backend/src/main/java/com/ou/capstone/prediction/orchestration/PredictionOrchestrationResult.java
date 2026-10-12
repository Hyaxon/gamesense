package com.ou.capstone.prediction.orchestration;

import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.prediction.Matchup;
import java.util.List;
import java.util.Objects;

/**
 * Backend-only collection of execution outcomes for one matchup and snapshot.
 *
 * <p>gameId is absent for custom matchups. Outcomes retain execution order, including repeated
 * model IDs and failures. The list is defensively copied; it may contain only failures. This type
 * does not aggregate predictions or implement the frontend PredictionResponse contract.
 */
public record PredictionOrchestrationResult(
    String gameId,
    Matchup matchup,
    PredictionSnapshot snapshot,
    List<ModelExecutionOutcome> outcomes) {
  public PredictionOrchestrationResult {
    Objects.requireNonNull(matchup, "matchup");
    Objects.requireNonNull(snapshot, "snapshot");
    outcomes = List.copyOf(outcomes);
    if (snapshot.season() != matchup.season()) {
      throw new IllegalArgumentException("Snapshot season must match the matchup.");
    }
    for (ModelExecutionOutcome outcome : outcomes) {
      if (outcome instanceof ModelExecutionOutcome.Success success) {
        if (!matchup.equals(success.result().matchup())
            || success.result().metadata() == null
            || !Objects.equals(
                snapshot.dataSnapshotId(), success.result().metadata().dataSnapshotId())) {
          throw new IllegalArgumentException(
              "Successful results must match the collection context.");
        }
      }
    }
  }
}
