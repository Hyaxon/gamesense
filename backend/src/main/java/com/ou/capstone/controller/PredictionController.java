package com.ou.capstone.controller;

import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorDetail;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.orchestration.PredictionInputException;
import com.ou.capstone.prediction.service.CustomPredictionRequest;
import com.ou.capstone.prediction.service.CustomPredictionService;
import com.ou.capstone.prediction.service.PredictionApiException;
import com.ou.capstone.prediction.service.PredictionResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes custom matchup predictions through /predictions. Registered only when prediction
 * orchestration is enabled. Failures use the shared ErrorResponse contract.
 */
@RestController
@RequestMapping("/predictions")
@ConditionalOnProperty(prefix = "prediction.orchestration", name = "enabled", havingValue = "true")
public class PredictionController {

  private final CustomPredictionService service;

  public PredictionController(CustomPredictionService service) {
    this.service = service;
  }

  // example usage: POST /predictions/custom with a CustomPredictionRequest body.
  // Omit "methods" to run every available model.
  @PostMapping("/custom")
  public PredictionResponse predictCustom(@RequestBody CustomPredictionRequest request) {
    return service.predict(request);
  }

  @ExceptionHandler(PredictionApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(PredictionApiException error) {
    return ResponseEntity.status(error.status())
        .body(new ErrorResponse(error.code(), error.getMessage(), null, error.details()));
  }

  // Missing snapshots or teams are unavailable data (404); other input problems are 400.
  @ExceptionHandler(PredictionInputException.class)
  public ResponseEntity<ErrorResponse> handleInput(PredictionInputException error) {
    boolean unavailable = error.getMessage() != null && error.getMessage().contains("unavailable");
    ErrorCode code = unavailable ? ErrorCode.NOT_FOUND : ErrorCode.VALIDATION_ERROR;
    return ResponseEntity.status(unavailable ? 404 : 400)
        .body(
            new ErrorResponse(
                code,
                unavailable ? "Prediction data is unavailable." : "Invalid prediction request.",
                null,
                List.of(new ErrorDetail(error.getPath(), error.getMessage()))));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException error) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                "Request body is malformed or contains unsupported values.",
                null,
                List.of(new ErrorDetail("", "Malformed request body."))));
  }
}
