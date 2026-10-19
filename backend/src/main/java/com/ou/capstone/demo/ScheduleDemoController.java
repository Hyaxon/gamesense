package com.ou.capstone.demo;

import com.ou.capstone.prediction.client.PythonPredictionClient;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome;
import com.ou.capstone.prediction.orchestration.ModelSelection;
import com.ou.capstone.prediction.orchestration.PredictionInputException;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrationResult;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** HTTP entry point for replaying held-out games against a fixed historical snapshot. */
@RestController
@Profile("schedule-demo")
@RequestMapping("/demo")
public class ScheduleDemoController {
  private static final List<ModelSelection> MODELS =
      List.of(
          new ModelSelection("elo-v1", ExecutionKind.DETERMINISTIC, null, null, null),
          new ModelSelection("glicko2", ExecutionKind.DETERMINISTIC, null, null, null),
          new ModelSelection("bradley-terry-v1", ExecutionKind.DETERMINISTIC, null, null, null),
          new ModelSelection("monte-carlo-v1", ExecutionKind.SIMULATION, null, 42, 10000),
          new ModelSelection("coin-flip-v1", ExecutionKind.SIMULATION, null, 42, 10000),
          new ModelSelection("xgboost-v1", ExecutionKind.DETERMINISTIC, null, null, null));
  private final ScheduleDemoData data;
  private final PythonPredictionClient client;

  public ScheduleDemoController(ScheduleDemoData data, PythonPredictionClient client) {
    this.data = data;
    this.client = client;
  }

  public record PredictionRequest(String modelId, String gameId) {}

  public record GameOption(String gameId, String homeTeam, String awayTeam, Instant scheduledAt) {}

  public record PredictionResponse(
      String homeTeam,
      String awayTeam,
      String predictedWinner,
      Instant historyBefore,
      PredictionOrchestrationResult execution) {}

  @GetMapping("/models")
  public List<ModelSelection> models() {
    return MODELS;
  }

  @GetMapping("/games")
  public List<GameOption> games(@RequestParam(defaultValue = "") String team) {
    return data.games().stream()
        .map(
            game ->
                new GameOption(
                    game.id(),
                    data.teamName(game.homeTeamId()),
                    data.teamName(game.awayTeamId()),
                    game.scheduledAt()))
        .filter(
            game ->
                team.isBlank()
                    || game.homeTeam().equalsIgnoreCase(team)
                    || game.awayTeam().equalsIgnoreCase(team))
        .toList();
  }

  @PostMapping("/predictions")
  public ResponseEntity<PredictionResponse> predict(@RequestBody PredictionRequest request) {
    var selection =
        MODELS.stream()
            .filter(model -> model.modelId().equals(request.modelId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose a modelId from GET /demo/models."));
    if (request.gameId() == null || request.gameId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gameId is required.");
    }
    var game =
        data.findGame(request.gameId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Choose a gameId from GET /demo/games."));
    var orchestrator = new PredictionOrchestrator(data, client, List.of(selection));
    var result = orchestrator.predictGame(game.id());
    var outcome = result.outcomes().get(0);
    String winner =
        outcome instanceof ModelExecutionOutcome.Success success
            ? data.teamName(success.result().prediction().predictedWinnerTeamId())
            : null;
    return ResponseEntity.status(winner == null ? HttpStatus.BAD_GATEWAY : HttpStatus.OK)
        .body(
            new PredictionResponse(
                data.teamName(game.homeTeamId()),
                data.teamName(game.awayTeamId()),
                winner,
                data.cutoff(),
                result));
  }

  @ExceptionHandler({PredictionInputException.class, ResponseStatusException.class})
  public ResponseEntity<Map<String, String>> invalidInput(RuntimeException error) {
    if (error instanceof ResponseStatusException status) {
      return ResponseEntity.status(status.getStatusCode())
          .body(Map.of("error", status.getReason()));
    }
    return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
  }
}
