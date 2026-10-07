package com.ou.capstone.prediction;

public record ModelExecutionError(
        String status,
        String executionId,
        String modelId,
        ErrorResponse error) {
}