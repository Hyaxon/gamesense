package com.ou.capstone.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises response decoding with shared fixtures and deliberately invalid payloads.
 *
 * <p>Covers response shape, strict field types, request correlation, and cross-field invariants.
 */
class PredictionResponseDecoderTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final PredictionResponseDecoder decoder = new PredictionResponseDecoder();

  @ParameterizedTest
  @ValueSource(strings = {"deterministic", "stochastic", "simulation"})
  void decodesSharedSuccessExamples(String example) throws Exception {
    ModelExecutionRequest request = request(example);
    ModelExecutionResult result =
        (ModelExecutionResult) decoder.decode(example(example, "result"), request);
    assertThat(result.executionId()).isEqualTo(request.executionId());
    assertThat(result.model().modelId()).isEqualTo(request.modelId());
    assertThat(result.matchup()).isEqualTo(request.matchup());
    assertThat(result.metadata().executionKind()).isEqualTo(request.executionKind());
    assertThat(result.prediction().generatedAt()).isEqualTo(Instant.parse("2025-08-29T12:00:00Z"));
    assertThat(result.model().version()).isEqualTo("1.0.0");
    if (example.equals("simulation")) {
      assertThat(result.simulation().trials()).isEqualTo(10000);
      assertThat(result.simulation().homeWinProbability()).isEqualTo(0.71);
      assertThat(result.metadata().seed()).isEqualTo(42);
    } else {
      assertThat(result.simulation()).isNull();
    }
    if (example.equals("deterministic")) {
      assertThat(result.supportingScores()).hasSize(2);
      assertThat(result.configuration()).containsEntry("homeAdvantage", 0);
    }
  }

  @Test
  void decodesCorrelatedAndPlainErrors() throws Exception {
    String body = Files.readString(Path.of("../shared/examples/model-execution-error.json"));
    ModelExecutionError result =
        (ModelExecutionError) decoder.decode(body, request("deterministic"));
    assertThat(result.error().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(result.error().details().get(0).path()).isEqualTo("/configuration/homeAdvantage");
    ErrorResponse plain =
        (ErrorResponse)
            decoder.decode(mapper.writeValueAsString(result.error()), request("deterministic"));
    assertThat(plain).isEqualTo(result.error());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "null",
        "[]",
        "42",
        "<html>error</html>",
        "{",
        "{}",
        "{\"code\":\"VALIDATION_ERROR\",\"message\":\"bad\",\"message\":\"duplicate\"}",
        "{\"code\":\"VALIDATION_ERROR\",\"message\":\"bad\"} {}",
        "{\"code\":\"UNKNOWN\",\"message\":\"bad\"}",
        "{\"code\":\"NOT_FOUND\",\"message\":null}",
        "{\"code\":\"NOT_FOUND\",\"message\":\"bad\",\"details\":[]}"
      })
  void rejectsInvalidBodies(String body) throws Exception {
    reject(body, request("deterministic"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "/status | \"OTHER\"",
        "/executionId | \"b1b2c3d4-e5f6-4789-8abc-1234567890ab\"",
        "/model/modelId | \"other-model\"",
        "/model/method | \"OTHER\"",
        "/model/displayName | \"  \"",
        "/model/supportedExecutionKinds | []",
        "/model/supportedExecutionKinds | [\"SIMULATION\",\"SIMULATION\"]",
        "/model/supportedExecutionKinds | [\"DETERMINISTIC\"]",
        "/matchup/season | 2024",
        "/matchup/season | \"2025\"",
        "/matchup/isNeutralSite | 1",
        "/metadata/dataSnapshotId | \"other-snapshot\"",
        "/metadata/executionKind | \"STOCHASTIC\"",
        "/metadata/durationMilliseconds | -1",
        "/metadata/durationMilliseconds | 1e999",
        "/metadata/seed | 43",
        "/metadata/seed | 42.0",
        "/metadata/completedAt | \"2025-08-28T12:00:00Z\"",
        "/metadata/startedAt | \"2025-08-29T12:00:00+00:00\"",
        "/prediction/confidence | null",
        "/prediction/confidence | \"0.71\"",
        "/prediction/confidence | true",
        "/prediction/confidence | 1.1",
        "/prediction/confidence | 0.4",
        "/prediction/method | \"ELO\"",
        "/prediction/id | \"not-a-uuid\"",
        "/prediction/generatedAt | \"invalid\"",
        "/prediction/predictedWinnerTeamId | \"a1b2c3d4-e5f6-4789-8abc-1234567890ab\"",
        "/prediction/predictedHomeScore | -1",
        "/prediction/predictedAwayScore | 1.5",
        "/simulation | null",
        "/simulation/trials | 9999",
        "/simulation/trials | 2147483648",
        "/simulation/homeWinProbability | 0.8",
        "/simulation/awayWinProbability | -0.1",
        "/simulation/id | \"7f6e5d4c-3b2a-4190-8b7c-6a5f4e3d2c1b\"",
        "/simulation/method | \"ELO\"",
        "/configuration | []",
        "/configuration | {\"seed\":42}",
        "/supportingScores | []",
        "/unexpected | true"
      })
  void rejectsInvalidFields(String pointer, String replacement) throws Exception {
    ObjectNode body = result("simulation");
    int split = pointer.lastIndexOf('/');
    ObjectNode parent = (ObjectNode) body.at(pointer.substring(0, split));
    parent.set(pointer.substring(split + 1), mapper.readTree(replacement));
    reject(body.toString(), request("simulation"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/model",
        "/prediction",
        "/metadata",
        "/simulation",
        "/prediction/confidence",
        "/metadata/durationMilliseconds",
        "/matchup/isNeutralSite",
        "/simulation/trials",
        "/metadata/seed"
      })
  void rejectsMissingRequiredFields(String pointer) throws Exception {
    ObjectNode body = result("simulation");
    int split = pointer.lastIndexOf('/');
    ((ObjectNode) body.at(pointer.substring(0, split))).remove(pointer.substring(split + 1));
    reject(body.toString(), request("simulation"));
  }

  @Test
  void rejectsSimulationAndSeedOnDeterministicResults() throws Exception {
    ObjectNode body = result("deterministic");
    body.set("simulation", result("simulation").get("simulation"));
    reject(body.toString(), request("deterministic"));
    body.remove("simulation");
    ((ObjectNode) body.get("metadata")).put("seed", 42);
    reject(body.toString(), request("deterministic"));
  }

  @Test
  void preservesOptionalScoresAndNegativeDiagnostics() throws Exception {
    ObjectNode body = result("deterministic");
    ((ObjectNode) body.get("prediction"))
        .put("predictedHomeScore", 80)
        .put("predictedAwayScore", 70);
    ((ObjectNode) body.at("/supportingScores/0")).put("value", -12.5);
    ModelExecutionResult decoded =
        (ModelExecutionResult) decoder.decode(body.toString(), request("deterministic"));
    assertThat(decoded.prediction().predictedHomeScore()).isEqualTo(80);
    assertThat(decoded.prediction().predictedAwayScore()).isEqualTo(70);
    assertThat(decoded.supportingScores().get(0).value()).isEqualTo(-12.5);
  }

  @Test
  void rejectsDuplicateDiagnosticsAndChangedConfiguration() throws Exception {
    ObjectNode body = result("deterministic");
    ((ObjectNode) body.at("/supportingScores/1"))
        .put("teamId", body.at("/supportingScores/0/teamId").textValue());
    reject(body.toString(), request("deterministic"));
    body = result("deterministic");
    ((ObjectNode) body.get("configuration")).put("homeAdvantage", 1);
    reject(body.toString(), request("deterministic"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"modelId", "executionId"})
  void rejectsMismatchedErrorCorrelation(String field) throws Exception {
    ObjectNode body =
        (ObjectNode)
            mapper.readTree(
                Files.readString(Path.of("../shared/examples/model-execution-error.json")));
    body.put(field, field.equals("modelId") ? "other" : "b1b2c3d4-e5f6-4789-8abc-1234567890ab");
    reject(body.toString(), request("deterministic"));
  }

  @Test
  void acceptsHomeTieButRejectsAwayTie() throws Exception {
    ObjectNode body = result("simulation");
    ((ObjectNode) body.get("simulation"))
        .put("homeWinProbability", 0.5)
        .put("awayWinProbability", 0.5);
    ((ObjectNode) body.get("prediction")).put("confidence", 0.5);
    assertThat(decoder.decode(body.toString(), request("simulation")))
        .isInstanceOf(ModelExecutionResult.class);
    ((ObjectNode) body.get("prediction"))
        .put("predictedWinnerTeamId", body.at("/matchup/awayTeamId").textValue());
    reject(body.toString(), request("simulation"));
  }

  private void reject(String body, ModelExecutionRequest request) {
    assertThatThrownBy(() -> decoder.decode(body, request))
        .isInstanceOf(InvalidPredictionResponseException.class)
        .hasMessage("Prediction service returned an invalid response.");
  }

  private ObjectNode result(String name) throws Exception {
    return (ObjectNode) mapper.readTree(example(name, "result"));
  }

  private ModelExecutionRequest request(String name) throws Exception {
    return mapper.readValue(example(name, "request"), ModelExecutionRequest.class);
  }

  private String example(String name, String suffix) throws Exception {
    return Files.readString(
        Path.of("../shared/examples/model-execution", name + "-" + suffix + ".json"));
  }
}
