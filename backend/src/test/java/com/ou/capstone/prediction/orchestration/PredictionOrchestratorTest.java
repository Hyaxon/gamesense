package com.ou.capstone.prediction.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ou.capstone.data.PredictionDataProvider;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.client.InvalidPredictionResponseException;
import com.ou.capstone.prediction.client.PredictionResponseDecoder;
import com.ou.capstone.prediction.client.PredictionTransportException;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.ModelExecutionError;
import com.ou.capstone.prediction.contract.ModelExecutionRequest;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionServiceResponse;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome.FailureKind;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises orchestration with canonical fixtures and an in-process fake client; no HTTP calls. */
class PredictionOrchestratorTest {
  private static final String HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  private static final String GAME_ID = "9c1e2d3b-4a5f-4e6d-8b7c-1a2b3c4d5e6f";
  private static final Matchup MATCHUP = new Matchup(HOME, AWAY, 2025, true);
  private static final PredictionSnapshot SNAPSHOT =
      new PredictionSnapshot("development-2025", 2025);
  private static final ModelSelection DETERMINISTIC =
      new ModelSelection(
          "elo-v1", ExecutionKind.DETERMINISTIC, Map.of("homeAdvantage", 0), null, null);
  private static final ModelSelection SIMULATION =
      new ModelSelection("coin-flip-v1", ExecutionKind.SIMULATION, null, 42, 10);
  private static final ErrorResponse ERROR =
      new ErrorResponse(ErrorCode.NOT_FOUND, "Required model input is unavailable.", null, null);
  private final FakeDataProvider provider = new FakeDataProvider();
  private final FakeClient client = new FakeClient();

  @Test
  void retrievesScheduledGameAndBuildsDistinctRequestsForConfiguredModels() {
    var result = orchestrator(DETERMINISTIC, SIMULATION).predictGame(GAME_ID);
    assertThat(provider.lookups)
        .containsExactly("game:" + GAME_ID, "snapshot:2025", "team:" + HOME, "team:" + AWAY);
    assertThat(result.gameId()).isEqualTo(GAME_ID);
    assertThat(result.matchup()).isEqualTo(MATCHUP);
    assertThat(result.snapshot()).isEqualTo(SNAPSHOT);
    assertThat(result.outcomes())
        .hasSize(2)
        .allMatch(ModelExecutionOutcome.Success.class::isInstance);
    assertThat(client.requests).hasSize(2);
    ModelExecutionRequest first = client.requests.get(0);
    ModelExecutionRequest second = client.requests.get(1);
    assertThat(first.modelId()).isEqualTo("elo-v1");
    assertThat(first.executionKind()).isEqualTo(ExecutionKind.DETERMINISTIC);
    assertThat(first.configuration()).containsEntry("homeAdvantage", 0);
    assertThat(first.seed()).isNull();
    assertThat(first.trials()).isNull();
    assertThat(second.modelId()).isEqualTo("coin-flip-v1");
    assertThat(second.executionKind()).isEqualTo(ExecutionKind.SIMULATION);
    assertThat(second.configuration()).isNull();
    assertThat(second.seed()).isEqualTo(42);
    assertThat(second.trials()).isEqualTo(10);
    assertThat(first.executionId()).isNotEqualTo(second.executionId());
    for (int index = 0; index < client.requests.size(); index++) {
      ModelExecutionRequest request = client.requests.get(index);
      assertThat(UUID.fromString(request.executionId()).version()).isEqualTo(4);
      assertThat(request.executionId())
          .isEqualTo(UUID.fromString(request.executionId()).toString());
      assertThat(request.matchup()).isEqualTo(MATCHUP);
      assertThat(request.dataSnapshotId()).isEqualTo(SNAPSHOT.dataSnapshotId());
      var outcome = (ModelExecutionOutcome.Success) result.outcomes().get(index);
      assertThat(outcome.executionId()).isEqualTo(request.executionId());
      assertThat(outcome.modelId()).isEqualTo(request.modelId());
      assertThat(outcome.result()).isSameAs(client.returned.get(index));
    }
  }

