package com.ou.capstone.prediction;

/**
 * Correlated model failure containing execution identity, model identity, and safe error details.
 *
 * <p>The response decoder checks the ERROR status and matches both identifiers to the request.
 */
public record ModelExecutionError(
    String status, String executionId, String modelId, ErrorResponse error)
    implements PredictionServiceResponse {}
