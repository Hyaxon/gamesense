package com.ou.capstone.prediction.results;

import static com.ou.capstone.prediction.results.PredictionResultFixtures.deterministic;
import static com.ou.capstone.prediction.results.PredictionResultFixtures.matchup;
import static com.ou.capstone.prediction.results.PredictionResultFixtures.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.prediction.client.PredictionTransportException;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.orchestration.PredictionInputException;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StartupPredictionServiceTest {
  private final InMemoryCanonicalDataStore data = new InMemoryCanonicalDataStore();
  private final InMemoryPredictionResultStore results = new InMemoryPredictionResultStore();
  private final PredictionOrchestrator orchestrator = mock(PredictionOrchestrator.class);
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void stopExecutor() {
    executor.shutdownNow();
  }

  private static Game game(String id, int season, GameStatus status) {
    return new Game(
        id,
        null,
        season,
        null,
        "home",
        "away",
        null,
        false,
        Instant.EPOCH,
        status,
        null,
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private StartupPredictionService service(boolean enabled, PredictionOrchestrator available) {
    ObjectProvider<PredictionOrchestrator> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(available);
    return new StartupPredictionService(provider, data, results, enabled, executor);
  }

  private void awaitCompletion(Optional<Future<?>> job) throws Exception {
    assertThat(job).isPresent();
    job.get().get();
  }

  @Test
  void predictsOnlyScheduledGamesInTheSeason() throws Exception {
    data.load(
        List.of(),
        List.of(
            game("scheduled", 2026, GameStatus.SCHEDULED),
            game("final", 2026, GameStatus.FINAL),
            game("other-season", 2025, GameStatus.SCHEDULED)));
    Matchup matchup = matchup("home", "away");
    when(orchestrator.predictGame("scheduled"))
        .thenReturn(
            result(
                "scheduled",
                matchup,
                deterministic(matchup, "elo-v1", "Elo", PredictionMethod.ELO)));

    awaitCompletion(service(true, orchestrator).start(2026));

    assertThat(results.find("scheduled").orElseThrow().state())
        .isEqualTo(PredictionState.AVAILABLE);
    assertThat(results.find("final")).isEmpty();
    assertThat(results.find("other-season")).isEmpty();
    verify(orchestrator, never()).predictGame("final");
    verify(orchestrator, never()).predictGame("other-season");
  }

  @Test
  void gamesArePendingUntilPredicted() throws Exception {
    data.load(
        List.of(),
        List.of(
            game("first", 2026, GameStatus.SCHEDULED), game("second", 2026, GameStatus.SCHEDULED)));
    Matchup matchup = matchup("home", "away");
    when(orchestrator.predictGame(any()))
        .thenAnswer(
            call -> {
              if ("first".equals(call.getArgument(0))) {
                // The second game is queued but not yet predicted.
                assertThat(results.find("second").orElseThrow())
                    .isEqualTo(GamePredictions.pending());
              }
              return result(
                  call.getArgument(0),
                  matchup,
                  deterministic(matchup, "elo-v1", "Elo", PredictionMethod.ELO));
            });

    awaitCompletion(service(true, orchestrator).start(2026));

    assertThat(results.find("second").orElseThrow().state()).isEqualTo(PredictionState.AVAILABLE);
  }

  @Test
  void oneFailedGameDoesNotStopTheRest() throws Exception {
    data.load(
        List.of(),
        List.of(
            game("broken", 2026, GameStatus.SCHEDULED), game("ok", 2026, GameStatus.SCHEDULED)));
    Matchup matchup = matchup("home", "away");
    when(orchestrator.predictGame("broken"))
        .thenThrow(new PredictionInputException("/dataSnapshotId", "No snapshot."));
    when(orchestrator.predictGame("ok"))
        .thenReturn(
            result("ok", matchup, deterministic(matchup, "elo-v1", "Elo", PredictionMethod.ELO)));

    awaitCompletion(service(true, orchestrator).start(2026));

    assertThat(results.find("broken")).contains(GamePredictions.unavailable());
    assertThat(results.find("ok").orElseThrow().state()).isEqualTo(PredictionState.AVAILABLE);
  }

  @Test
  void interruptionLeavesRemainingGamesPending() throws Exception {
    data.load(
        List.of(),
        List.of(
            game("first", 2026, GameStatus.SCHEDULED), game("second", 2026, GameStatus.SCHEDULED)));
    when(orchestrator.predictGame("first"))
        .thenThrow(
            new PredictionTransportException(
                PredictionTransportException.Reason.INTERRUPTED, "Interrupted.", null));

    awaitCompletion(service(true, orchestrator).start(2026));

    assertThat(results.find("first")).contains(GamePredictions.pending());
    assertThat(results.find("second")).contains(GamePredictions.pending());
    verify(orchestrator, never()).predictGame("second");
  }

  @Test
  void disabledStartupPredictionsDoNothing() {
    data.load(List.of(), List.of(game("scheduled", 2026, GameStatus.SCHEDULED)));

    assertThat(service(false, orchestrator).start(2026)).isEmpty();

    assertThat(results.find("scheduled")).isEmpty();
    verify(orchestrator, never()).predictGame(any());
  }

  @Test
  void disabledOrchestrationDoesNothing() {
    data.load(List.of(), List.of(game("scheduled", 2026, GameStatus.SCHEDULED)));

    assertThat(service(true, null).start(2026)).isEmpty();

    assertThat(results.find("scheduled")).isEmpty();
  }
}
