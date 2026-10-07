package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelExecutionResult(
    String status,
    String executionId,
    ModelDescriptor model,
    Matchup matchup,
    SinglePredictionResult prediction,
    ExecutionMetadata metadata,
    SimulationOutcome simulation,
    List<SupportingScore> supportingScores,
    Map<String, Object> configuration)
    implements PredictionServiceResponse {}
