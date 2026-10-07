package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    ErrorCode code, String message, String requestId, List<ErrorDetail> details)
    implements PredictionServiceResponse {}
