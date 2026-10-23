package com.ou.capstone.prediction.orchestration;

/** Invalid or unavailable prediction input detected before model execution. */
public final class PredictionInputException extends IllegalArgumentException {
  private final String path;

  public PredictionInputException(String path, String message) {
    super(message);
    this.path = path;
  }

  /** JSON Pointer identifying the input that failed validation. */
  public String getPath() {
    return path;
  }
}
