package com.ou.capstone.prediction;

/**
 * Service-defined failure categories returned by Python.
 *
 * <p>Transport failures such as timeouts are represented separately by
 * PredictionTransportException.
 */
public enum ErrorCode {
  VALIDATION_ERROR,
  NOT_FOUND,
  UNSUPPORTED_METHOD,
  PREDICTION_FAILED,
  INTERNAL_ERROR
}
