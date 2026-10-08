package com.ou.capstone.prediction;

/**
 * Signals an untrustworthy service response, including invalid JSON, contract violations,
 * correlation mismatches, or inconsistent HTTP metadata.
 */
public final class InvalidPredictionResponseException extends RuntimeException {
  public InvalidPredictionResponseException(String message) {
    super(message);
  }
}
