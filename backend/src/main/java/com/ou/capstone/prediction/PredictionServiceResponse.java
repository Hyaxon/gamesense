package com.ou.capstone.prediction;

/**
 * Common return type for the three supported Python response shapes.
 *
 * <p>Transport failures and invalid responses are exceptions, not members of this response union.
 */
public sealed interface PredictionServiceResponse
    permits ModelExecutionResult, ModelExecutionError, ErrorResponse {}
