package com.ou.capstone.prediction;

/** The three response shapes accepted from the prediction service. */
public sealed interface PredictionServiceResponse
    permits ModelExecutionResult, ModelExecutionError, ErrorResponse {}
