package com.ou.capstone.prediction.results;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Keeps predictions in memory; the background prediction job writes while requests read. */
@Component
public class InMemoryPredictionResultStore implements PredictionResultStore {
  private final Map<String, GamePredictions> predictions = new ConcurrentHashMap<>();

  @Override
  public Optional<GamePredictions> find(String gameId) {
    return Optional.ofNullable(predictions.get(gameId));
  }

  @Override
  public void save(String gameId, GamePredictions predictions) {
    this.predictions.put(
        Objects.requireNonNull(gameId, "gameId"),
        Objects.requireNonNull(predictions, "predictions"));
  }
}
