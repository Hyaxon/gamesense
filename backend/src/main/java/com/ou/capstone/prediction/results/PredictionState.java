package com.ou.capstone.prediction.results;

/** Whether a game's stored predictions are still running, ready, or could not be produced. */
public enum PredictionState {
  PENDING,
  AVAILABLE,
  UNAVAILABLE
}
