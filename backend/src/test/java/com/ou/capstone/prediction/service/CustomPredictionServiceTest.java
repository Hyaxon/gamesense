package com.ou.capstone.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.ExecutionMetadata;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.ModelDescriptor;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.contract.SimulationOutcome;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome.FailureKind;
import com.ou.capstone.prediction.orchestration.ModelSelection;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrationResult;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrator;
import com.ou.capstone.prediction.service.CustomPredictionRequest.MatchupRequest;
import com.ou.capstone.prediction.service.CustomPredictionRequest.SimulationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomPredictionServiceTest {
  private static final String HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  private static final Matchup MATCHUP = new Matchup(HOME, AWAY, 2025, true);
  private static final PredictionSnapshot SNAPSHOT = new PredictionSnapshot("snap-2025", 2025);
  private static final MatchupRequest MATCHUP_REQUEST = new MatchupRequest(HOME, AWAY, 2025, true);

  private final PredictionOrchestrator orchestrator = mock(PredictionOrchestrator.class);
  private final CustomPredictionService service = new CustomPredictionService(orchestrator);
  private FailureKind failWith;

  private static ModelSelection selection(String id, ExecutionKind kind, PredictionMethod method) {
    Integer trials = kind == ExecutionKind.SIMULATION ? 100 : null;
    return new ModelSelection(id, kind, null, null, trials, method);
  }

  private void configure(ModelSelection... selections) {
    when(orchestrator.selections()).thenReturn(List.of(selections));
    when(orchestrator.predictMatchup(any(), anyList()))
        .thenAnswer(
            call -> {
              List<ModelSelection> requested = call.getArgument(1);
              List<ModelExecutionOutcome> outcomes = new ArrayList<>();
              for (ModelSelection s : requested) {
                String executionId = java.util.UUID.randomUUID().toString();
                if (failWith != null) {
                  outcomes.add(
                      new ModelExecutionOutcome.Failure(
                          executionId, s.modelId(), failWith, "Safe failure.", null));
                } else {
                  outcomes.add(success(executionId, s));
                }
              }
              return new PredictionOrchestrationResult(null, MATCHUP, SNAPSHOT, outcomes);
            });
  }

  private static ModelExecutionOutcome success(String executionId, ModelSelection s) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    boolean sim = s.executionKind() == ExecutionKind.SIMULATION;
    var prediction =
        new SinglePredictionResult("p-" + s.modelId(), s.method(), HOME, 0.6, now, null, null);
    var simulation =
        sim
            ? new SimulationOutcome("s-" + s.modelId(), s.method(), s.trials(), 0.6, 0.4, now)
            : null;
    var descriptor =
        new ModelDescriptor(
            s.modelId(), s.modelId(), s.method(), "1", List.of(s.executionKind()), null);
    var metadata = new ExecutionMetadata(s.executionKind(), "snap-2025", now, now, 1.0, s.seed());
    return new ModelExecutionOutcome.Success(
        executionId,
        s.modelId(),
        new ModelExecutionResult(
            "SUCCESS",
            executionId,
            descriptor,
            MATCHUP,
            prediction,
            metadata,
            simulation,
            null,
            null));
  }

  private static CustomPredictionRequest request(
      List<PredictionMethod> methods, List<SimulationRequest> sims) {
    return new CustomPredictionRequest(MATCHUP_REQUEST, methods, sims);
  }

  @Test
  void omittedMethodsRunEveryConfiguredMethodAndSkipOthers() {
    configure(
        selection("elo-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.ELO),
        selection("coin-v1", ExecutionKind.STOCHASTIC, PredictionMethod.RANDOM),
        selection("legacy", ExecutionKind.DETERMINISTIC, null));

    PredictionResponse response = service.predict(request(null, null));

    assertThat(response.matchup()).isEqualTo(MATCHUP);
    assertThat(response.predictions())
        .extracting(SinglePredictionResult::method)
        .containsExactly(PredictionMethod.ELO, PredictionMethod.RANDOM);
    assertThat(response.simulations()).isNull();
  }

  @Test
  void requestedMethodsOnlyRunMatchingModels() {
    configure(
        selection("elo-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.ELO),
        selection("coin-v1", ExecutionKind.STOCHASTIC, PredictionMethod.RANDOM));

    PredictionResponse response = service.predict(request(List.of(PredictionMethod.RANDOM), null));

    assertThat(response.predictions()).hasSize(1);
    assertThat(response.predictions().get(0).method()).isEqualTo(PredictionMethod.RANDOM);
  }

  @Test
  void unsupportedMethodIsRejected() {
    configure(selection("elo-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.ELO));

    assertThatThrownBy(() -> service.predict(request(List.of(PredictionMethod.GLICKO2), null)))
        .isInstanceOfSatisfying(
            PredictionApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(400);
              assertThat(e.code()).isEqualTo(ErrorCode.UNSUPPORTED_METHOD);
            });
  }

  @Test
  void simulationsHonorTrialsAndSeed() {
    configure(selection("mc-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.MONTE_CARLO));

    PredictionResponse response =
        service.predict(
            request(
                List.of(PredictionMethod.MONTE_CARLO),
                List.of(new SimulationRequest(PredictionMethod.MONTE_CARLO, 5000, 42))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ModelSelection>> captor = ArgumentCaptor.forClass(List.class);
    org.mockito.Mockito.verify(orchestrator).predictMatchup(any(), captor.capture());
    ModelSelection simulation = captor.getValue().get(1);
    assertThat(simulation.executionKind()).isEqualTo(ExecutionKind.SIMULATION);
    assertThat(simulation.trials()).isEqualTo(5000);
    assertThat(simulation.seed()).isEqualTo(42);
    assertThat(response.simulations()).hasSize(1);
    assertThat(response.simulations().get(0).trials()).isEqualTo(5000);
  }

  @Test
  void invalidInputsAreRejected() {
    configure(selection("elo-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.ELO));
    var badTrials = List.of(new SimulationRequest(PredictionMethod.ELO, 0, null));

    assertThatThrownBy(() -> service.predict(new CustomPredictionRequest(null, null, null)))
        .isInstanceOf(PredictionApiException.class);
    assertThatThrownBy(() -> service.predict(request(List.of(), null)))
        .isInstanceOf(PredictionApiException.class);
    assertThatThrownBy(
            () ->
                service.predict(request(List.of(PredictionMethod.ELO, PredictionMethod.ELO), null)))
        .isInstanceOf(PredictionApiException.class);
    assertThatThrownBy(() -> service.predict(request(null, badTrials)))
        .isInstanceOf(PredictionApiException.class);
    assertThatThrownBy(
            () ->
                service.predict(
                    new CustomPredictionRequest(
                        new MatchupRequest(HOME, AWAY, null, true), null, null)))
        .isInstanceOf(PredictionApiException.class);
  }

  @Test
  void failuresMapToApiErrors() {
    configure(selection("elo-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.ELO));
    int[] expected = {504, 502, 502, 500};
    FailureKind[] kinds = {
      FailureKind.TIMEOUT,
      FailureKind.CONNECTION_FAILURE,
      FailureKind.INVALID_RESPONSE,
      FailureKind.SERIALIZATION_FAILURE
    };
    for (int i = 0; i < kinds.length; i++) {
      failWith = kinds[i];
      int status = expected[i];
      assertThatThrownBy(() -> service.predict(request(null, null)))
          .isInstanceOfSatisfying(
              PredictionApiException.class, e -> assertThat(e.status()).isEqualTo(status));
    }
  }

  @Test
  void modelErrorUsesServiceErrorCode() {
    when(orchestrator.selections())
        .thenReturn(
            List.of(selection("elo-v1", ExecutionKind.DETERMINISTIC, PredictionMethod.ELO)));
    var error = new ErrorResponse(ErrorCode.NOT_FOUND, "No history.", null, null);
    when(orchestrator.predictMatchup(any(), anyList()))
        .thenReturn(
            new PredictionOrchestrationResult(
                null,
                MATCHUP,
                SNAPSHOT,
                List.of(
                    new ModelExecutionOutcome.Failure(
                        "e1", "elo-v1", FailureKind.MODEL_ERROR, "No history.", error))));

    assertThatThrownBy(() -> service.predict(request(null, null)))
        .isInstanceOfSatisfying(
            PredictionApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(404);
              assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
            });
  }
}
