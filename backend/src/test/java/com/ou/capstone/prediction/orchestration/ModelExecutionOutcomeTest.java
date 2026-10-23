package com.ou.capstone.prediction.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.prediction.client.PredictionResponseDecoder;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorDetail;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ModelExecutionRequest;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ModelExecutionOutcomeTest {
  private static final String EXECUTION_ID = "a1b2c3d4-e5f6-4789-8abc-1234567890ab";
  private static final String MODEL_ID = "test-model";
  private static final ErrorResponse ERROR =
      new ErrorResponse(
          ErrorCode.VALIDATION_ERROR,
          "Invalid configuration.",
          "request-123",
          List.of(new ErrorDetail("/configuration", "Unsupported setting.")));

  @ParameterizedTest
  @ValueSource(strings = {"deterministic", "stochastic", "simulation"})
  void preservesCompleteResultAndRejectsWrongAssociation(String example) throws Exception {
    Path directory = Path.of("../shared/examples/model-execution");
    ModelExecutionRequest request =
        new ObjectMapper()
            .readValue(
                directory.resolve(example + "-request.json").toFile(), ModelExecutionRequest.class);
    ModelExecutionResult result =
        (ModelExecutionResult)
            new PredictionResponseDecoder()
                .decode(Files.readString(directory.resolve(example + "-result.json")), request);
    var outcome =
        new ModelExecutionOutcome.Success(request.executionId(), request.modelId(), result);
    assertThat(outcome.result()).isSameAs(result);
    assertThat(outcome.executionId()).isEqualTo(request.executionId());
    assertThat(outcome.modelId()).isEqualTo(request.modelId());
    assertThatThrownBy(
            () -> new ModelExecutionOutcome.Success("other-execution", request.modelId(), result))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ModelExecutionOutcome.Success(request.executionId(), "other-model", result))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ModelExecutionOutcome.FailureKind.class,
      names = {"MODEL_ERROR", "BOUNDARY_ERROR"})
  void preservesServiceErrorWithSubmittedIdentity(ModelExecutionOutcome.FailureKind kind) {
    var outcome =
        new ModelExecutionOutcome.Failure(EXECUTION_ID, MODEL_ID, kind, ERROR.message(), ERROR);
    assertThat(outcome.serviceError()).isSameAs(ERROR);
    assertThat(outcome.executionId()).isEqualTo(EXECUTION_ID);
    assertThat(outcome.modelId()).isEqualTo(MODEL_ID);
    assertThatThrownBy(
            () -> new ModelExecutionOutcome.Failure(EXECUTION_ID, MODEL_ID, kind, "Failure.", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ModelExecutionOutcome.FailureKind.class,
      names = {"TIMEOUT", "CONNECTION_FAILURE", "INVALID_RESPONSE", "SERIALIZATION_FAILURE"})
  void keepsClientFailureSeparateFromServiceErrors(ModelExecutionOutcome.FailureKind kind) {
    var outcome =
        new ModelExecutionOutcome.Failure(EXECUTION_ID, MODEL_ID, kind, "Execution failed.", null);
    assertThat(outcome.kind()).isEqualTo(kind);
    assertThat(outcome.serviceError()).isNull();
    assertThatThrownBy(
            () ->
                new ModelExecutionOutcome.Failure(EXECUTION_ID, MODEL_ID, kind, "Failure.", ERROR))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMissingFailureMessage() {
    assertThatThrownBy(
            () ->
                new ModelExecutionOutcome.Failure(
                    EXECUTION_ID, MODEL_ID, ModelExecutionOutcome.FailureKind.TIMEOUT, " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
