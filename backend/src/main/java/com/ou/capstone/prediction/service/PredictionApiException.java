package com.ou.capstone.prediction.service;

import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorDetail;
import java.util.List;

/** API-level failure carrying the HTTP status and the shared error payload contents. */
public final class PredictionApiException extends RuntimeException {
  private final int status;
  private final ErrorCode code;
  private final List<ErrorDetail> details;

  public PredictionApiException(
      int status, ErrorCode code, String message, List<ErrorDetail> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details == null || details.isEmpty() ? null : List.copyOf(details);
  }

  public int status() {
    return status;
  }

  public ErrorCode code() {
    return code;
  }

  public List<ErrorDetail> details() {
    return details;
  }
}
