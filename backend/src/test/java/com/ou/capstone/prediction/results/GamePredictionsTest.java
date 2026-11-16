package com.ou.capstone.prediction.results;

import static com.ou.capstone.prediction.results.PredictionResultFixtures.deterministic;
import static com.ou.capstone.prediction.results.PredictionResultFixtures.failure;
import static com.ou.capstone.prediction.results.PredictionResultFixtures.matchup;
import static com.ou.capstone.prediction.results.PredictionResultFixtures.result;
import static com.ou.capstone.prediction.results.PredictionResultFixtures.simulation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.PredictionMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class GamePredictionsTest {
  private final Matchup matchup = matchup("home", "away");

  @Test
  void partialFailuresKeepSuccessfulModelsInOrder() {
    GamePredictions predictions =
        GamePredictions.from(
            result(
                "game",
                matchup,
                deterministic(matchup, "elo-v1", "Elo", PredictionMethod.ELO),
                failure("glicko2"),
                simulation(
                    matchup, "monte-carlo-v1", "Monte Carlo", PredictionMethod.MONTE_CARLO)));

    assertThat(predictions.state()).isEqualTo(PredictionState.AVAILABLE);
    assertThat(predictions.models())
        .extracting(ModelPrediction::modelId)
        .containsExactly("elo-v1", "monte-carlo-v1");
    ModelPrediction elo = predictions.models().get(0);
    assertThat(elo.displayName()).isEqualTo("Elo");
    assertThat(elo.prediction().method()).isEqualTo(PredictionMethod.ELO);
    assertThat(elo.prediction().predictedWinnerTeamId()).isEqualTo("home");
    assertThat(elo.simulation()).isNull();
    assertThat(predictions.models().get(1).simulation().trials()).isEqualTo(1000);
  }

  @Test
  void allFailedModelsAreUnavailable() {
    GamePredictions predictions =
        GamePredictions.from(result("game", matchup, failure("elo-v1"), failure("glicko2")));

    assertThat(predictions).isEqualTo(GamePredictions.unavailable());
    assertThat(predictions.models()).isEmpty();
  }

  @Test
  void onlyAvailablePredictionsContainModels() {
    ModelPrediction elo =
        GamePredictions.from(
                result(
                    "game", matchup, deterministic(matchup, "elo-v1", "Elo", PredictionMethod.ELO)))
            .models()
            .get(0);

    assertThatThrownBy(() -> new GamePredictions(PredictionState.AVAILABLE, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GamePredictions(PredictionState.PENDING, List.of(elo)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
