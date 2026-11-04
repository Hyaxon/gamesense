package com.ou.capstone.prediction.orchestration;

import com.ou.capstone.data.PredictionDataProvider;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.client.InvalidPredictionResponseException;
import com.ou.capstone.prediction.client.PredictionTransportException;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.ModelExecutionError;
import com.ou.capstone.prediction.contract.ModelExecutionRequest;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionServiceResponse;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome.FailureKind;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates canonical input retrieval and sequential model executions for one matchup.
 *
 * <p>Selections are explicitly supplied by backend configuration. Each invocation validates all
 * common inputs before model execution. Independent failures remain in the ordered result, even
 * when every execution fails. Interruption stops scheduling and propagates with the interrupt flag
 * preserved. There are no automatic retries, status calculations, or aggregation.
 */
public final class PredictionOrchestrator {
  private final PredictionDataProvider dataProvider;
  private final PythonPredictionClient predictionClient;
  private final PredictionInputValidator validator = new PredictionInputValidator();
  private final List<ModelSelection> selections;

  public PredictionOrchestrator(
      PredictionDataProvider dataProvider,
      PythonPredictionClient predictionClient,
      List<ModelSelection> selections) {
    this.dataProvider = Objects.requireNonNull(dataProvider, "dataProvider");
    this.predictionClient = Objects.requireNonNull(predictionClient, "predictionClient");
    validator.validateSelections(selections);
    this.selections = List.copyOf(selections);
  }

  /** Predicts an existing game using its canonical matchup and the configured models. */
  public PredictionOrchestrationResult predictGame(String gameId) {
    checkInterruption();
    validator.validateGameId(gameId);
    Game game = dataProvider.findGame(gameId).orElse(null);
    validator.validateGame(gameId, game);
    Matchup matchup =
        new Matchup(game.homeTeamId(), game.awayTeamId(), game.season(), game.isNeutralSite());
    return predict(gameId, matchup);
  }

  /** Predicts a custom matchup without looking up or creating a scheduled game. */
  public PredictionOrchestrationResult predictMatchup(Matchup matchup) {
    return predict(null, matchup);
  }

  private PredictionOrchestrationResult predict(String gameId, Matchup matchup) {
    checkInterruption();
    validator.validateSelections(selections);
    validator.validateMatchup(matchup);
    PredictionSnapshot snapshot = dataProvider.findSnapshot(matchup.season()).orElse(null);
    validator.validateSnapshot(matchup, snapshot);
    Team home = dataProvider.findTeam(snapshot, matchup.homeTeamId()).orElse(null);
    Team away = dataProvider.findTeam(snapshot, matchup.awayTeamId()).orElse(null);
    validator.validateData(matchup, snapshot, home, away);

    List<ModelExecutionOutcome> outcomes = new ArrayList<>();
    for (ModelSelection selection : selections) {
      checkInterruption();
      ModelExecutionRequest request =
          new ModelExecutionRequest(
              UUID.randomUUID().toString(),
              selection.modelId(),
              matchup,
              snapshot.dataSnapshotId(),
              selection.executionKind(),
              selection.configuration(),
              selection.seed(),
              selection.trials());
      outcomes.add(execute(request));
    }
    checkInterruption();
    return new PredictionOrchestrationResult(gameId, matchup, snapshot, outcomes);
  }

  private ModelExecutionOutcome execute(ModelExecutionRequest request) {
    try {
      PredictionServiceResponse response = predictionClient.execute(request);
      checkInterruption();
      if (response instanceof ModelExecutionResult result) {
        // The client validates the full wire contract. Keep association checks here as well so
        // an alternate client or test double cannot attach another execution's result.
        requireResponse(
            "SUCCESS".equals(result.status())
                && request.executionId().equals(result.executionId())
                && result.model() != null
                && request.modelId().equals(result.model().modelId())
                && request.matchup().equals(result.matchup())
                && result.metadata() != null
                && request.dataSnapshotId().equals(result.metadata().dataSnapshotId())
                && request.executionKind() == result.metadata().executionKind());
        return new ModelExecutionOutcome.Success(request.executionId(), request.modelId(), result);
      }
      if (response instanceof ModelExecutionError error) {
        requireResponse(
            "ERROR".equals(error.status())
                && request.executionId().equals(error.executionId())
                && request.modelId().equals(error.modelId()));
        return serviceFailure(request, FailureKind.MODEL_ERROR, error.error());
      }
      if (response instanceof ErrorResponse error) {
        return serviceFailure(request, FailureKind.BOUNDARY_ERROR, error);
      }
      throw new InvalidPredictionResponseException("Prediction service returned no outcome.");
    } catch (PredictionTransportException error) {
      if (error.reason() == PredictionTransportException.Reason.INTERRUPTED) {
        Thread.currentThread().interrupt();
        throw error;
      }
      return switch (error.reason()) {
        case TIMEOUT ->
            failure(request, FailureKind.TIMEOUT, "Prediction service request timed out.");
        case CONNECTION_FAILURE ->
            failure(
                request,
                FailureKind.CONNECTION_FAILURE,
                "Communication with the prediction service failed.");
        case INTERRUPTED -> throw error;
      };
    } catch (InvalidPredictionResponseException error) {
      return failure(
          request,
          FailureKind.INVALID_RESPONSE,
          "Prediction service returned an invalid response.");
    } catch (IOException error) {
      return failure(
          request,
          FailureKind.SERIALIZATION_FAILURE,
          "Prediction request could not be serialized.");
    }
  }

  private static ModelExecutionOutcome serviceFailure(
      ModelExecutionRequest request, FailureKind kind, ErrorResponse error) {
    requireResponse(
        error != null
            && error.code() != null
            && error.message() != null
            && !error.message().isBlank());
    return new ModelExecutionOutcome.Failure(
        request.executionId(), request.modelId(), kind, error.message(), error);
  }

  private static ModelExecutionOutcome failure(
      ModelExecutionRequest request, FailureKind kind, String message) {
    return new ModelExecutionOutcome.Failure(
        request.executionId(), request.modelId(), kind, message, null);
  }

  private static void requireResponse(boolean condition) {
    if (!condition) {
      throw new InvalidPredictionResponseException(
          "Prediction response does not match its request.");
    }
  }

  private static void checkInterruption() {
    if (Thread.currentThread().isInterrupted()) {
      throw new PredictionTransportException(
          PredictionTransportException.Reason.INTERRUPTED,
          "Prediction orchestration was interrupted.",
          null);
    }
  }
}
