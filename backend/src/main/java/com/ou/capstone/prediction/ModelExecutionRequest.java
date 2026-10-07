package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelExecutionRequest(
                String executionId,
                String modelId,
                Matchup matchup,
                String dataSnapshotId,
                ExecutionKind executionKind,
                Map<String, Object> configuration,
                Integer seed,
                Integer trials) {
}