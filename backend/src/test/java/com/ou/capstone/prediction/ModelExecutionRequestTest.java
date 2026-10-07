package com.ou.capstone.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ModelExecutionRequestTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @ParameterizedTest
  @CsvSource({
    "deterministic, elo-v1, DETERMINISTIC",
    "stochastic, ml-stochastic-v1, STOCHASTIC",
    "simulation, monte-carlo-v1, SIMULATION"
  })
  void serializesExactlyAsSharedExample(String example, String modelId, ExecutionKind kind)
      throws Exception {
    ModelExecutionRequest request =
        request(
            modelId,
            kind,
            kind == ExecutionKind.DETERMINISTIC ? Map.of("homeAdvantage", 0) : Map.of(),
            kind == ExecutionKind.DETERMINISTIC ? null : 42,
            kind == ExecutionKind.SIMULATION ? 10000 : null);

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(request));

    // Compare objects, not JSON strings: property order is not part of the contract.
    assertThat(actual).isEqualTo(sharedExample(example));
  }

  @Test
  void omitsAbsentOptionalFields() throws Exception {
    ModelExecutionRequest request =
        request("elo-v1", ExecutionKind.DETERMINISTIC, null, null, null);
    ObjectNode expected = (ObjectNode) sharedExample("deterministic");
    expected.remove("configuration");

    JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(request));

    assertThat(actual).isEqualTo(expected);
    assertThat(actual.has("configuration")).isFalse();
    assertThat(actual.has("seed")).isFalse();
    assertThat(actual.has("trials")).isFalse();
  }

  private ModelExecutionRequest request(
      String modelId,
      ExecutionKind kind,
      Map<String, Object> configuration,
      Integer seed,
      Integer trials) {
    return new ModelExecutionRequest(
        "a1b2c3d4-e5f6-4789-8abc-1234567890ab",
        modelId,
        new Matchup(
            "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
            "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
            2025,
            true),
        "season-2025-snapshot-2025-08-29",
        kind,
        configuration,
        seed,
        trials);
  }

  private JsonNode sharedExample(String name) throws Exception {
    return objectMapper.readTree(
        Path.of("../shared/examples/model-execution", name + "-request.json").toFile());
  }
}
