package com.ou.capstone.prediction.service;

import com.ou.capstone.prediction.client.PredictionTransportException;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorDetail;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.contract.SimulationOutcome;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome;
import com.ou.capstone.prediction.orchestration.ModelSelection;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrationResult;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Resolves a custom prediction request to model executions, runs them through the orchestrator, and
 * maps the outcomes to a PredictionResponse.
 *
 * <p>Prediction executions use configured non-simulation selections that declare a method. When
 * methods is omitted every such selection runs; unimplemented enum methods are simply absent.
 * Requested simulations reuse the configured model for their method with the requested trials and
 * seed. The API returns complete success or a single error.
 */
@Service
@ConditionalOnProperty(prefix = "prediction.orchestration", name = "enabled", havingValue = "true")
public class CustomPredictionService {
  private static final int MAX_TRIALS = 100000;

  private final PredictionOrchestrator orchestrator;

  public CustomPredictionService(PredictionOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  /** Runs the request; throws PredictionApiException or PredictionInputException on failure. */
  public PredictionResponse predict(CustomPredictionRequest request) {
    Matchup matchup = toMatchup(request);
    validateLists(request);

    List<ModelSelection> predictionSelections = resolvePredictionSelections(request.methods());
    List<ModelSelection> simulationSelections = resolveSimulationSelections(request.simulations());
    List<ModelSelection> all = new ArrayList<>(predictionSelections);
    all.addAll(simulationSelections);

    PredictionOrchestrationResult result;
    try {
      result = orchestrator.predictMatchup(matchup, all);
    } catch (PredictionTransportException error) {
      throw new PredictionApiException(
          503, ErrorCode.INTERNAL_ERROR, "Prediction request was interrupted.", null);
    }
    return toResponse(matchup, result, predictionSelections.size());
  }

  private static Matchup toMatchup(CustomPredictionRequest request) {
    if (request == null || request.matchup() == null) {
      throw invalid("/matchup", "A matchup is required.");
    }
    var m = request.matchup();
    if (m.homeTeamId() == null) {
      throw invalid("/matchup/homeTeamId", "A home team is required.");
    }
    if (m.awayTeamId() == null) {
      throw invalid("/matchup/awayTeamId", "An away team is required.");
    }
    if (m.season() == null) {
      throw invalid("/matchup/season", "A season is required.");
    }
    if (m.isNeutralSite() == null) {
      throw invalid("/matchup/isNeutralSite", "Neutral-site setting must be explicit.");
    }
    return new Matchup(m.homeTeamId(), m.awayTeamId(), m.season(), m.isNeutralSite());
  }

  private static void validateLists(CustomPredictionRequest request) {
    List<PredictionMethod> methods = request.methods();
    if (methods != null) {
      if (methods.isEmpty()) {
        throw invalid("/methods", "Omit methods to run all models, or list at least one.");
      }
      Set<PredictionMethod> seen = new HashSet<>();
      for (int i = 0; i < methods.size(); i++) {
        if (methods.get(i) == null) {
          throw invalid("/methods/" + i, "A method is required.");
        }
        if (!seen.add(methods.get(i))) {
          throw invalid("/methods/" + i, "Methods must be unique.");
        }
      }
    }
    var simulations = request.simulations();
    if (simulations != null) {
      if (simulations.isEmpty()) {
        throw invalid("/simulations", "Omit simulations or list at least one.");
      }
      for (int i = 0; i < simulations.size(); i++) {
        var sim = simulations.get(i);
        String path = "/simulations/" + i;
        if (sim == null || sim.method() == null) {
          throw invalid(path + "/method", "A method is required.");
        }
        if (sim.trials() == null || sim.trials() < 1 || sim.trials() > MAX_TRIALS) {
          throw invalid(path + "/trials", "Trials must be between 1 and 100000.");
        }
        if (sim.seed() != null && sim.seed() < 0) {
          throw invalid(path + "/seed", "Seed must be a nonnegative 32-bit integer.");
        }
      }
    }
  }

  private List<ModelSelection> resolvePredictionSelections(List<PredictionMethod> methods) {
    List<ModelSelection> available =
        orchestrator.selections().stream().filter(s -> s.method() != null).toList();
    if (methods == null) {
      if (available.isEmpty()) {
        throw new PredictionApiException(
            503, ErrorCode.INTERNAL_ERROR, "No prediction models are available.", null);
      }
      return available;
    }
    List<ModelSelection> resolved = new ArrayList<>();
    for (int i = 0; i < methods.size(); i++) {
      PredictionMethod method = methods.get(i);
      List<ModelSelection> matches = available.stream().filter(s -> s.method() == method).toList();
      if (matches.isEmpty()) {
        throw unsupported("/methods/" + i, method);
      }
      resolved.addAll(matches);
    }
    return resolved;
  }

  private List<ModelSelection> resolveSimulationSelections(
      List<CustomPredictionRequest.SimulationRequest> simulations) {
    List<ModelSelection> resolved = new ArrayList<>();
    if (simulations == null) {
      return resolved;
    }
    for (int i = 0; i < simulations.size(); i++) {
      var sim = simulations.get(i);
      List<ModelSelection> configured =
          orchestrator.selections().stream().filter(s -> s.method() == sim.method()).toList();
      ModelSelection template =
          configured.stream()
              .filter(s -> s.executionKind() == ExecutionKind.SIMULATION)
              .findFirst()
              .orElse(configured.isEmpty() ? null : configured.get(0));
      if (template == null) {
        throw unsupported("/simulations/" + i + "/method", sim.method());
      }
      resolved.add(
          new ModelSelection(
              template.modelId(),
              ExecutionKind.SIMULATION,
              template.configuration(),
              sim.seed(),
              sim.trials(),
              sim.method()));
    }
    return resolved;
  }

  private static PredictionResponse toResponse(
      Matchup matchup, PredictionOrchestrationResult result, int predictionCount) {
    List<SinglePredictionResult> predictions = new ArrayList<>();
    List<SimulationOutcome> simulations = new ArrayList<>();
    List<ModelExecutionOutcome> outcomes = result.outcomes();
    for (int i = 0; i < outcomes.size(); i++) {
      if (outcomes.get(i) instanceof ModelExecutionOutcome.Failure failure) {
        throw toApiException(failure);
      }
      ModelExecutionResult success = ((ModelExecutionOutcome.Success) outcomes.get(i)).result();
      if (i < predictionCount) {
        predictions.add(requireNonNull(success.prediction()));
      } else {
        simulations.add(requireNonNull(success.simulation()));
      }
    }
    return new PredictionResponse(
        matchup, predictions, simulations.isEmpty() ? null : List.copyOf(simulations));
  }

  private static <T> T requireNonNull(T value) {
    if (value == null) {
      throw new PredictionApiException(
          502,
          ErrorCode.PREDICTION_FAILED,
          "Prediction service returned an invalid response.",
          null);
    }
    return value;
  }

  private static PredictionApiException toApiException(ModelExecutionOutcome.Failure failure) {
    ErrorResponse error = failure.serviceError();
    if (error != null) {
      int status =
          switch (error.code()) {
            case NOT_FOUND -> 404;
            case VALIDATION_ERROR, UNSUPPORTED_METHOD -> 422;
            default -> 502;
          };
      ErrorCode code = status == 502 ? ErrorCode.PREDICTION_FAILED : error.code();
      return new PredictionApiException(status, code, error.message(), error.details());
    }
    return switch (failure.kind()) {
      case TIMEOUT ->
          new PredictionApiException(504, ErrorCode.PREDICTION_FAILED, failure.message(), null);
      case SERIALIZATION_FAILURE ->
          new PredictionApiException(500, ErrorCode.INTERNAL_ERROR, failure.message(), null);
      default ->
          new PredictionApiException(502, ErrorCode.PREDICTION_FAILED, failure.message(), null);
    };
  }

  private static PredictionApiException invalid(String path, String message) {
    return new PredictionApiException(
        400,
        ErrorCode.VALIDATION_ERROR,
        "Invalid prediction request.",
        List.of(new ErrorDetail(path, message)));
  }

  private static PredictionApiException unsupported(String path, PredictionMethod method) {
    return new PredictionApiException(
        400,
        ErrorCode.UNSUPPORTED_METHOD,
        "Prediction method is not supported.",
        List.of(new ErrorDetail(path, method + " is not available.")));
  }
}
