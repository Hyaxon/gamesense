package com.ou.capstone.prediction;

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
