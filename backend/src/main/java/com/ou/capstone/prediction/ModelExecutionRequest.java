package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Single-model invocation sent to Python using the shared camelCase JSON contract.
 *
 * <p>References a matchup and an existing snapshot; optional configuration, seed, and trial count
 * are omitted from JSON when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelExecutionRequest(
    String executionId,
    String modelId,
    Matchup matchup,
    String dataSnapshotId,
    ExecutionKind executionKind,
    Map<String, Object> configuration,
    Integer seed,
    Integer trials) {}