  @Test
  void customMatchupSkipsGameLookupAndRetainsRepeatedModels() {
    ModelSelection anotherRun =
        new ModelSelection("coin-flip-v1", ExecutionKind.SIMULATION, null, 7, 20);
    var result = orchestrator(SIMULATION, anotherRun).predictMatchup(MATCHUP);
    assertThat(result.gameId()).isNull();
    assertThat(provider.lookups).containsExactly("snapshot:2025", "team:" + HOME, "team:" + AWAY);
    assertThat(result.outcomes())
        .extracting(ModelExecutionOutcome::modelId)
        .containsExactly("coin-flip-v1", "coin-flip-v1");
    assertThat(result.outcomes())
        .extracting(ModelExecutionOutcome::executionId)
        .doesNotHaveDuplicates();
    assertThat(client.requests.get(1).seed()).isEqualTo(7);
    assertThat(client.requests.get(1).trials()).isEqualTo(20);
  }

  @Test
  void supportsStochasticExecutionAndFreshIdsAcrossCalls() {
    var orchestrator =
        orchestrator(
            new ModelSelection("ml-stochastic-v1", ExecutionKind.STOCHASTIC, null, 42, null));
    orchestrator.predictMatchup(MATCHUP);
    orchestrator.predictMatchup(MATCHUP);
    assertThat(client.requests)
        .extracting(ModelExecutionRequest::executionId)
        .doesNotHaveDuplicates();
    assertThat(client.requests.get(0).executionKind()).isEqualTo(ExecutionKind.STOCHASTIC);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "game",
        "snapshot",
        "home",
        "away",
        "wrong-game",
        "wrong-team",
        "wrong-season",
        "blank-snapshot",
        "unknown-location"
      })
  void rejectsUnavailableOrInconsistentDataBeforeAnyExecution(String problem) {
    switch (problem) {
      case "game" -> provider.game = null;
      case "snapshot" -> provider.snapshot = null;
      case "home" -> provider.teams.remove(HOME);
      case "away" -> provider.teams.remove(AWAY);
      case "wrong-game" -> provider.game = game(UUID.randomUUID().toString(), true);
      case "wrong-team" -> provider.teams.put(HOME, team(AWAY));
      case "wrong-season" -> provider.snapshot = new PredictionSnapshot("other", 2026);
      case "blank-snapshot" -> provider.snapshot = new PredictionSnapshot(" ", 2025);
      case "unknown-location" -> provider.game = game(GAME_ID, null);
      default -> throw new AssertionError(problem);
    }
    assertThatThrownBy(() -> orchestrator(SIMULATION).predictGame(GAME_ID))
        .isInstanceOf(PredictionInputException.class);
    assertThat(client.requests).isEmpty();
    if (problem.equals("wrong-season") || problem.equals("blank-snapshot")) {
      assertThat(provider.lookups).containsExactly("game:" + GAME_ID, "snapshot:2025");
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid"})
  void rejectsInvalidGameIdsBeforeLookup(String gameId) {
    assertThatThrownBy(() -> orchestrator(SIMULATION).predictGame(gameId))
        .isInstanceOf(PredictionInputException.class);
    assertThat(provider.lookups).isEmpty();
    assertThat(client.requests).isEmpty();
  }

  @Test
  void rejectsInvalidMatchupBeforeLookup() {
    assertThatThrownBy(
            () -> orchestrator(SIMULATION).predictMatchup(new Matchup(HOME, HOME, 2025, true)))
        .isInstanceOf(PredictionInputException.class);
    assertThat(provider.lookups).isEmpty();
    assertThat(client.requests).isEmpty();
  }

  @Test
  void rejectsEntireInvalidSelectionPlanBeforeCalls() {
    ModelSelection invalid =
        new ModelSelection("coin-flip-v1", ExecutionKind.SIMULATION, null, 42, 0);
    assertThatThrownBy(() -> orchestrator(DETERMINISTIC, invalid))
        .isInstanceOf(PredictionInputException.class);
    assertThatThrownBy(() -> orchestrator()).isInstanceOf(PredictionInputException.class);
    assertThat(client.requests).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(value = FailureKind.class)
  void retainsPartialFailureAndContinuesWithNextModel(FailureKind kind) {
    client.execution =
        request -> client.requests.size() == 2 ? fail(request, kind) : success(request);
    var result = orchestrator(DETERMINISTIC, SIMULATION, DETERMINISTIC).predictMatchup(MATCHUP);
    assertThat(client.requests).hasSize(3);
    assertThat(result.outcomes().get(0)).isInstanceOf(ModelExecutionOutcome.Success.class);
    assertThat(result.outcomes().get(2)).isInstanceOf(ModelExecutionOutcome.Success.class);
    var failure = (ModelExecutionOutcome.Failure) result.outcomes().get(1);
    assertThat(failure.kind()).isEqualTo(kind);
    assertThat(failure.executionId()).isEqualTo(client.requests.get(1).executionId());
    assertThat(failure.modelId()).isEqualTo(client.requests.get(1).modelId());
    assertThat(failure.message()).doesNotContain("private diagnostic");
    if (kind == FailureKind.MODEL_ERROR || kind == FailureKind.BOUNDARY_ERROR) {
      assertThat(failure.serviceError()).isSameAs(ERROR);
    } else {
      assertThat(failure.serviceError()).isNull();
    }
  }

  @Test
  void returnsAllFailedOutcomesWithoutInventingPredictionsOrRetrying() {
    client.execution = request -> fail(request, FailureKind.CONNECTION_FAILURE);
    var result = orchestrator(DETERMINISTIC, SIMULATION).predictMatchup(MATCHUP);
    assertThat(client.requests).hasSize(2);
    assertThat(result.outcomes())
        .hasSize(2)
        .allMatch(ModelExecutionOutcome.Failure.class::isInstance);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "execution",
        "model",
        "matchup",
        "snapshot",
        "kind",
        "error-execution",
        "error-model",
        "null"
      })
  void rejectsMisassociatedResponsesAsIndividualFailures(String problem) {
    client.execution =
        request -> {
          if (client.requests.size() > 1) {
            return success(request);
          }
          if (problem.equals("null")) {
            return null;
          }
          if (problem.startsWith("error-")) {
            return new ModelExecutionError(
                "ERROR",
                problem.equals("error-execution")
                    ? UUID.randomUUID().toString()
                    : request.executionId(),
                problem.equals("error-model") ? "other-model" : request.modelId(),
                ERROR);
          }
          ModelExecutionRequest wrongRequest =
              new ModelExecutionRequest(
                  problem.equals("execution")
                      ? UUID.randomUUID().toString()
                      : request.executionId(),
                  problem.equals("model") ? "other-model" : request.modelId(),
                  problem.equals("matchup")
                      ? new Matchup(AWAY, HOME, 2025, true)
                      : request.matchup(),
                  problem.equals("snapshot") ? "other-snapshot" : request.dataSnapshotId(),
                  problem.equals("kind") ? ExecutionKind.STOCHASTIC : request.executionKind(),
                  null,
                  null,
                  null);
          return success(wrongRequest);
        };
    var result = orchestrator(DETERMINISTIC, SIMULATION).predictMatchup(MATCHUP);
    assertThat(((ModelExecutionOutcome.Failure) result.outcomes().get(0)).kind())
        .isEqualTo(FailureKind.INVALID_RESPONSE);
    assertThat(result.outcomes().get(1)).isInstanceOf(ModelExecutionOutcome.Success.class);
  }

  @Test
  void interruptionStopsSchedulingAndPreservesFlag() {
    client.execution =
        request -> {
          throw new PredictionTransportException(
              PredictionTransportException.Reason.INTERRUPTED, "Interrupted.", null);
        };
    try {
      assertThatThrownBy(() -> orchestrator(DETERMINISTIC, SIMULATION).predictMatchup(MATCHUP))
          .isInstanceOfSatisfying(
              PredictionTransportException.class,
              error ->
                  assertThat(error.reason())
                      .isEqualTo(PredictionTransportException.Reason.INTERRUPTED));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(client.requests).hasSize(1);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void alreadyInterruptedCallerDoesNoLookupsOrExecutions() {
    var orchestrator = orchestrator(SIMULATION);
    try {
      Thread.currentThread().interrupt();
      assertThatThrownBy(() -> orchestrator.predictGame(GAME_ID))
          .isInstanceOf(PredictionTransportException.class);
      assertThat(provider.lookups).isEmpty();
      assertThat(client.requests).isEmpty();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private PredictionOrchestrator orchestrator(ModelSelection... selections) {
    return new PredictionOrchestrator(provider, client, List.of(selections));
  }

  private static PredictionServiceResponse fail(ModelExecutionRequest request, FailureKind kind)
      throws IOException {
    return switch (kind) {
      case MODEL_ERROR ->
          new ModelExecutionError("ERROR", request.executionId(), request.modelId(), ERROR);
      case BOUNDARY_ERROR -> ERROR;
      case TIMEOUT ->
          throw new PredictionTransportException(
              PredictionTransportException.Reason.TIMEOUT, "private diagnostic", null);
      case CONNECTION_FAILURE ->
          throw new PredictionTransportException(
              PredictionTransportException.Reason.CONNECTION_FAILURE, "private diagnostic", null);
      case INVALID_RESPONSE -> throw new InvalidPredictionResponseException("private diagnostic");
      case SERIALIZATION_FAILURE -> throw new IOException("private diagnostic");
    };
  }

  private static ModelExecutionResult success(ModelExecutionRequest request) throws IOException {
    String example =
        switch (request.executionKind()) {
          case DETERMINISTIC -> "deterministic";
          case STOCHASTIC -> "stochastic";
          case SIMULATION -> "simulation";
        };
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode body =
        (ObjectNode)
            mapper.readTree(
                Path.of("../shared/examples/model-execution", example + "-result.json").toFile());
    body.put("executionId", request.executionId());
    ((ObjectNode) body.get("model")).put("modelId", request.modelId());
    body.set("matchup", mapper.valueToTree(request.matchup()));
    ((ObjectNode) body.get("prediction"))
        .put("predictedWinnerTeamId", request.matchup().homeTeamId());
    body.remove("supportingScores");
    ObjectNode metadata = (ObjectNode) body.get("metadata");
    metadata.put("dataSnapshotId", request.dataSnapshotId());
    metadata.remove("seed");
    if (request.seed() != null) {
      metadata.put("seed", request.seed());
    }
    if (body.has("simulation")) {
      ((ObjectNode) body.get("simulation")).put("trials", request.trials());
    }
    return (ModelExecutionResult) new PredictionResponseDecoder().decode(body.toString(), request);
  }

  private static Team team(String id) {
    return new Team(id, null, "Fixture team", null, "Fixture conference", null);
  }

  private static Game game(String id, Boolean isNeutralSite) {
    return new Game(
        id,
        null,
        2025,
        null,
        HOME,
        AWAY,
        null,
        isNeutralSite,
        Instant.parse("2025-10-01T12:00:00Z"),
        GameStatus.SCHEDULED,
        null,
        null,
        null);
  }

  private static final class FakeDataProvider implements PredictionDataProvider {
    private Game game = game(GAME_ID, true);
    private PredictionSnapshot snapshot = SNAPSHOT;
    private final Map<String, Team> teams =
        new HashMap<>(Map.of(HOME, team(HOME), AWAY, team(AWAY)));
    private final List<String> lookups = new ArrayList<>();

    @Override
    public Optional<Game> findGame(String gameId) {
      lookups.add("game:" + gameId);
      return Optional.ofNullable(game);
    }

    @Override
    public Optional<PredictionSnapshot> findSnapshot(int season) {
      lookups.add("snapshot:" + season);
      return Optional.ofNullable(snapshot);
    }

    @Override
    public Optional<Team> findTeam(PredictionSnapshot selected, String teamId) {
      assertThat(selected).isSameAs(snapshot);
      lookups.add("team:" + teamId);
      return Optional.ofNullable(teams.get(teamId));
    }
  }

  @FunctionalInterface
  private interface Execution {
    PredictionServiceResponse execute(ModelExecutionRequest request) throws IOException;
  }

  private static final class FakeClient extends PythonPredictionClient {
    private final List<ModelExecutionRequest> requests = new ArrayList<>();
    private final List<PredictionServiceResponse> returned = new ArrayList<>();
    private Execution execution = PredictionOrchestratorTest::success;

    private FakeClient() {
      super(
          new ObjectMapper(),
          URI.create("http://127.0.0.1:1"),
          Duration.ofSeconds(1),
          Duration.ofSeconds(1));
    }

    @Override
    public PredictionServiceResponse execute(ModelExecutionRequest request) throws IOException {
      requests.add(request);
      PredictionServiceResponse response = execution.execute(request);
      returned.add(response);
      return response;
    }
  }
}
