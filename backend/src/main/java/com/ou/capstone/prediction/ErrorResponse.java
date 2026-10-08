package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Shared error payload used directly for boundary failures or inside a model execution error.
 *
 * <p>Request correlation metadata and field-level details are optional.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    ErrorCode code, String message, String requestId, List<ErrorDetail> details)
    implements PredictionServiceResponse {}
