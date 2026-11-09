package com.ou.capstone.prediction.client;

import com.ou.capstone.prediction.contract.ErrorResponse;

/** A valid error response from Python rejecting a snapshot publication. */
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
