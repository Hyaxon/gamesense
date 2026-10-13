package com.ou.capstone.prediction.orchestration;

import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import java.util.Objects;

/**
 * Backend-only outcome of one attempted execution, identified by the submitted request.
 *
 * <p>Success and failure are separate types, so a failure cannot carry a prediction. Interruption
 * cancels orchestration rather than becoming an ordinary model failure.
 */
public sealed interface ModelExecutionOutcome
    permits ModelExecutionOutcome.Success, ModelExecutionOutcome.Failure {
  String executionId();

  String modelId();

  /** Preserves the complete validated Python result and checks its execution association. */
  record Success(String executionId, String modelId, ModelExecutionResult result)
      implements ModelExecutionOutcome {
    public Success {
      Objects.requireNonNull(executionId, "executionId");
      Objects.requireNonNull(modelId, "modelId");
      Objects.requireNonNull(result, "result");
      if (!"SUCCESS".equals(result.status())
          || !executionId.equals(result.executionId())
          || result.model() == null
          || !modelId.equals(result.model().modelId())) {
        throw new IllegalArgumentException("Result must match the submitted execution and model.");
      }
    }
  }

  /**
   * Failure associated with the submitted request, even when the service supplied no correlation.
   *
   * <p>Service errors retain their shared error payload. Client failures use a safe message and no
   * service error. Raw exception messages and response bodies must not be supplied here.
   */
  record Failure(
      String executionId,
      String modelId,
      FailureKind kind,
      String message,
      ErrorResponse serviceError)
      implements ModelExecutionOutcome {
    public Failure {
      Objects.requireNonNull(executionId, "executionId");
      Objects.requireNonNull(modelId, "modelId");
      Objects.requireNonNull(kind, "kind");
      if (message == null || message.isBlank()) {
        throw new IllegalArgumentException("A safe failure message is required.");
      }
      boolean isServiceError =
          kind == FailureKind.MODEL_ERROR || kind == FailureKind.BOUNDARY_ERROR;
      if (isServiceError != (serviceError != null)) {
        throw new IllegalArgumentException(
            "Only service failures require a service error payload.");
      }
    }
  }

  /** Local categories supplement the shared error codes without changing the wire contract. */
  enum FailureKind {
    MODEL_ERROR,
    BOUNDARY_ERROR,
    TIMEOUT,
    CONNECTION_FAILURE,
    INVALID_RESPONSE,
    SERIALIZATION_FAILURE
  }
}
