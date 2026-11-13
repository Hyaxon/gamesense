package com.ou.capstone.prediction.client;

import com.ou.capstone.prediction.contract.ErrorResponse;

/**
 * Python understood the request but rejected publication with a valid error response. Keeps that
 * rejection separate from connection failures and untrustworthy responses.
 */
public final class SnapshotPublicationRejectedException extends RuntimeException {
  private final ErrorResponse error;

  public SnapshotPublicationRejectedException(ErrorResponse error) {
    super("Snapshot publication rejected: " + error.code() + ": " + error.message());
    this.error = error;
  }

  public ErrorResponse error() {
    return error;
  }
}
