package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelDescriptor(
        String modelId,
        String displayName,
        PredictionMethod method,
        String version,
        List<ExecutionKind> supportedExecutionKinds,
        String configurationSchemaId) {
}