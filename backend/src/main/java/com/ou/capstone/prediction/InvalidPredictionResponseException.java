package com.ou.capstone.prediction;

/** A response that must not be exposed downstream as a trustworthy prediction. */
public final class InvalidPredictionResponseException extends RuntimeException {
  public InvalidPredictionResponseException(String message) {
    super(message);
  }
}
