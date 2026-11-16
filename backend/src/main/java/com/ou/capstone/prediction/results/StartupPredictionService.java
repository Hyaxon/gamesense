package com.ou.capstone.prediction.results;

import com.ou.capstone.data.CanonicalDataProvider;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.prediction.client.PredictionTransportException;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Predicts every scheduled game in a season once, in the background, after startup ingestion.
 *
 * <p>Games are marked PENDING before the job starts, so the Games API can show progress. Each game
 * is then stored as AVAILABLE or UNAVAILABLE. Runs on one background thread so startup and game
 * retrieval never wait on the prediction service. Nothing runs when startup predictions or
 * orchestration are disabled, and those games carry no predictions.
 */
@Component
public class StartupPredictionService implements DisposableBean {
  private static final Logger LOGGER = LoggerFactory.getLogger(StartupPredictionService.class);
  private final ObjectProvider<PredictionOrchestrator> orchestrator;
  private final CanonicalDataProvider dataProvider;
  private final PredictionResultStore results;
  private final boolean enabled;
  private final ExecutorService executor;

  @Autowired
  public StartupPredictionService(
      ObjectProvider<PredictionOrchestrator> orchestrator,
      CanonicalDataProvider dataProvider,
      PredictionResultStore results,
      @Value("${prediction.startup.enabled:true}") boolean enabled) {
    this(
        orchestrator,
        dataProvider,
        results,
        enabled,
        Executors.newSingleThreadExecutor(
            task -> {
              Thread thread = new Thread(task, "startup-predictions");
              // Do not keep the JVM alive for unfinished predictions on shutdown.
              thread.setDaemon(true);
              return thread;
            }));
  }

  StartupPredictionService(
      ObjectProvider<PredictionOrchestrator> orchestrator,
      CanonicalDataProvider dataProvider,
      PredictionResultStore results,
      boolean enabled,
      ExecutorService executor) {
    this.orchestrator = orchestrator;
    this.dataProvider = dataProvider;
    this.results = results;
    this.enabled = enabled;
    this.executor = executor;
  }

  /**
   * Marks the season's scheduled games as pending and starts predicting them in the background.
   *
   * @return the background job, or empty when startup predictions are disabled
   */
  public Optional<Future<?>> start(int season) {
    PredictionOrchestrator predictor = orchestrator.getIfAvailable();
    if (!enabled || predictor == null) {
      LOGGER.info(
          "Startup predictions are disabled; season {} games carry no predictions.", season);
      return Optional.empty();
    }
    List<String> gameIds =
        dataProvider.findGames(season).stream()
            .filter(game -> game.status() == GameStatus.SCHEDULED)
            .map(Game::id)
            .toList();
    gameIds.forEach(gameId -> results.save(gameId, GamePredictions.pending()));
    return Optional.of(executor.submit(() -> predictGames(predictor, season, gameIds)));
  }

  private void predictGames(PredictionOrchestrator predictor, int season, List<String> gameIds) {
    int available = 0;
    for (String gameId : gameIds) {
      GamePredictions predictions;
      try {
        predictions = GamePredictions.from(predictor.predictGame(gameId));
      } catch (PredictionTransportException error) {
        if (error.reason() == PredictionTransportException.Reason.INTERRUPTED) {
          // Shutdown: leave the remaining games pending rather than reporting them as failed.
          Thread.currentThread().interrupt();
          LOGGER.info("Startup predictions for season {} stopped by shutdown.", season);
          return;
        }
        predictions = GamePredictions.unavailable();
      } catch (RuntimeException error) {
        // Missing snapshots or invalid game data affect one game, not the rest of the season.
        LOGGER.debug("Predictions unavailable for game {}", gameId, error);
        predictions = GamePredictions.unavailable();
      }
      results.save(gameId, predictions);
      if (predictions.state() == PredictionState.AVAILABLE) {
        available++;
      }
    }
    LOGGER.info(
        "Startup predictions for season {} complete: {} of {} scheduled games available.",
        season,
        available,
        gameIds.size());
  }

  @Override
  public void destroy() {
    executor.shutdownNow();
  }
}
