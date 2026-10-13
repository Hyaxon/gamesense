package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Identity, version, algorithm family, and supported execution modes of a registered model.
 *
 * <p>The optional configuration schema identifier names the model's configuration contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelDescriptor(
    String modelId,
    String displayName,
    PredictionMethod method,
    String version,
    List<ExecutionKind> supportedExecutionKinds,
    String configurationSchemaId) {}
