package com.ou.capstone.prediction;

/**
 * Distinguishes request timeout, communication failure, and interruption from Python model errors.
 *
 * <p>Retains the underlying cause for diagnostics and exposes a reason for caller failure handling.
 */
public final class PredictionTransportException extends RuntimeException {
  public enum Reason {
    TIMEOUT,
    CONNECTION_FAILURE,
    INTERRUPTED
  }

  private final Reason reason;

  public PredictionTransportException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
