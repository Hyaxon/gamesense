package com.ou.capstone.prediction.results;

import java.util.Optional;

/**
 * Stores each game's predictions by canonical game ID.
 *
 * <p>The in-memory implementation loses results on restart; persistence (#59) can replace it behind
 * this interface.
 */
public interface PredictionResultStore {

  /** Returns empty when no predictions were requested for the game. */
  Optional<GamePredictions> find(String gameId);

  /** Replaces any predictions already stored for the game. */
  void save(String gameId, GamePredictions predictions);
}
