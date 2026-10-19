package com.ou.capstone.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.ExecutionMetadata;
import com.ou.capstone.prediction.contract.ModelDescriptor;
import com.ou.capstone.prediction.contract.ModelExecutionError;
import com.ou.capstone.prediction.contract.ModelExecutionRequest;
import com.ou.capstone.prediction.contract.ModelExecutionResult;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.contract.PredictionServiceResponse;
import com.ou.capstone.prediction.contract.SimulationOutcome;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScheduleDemoTest {
  @TempDir Path temporary;

  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private ScheduleDemoData ingest() throws Exception {
    var source = temporary.resolve("schedule.json");
    Files.writeString(
        source,
        """
        {"teams":[
          {"name":"Home","league":"Conference","schedule":[
            {"timestamp":"09/01/2025","location":"vs.","opponent":"Away",
             "outcome":"W","pointsScored":"21","pointsAllowed":"7"},
            {"timestamp":"10/01/2025","location":"vs.","opponent":"Away",
             "outcome":"L","pointsScored":"7","pointsAllowed":"28"},
            {"timestamp":"10/02/2025","location":"vs.","opponent":"Missing",
             "outcome":"W","pointsScored":"21","pointsAllowed":"7"}]},
          {"name":"Away","league":"Conference","schedule":[]}
        ]}
        """);
    return ScheduleDemoData.ingest(source, 2025, LocalDate.parse("2025-10-01"));
  }

  @Test
  void exportsOnlyPreCutoffHistoryAndPreservesIdentitiesAcrossProcesses() throws Exception {
    var data = ingest();
    var directory = temporary.resolve("demo");
    data.write(directory, mapper);
    var reloaded = ScheduleDemoData.read(directory, mapper);
    assertThat(reloaded).isEqualTo(data);
    assertThat(data.skipped()).hasSize(1);
    var snapshot = mapper.readTree(directory.resolve("snapshot.json").toFile());
    assertThat(snapshot.get("games").size()).isEqualTo(1);
    assertThat(snapshot.at("/games/0/id").asText()).isEqualTo(data.history().get(0).id());
    assertThat(snapshot.at("/games/0/scheduledAt").asText()).isEqualTo("2025-09-01T00:00:00Z");
    assertThat(data.findGame(data.history().get(0).id())).isEmpty();
    assertThat(data.findGame(data.games().get(0).id())).isPresent();
    assertThat(data.games().get(0).scheduledAt()).isEqualTo(data.cutoff());
    assertThat(data.findSnapshot(2024)).isEmpty();
    assertThat(data.findTeam(new PredictionSnapshot("different", 2025), data.teams().get(0).id()))
        .isEmpty();
    assertThatThrownBy(() -> data.write(directory, mapper))
        .isInstanceOf(FileAlreadyExistsException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "elo-v1,ELO,DETERMINISTIC",
    "glicko2,GLICKO2,DETERMINISTIC",
    "bradley-terry-v1,BRADLEY_TERRY,DETERMINISTIC",
    "monte-carlo-v1,MONTE_CARLO,SIMULATION",
    "coin-flip-v1,RANDOM,SIMULATION",
    "xgboost-v1,MACHINE_LEARNING,DETERMINISTIC"
  })
  void httpRequestRunsSelectedGameAndReturnsNamedWinnerOrVisibleFailure(
      String modelId, PredictionMethod method, ExecutionKind kind) throws Exception {
    var data = ingest();
    var client = new FakeClient();
    client.response =
        request -> {
          assertThat(request.modelId()).isEqualTo(modelId);
          assertThat(request.executionKind()).isEqualTo(kind);
          assertThat(request.trials()).isEqualTo(kind == ExecutionKind.SIMULATION ? 10000 : null);
          assertThat(request.seed()).isEqualTo(kind == ExecutionKind.SIMULATION ? 42 : null);
          assertThat(request.dataSnapshotId()).isEqualTo(data.dataSnapshotId());
          assertThat(request.matchup().homeTeamId()).isEqualTo(data.games().get(0).homeTeamId());
          Instant now = Instant.now();
          return new ModelExecutionResult(
              "SUCCESS",
              request.executionId(),
              new ModelDescriptor(modelId, modelId, method, "1.0.0", List.of(kind), null),
              request.matchup(),
              new SinglePredictionResult(
                  UUID.randomUUID().toString(),
                  method,
                  request.matchup().homeTeamId(),
                  0.7,
                  now,
                  null,
                  null),
              new ExecutionMetadata(kind, data.dataSnapshotId(), now, now, 0, request.seed()),
              kind == ExecutionKind.SIMULATION
                  ? new SimulationOutcome(
                      UUID.randomUUID().toString(), method, request.trials(), 0.7, 0.3, now)
                  : null,
              List.of(),
              null);
        };
    var mvc = MockMvcBuilders.standaloneSetup(new ScheduleDemoController(data, client)).build();
    mvc.perform(get("/demo/models"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[3].modelId").value("monte-carlo-v1"))
        .andExpect(jsonPath("$[3].trials").value(10000));
    mvc.perform(get("/demo/games").param("team", "Home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].gameId").value(data.games().get(0).id()))
        .andExpect(jsonPath("$[0].homeTeam").value("Home"));
    var body =
        mapper.writeValueAsString(Map.of("modelId", modelId, "gameId", data.games().get(0).id()));
    mvc.perform(post("/demo/predictions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.execution.outcomes[0].result.model.modelId").value(modelId))
        .andExpect(
            jsonPath("$.execution.outcomes[0].result.prediction.method").value(method.name()))
        .andExpect(jsonPath("$.predictedWinner").value("Home"))
        .andExpect(jsonPath("$.execution.outcomes[0].result.prediction.confidence").value(0.7));
    client.response =
        request -> {
          return new ModelExecutionError(
              "ERROR",
              request.executionId(),
              request.modelId(),
              new ErrorResponse(ErrorCode.NOT_FOUND, "History unavailable", null, null));
        };
    mvc.perform(post("/demo/predictions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.execution.outcomes[0].serviceError.code").value("NOT_FOUND"));
  }

  @Test
  void rejectsMissingInputsUnsupportedModelsAndGamesAlreadyInHistory() throws Exception {
    var data = ingest();
    var mvc =
        MockMvcBuilders.standaloneSetup(new ScheduleDemoController(data, new FakeClient())).build();
    for (String body :
        List.of(
            "{}",
            "{\"modelId\":\"elo-v1\"}",
            "{\"modelId\":\"unknown\",\"gameId\":\"anything\"}")) {
      mvc.perform(post("/demo/predictions").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/demo/predictions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of("modelId", "elo-v1", "gameId", data.history().get(0).id()))))
        .andExpect(status().isNotFound());
  }

  private static final class FakeClient extends PythonPredictionClient {
    private Function<ModelExecutionRequest, PredictionServiceResponse> response =
        request -> {
          throw new AssertionError("Invalid input must not call Python");
        };

    FakeClient() {
      super(
          new ObjectMapper(),
          URI.create("http://127.0.0.1:8000"),
          Duration.ofSeconds(1),
          Duration.ofSeconds(1));
    }

    @Override
    public PredictionServiceResponse execute(ModelExecutionRequest request) {
      return response.apply(request);
    }
  }
}
