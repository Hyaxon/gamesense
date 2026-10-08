package com.ou.capstone.prediction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Strictly validates Python response JSON before constructing typed backend records.
 *
 * <p>Distinguishes success, correlated model errors, and plain boundary errors; checks shared
 * structural and semantic rules against the original request. Model-specific validation remains
 * owned by Python.
 */
public final class PredictionResponseDecoder {
  private static final double TOLERANCE = 0.000001;
  private final JsonMapper mapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  public PredictionServiceResponse decode(String body, ModelExecutionRequest request) {
    Objects.requireNonNull(request, "request");
    if (body == null || body.isBlank()) {
      throw invalid();
    }
    try {
      JsonNode root = mapper.readTree(body);
      require(root != null && root.isObject());
      if (!root.has("status")) {
        error(root);
        return mapper.treeToValue(root, ErrorResponse.class);
      }
      String status = text(root, "status");
      if (status.equals("ERROR")) {
        object(root, "status executionId modelId error", "");
        correlation(root, request);
        require(text(root, "modelId").equals(request.modelId()));
        error(root.get("error"));
        return mapper.treeToValue(root, ModelExecutionError.class);
      }
      require(status.equals("SUCCESS"));
      success(root, request);
      return mapper.treeToValue(root, ModelExecutionResult.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      // Never expose response bodies, internal paths, or parser diagnostics to callers.
      throw invalid();
    }
  }

  private void success(JsonNode root, ModelExecutionRequest request) {
    object(
        root,
        "status executionId model matchup prediction metadata",
        "simulation supportingScores configuration");
    correlation(root, request);
    JsonNode model = root.get("model");
    object(
        model,
        "modelId displayName method version supportedExecutionKinds",
        "configurationSchemaId");
    require(text(model, "modelId").equals(request.modelId()));
    text(model, "displayName");
    text(model, "version");
    PredictionMethod method = PredictionMethod.valueOf(text(model, "method"));
    if (model.has("configurationSchemaId")) {
      text(model, "configurationSchemaId");
    }
    JsonNode kinds = array(model, "supportedExecutionKinds");
    Set<ExecutionKind> supported = new HashSet<>();
    for (JsonNode kind : kinds) {
      require(kind.isTextual());
      require(supported.add(ExecutionKind.valueOf(kind.textValue())));
    }
    require(supported.contains(request.executionKind()));

    JsonNode matchup = root.get("matchup");
    object(matchup, "homeTeamId awayTeamId season isNeutralSite", "");
    String home = identifier(matchup, "homeTeamId");
    String away = identifier(matchup, "awayTeamId");
    require(!home.equals(away));
    integer(matchup, "season", 1, Integer.MAX_VALUE);
    require(matchup.get("isNeutralSite").isBoolean());
    require(matchup.equals(mapper.valueToTree(request.matchup())));

    JsonNode metadata = root.get("metadata");
    object(
        metadata,
        "executionKind dataSnapshotId startedAt completedAt durationMilliseconds",
        "seed");
    ExecutionKind kind = ExecutionKind.valueOf(text(metadata, "executionKind"));
    require(kind == request.executionKind());
    require(text(metadata, "dataSnapshotId").equals(request.dataSnapshotId()));
    Instant started = timestamp(metadata, "startedAt");
    require(!timestamp(metadata, "completedAt").isBefore(started));
    require(number(metadata, "durationMilliseconds") >= 0);
    if (metadata.has("seed")) {
      require(kind != ExecutionKind.DETERMINISTIC);
      integer(metadata, "seed", 0, Integer.MAX_VALUE);
    }
    if (request.seed() != null) {
      require(integer(metadata, "seed", 0, Integer.MAX_VALUE) == request.seed());
    }

    JsonNode prediction = root.get("prediction");
    object(
        prediction,
        "id method predictedWinnerTeamId confidence generatedAt",
        "predictedHomeScore predictedAwayScore");
    String predictionId = identifier(prediction, "id");
    require(PredictionMethod.valueOf(text(prediction, "method")) == method);
    String winner = identifier(prediction, "predictedWinnerTeamId");
    require(winner.equals(home) || winner.equals(away));
    double confidence = probability(prediction, "confidence");
    require(confidence >= 0.5);
    require(confidence != 0.5 || winner.equals(home));
    timestamp(prediction, "generatedAt");
    for (String field : new String[] {"predictedHomeScore", "predictedAwayScore"}) {
      if (prediction.has(field)) {
        integer(prediction, field, 0, Integer.MAX_VALUE);
      }
    }

    require(root.has("simulation") == (kind == ExecutionKind.SIMULATION));
    if (root.has("simulation")) {
      JsonNode simulation = root.get("simulation");
      object(simulation, "id method trials homeWinProbability awayWinProbability simulatedAt", "");
      require(!identifier(simulation, "id").equals(predictionId));
      require(PredictionMethod.valueOf(text(simulation, "method")) == method);
      require(Objects.equals(integer(simulation, "trials", 1, 100000), request.trials()));
      double homeProbability = probability(simulation, "homeWinProbability");
      double awayProbability = probability(simulation, "awayWinProbability");
      require(Math.abs(homeProbability + awayProbability - 1) <= TOLERANCE);
      require(winner.equals(homeProbability >= awayProbability ? home : away));
      require(Math.abs(confidence - Math.max(homeProbability, awayProbability)) <= TOLERANCE);
      timestamp(simulation, "simulatedAt");
    }
    if (root.has("supportingScores")) {
      Set<String> keys = new HashSet<>();
      for (JsonNode score : array(root, "supportingScores")) {
        object(score, "teamId name value", "unit");
        String team = identifier(score, "teamId");
        require(team.equals(home) || team.equals(away));
        require(keys.add(team + ":" + text(score, "name")));
        number(score, "value");
        if (score.has("unit")) {
          text(score, "unit");
        }
      }
    }
    if (root.has("configuration")) {
      JsonNode configuration = root.get("configuration");
      require(configuration.isObject());
      require(!configuration.has("seed") && !configuration.has("trials"));
      finiteJson(configuration);
      require(configuration.isEmpty() || model.has("configurationSchemaId"));
    }
    if (request.configuration() != null && !request.configuration().isEmpty()) {
      JsonNode effective = root.get("configuration");
      require(effective != null);
      request
          .configuration()
          .forEach(
              (key, value) ->
                  require(Objects.equals(effective.get(key), mapper.valueToTree(value))));
    }
  }

  private static void correlation(JsonNode root, ModelExecutionRequest request) {
    require(identifier(root, "executionId").equals(request.executionId()));
  }

  private static void error(JsonNode node) {
    object(node, "code message", "requestId details");
    ErrorCode.valueOf(text(node, "code"));
    text(node, "message");
    if (node.has("requestId")) {
      text(node, "requestId");
    }
    if (node.has("details")) {
      for (JsonNode detail : array(node, "details")) {
        object(detail, "path message", "");
        JsonNode path = detail.get("path");
        require(path.isTextual());
        require(path.textValue().isEmpty() || path.textValue().startsWith("/"));
        text(detail, "message");
      }
    }
  }

  private static void object(JsonNode node, String required, String optional) {
    require(node != null && node.isObject());
    Set<String> allowed = new HashSet<>();
    for (String field : required.split(" ")) {
      require(node.hasNonNull(field));
      allowed.add(field);
    }
    for (String field : optional.split(" ")) {
      allowed.add(field);
    }
    node.fields()
        .forEachRemaining(
            entry -> {
              require(allowed.contains(entry.getKey()));
              require(!entry.getValue().isNull());
            });
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    require(value != null && value.isTextual() && !value.textValue().isBlank());
    return value.textValue();
  }

  private static String identifier(JsonNode node, String field) {
    String value = text(node, field);
    UUID id = UUID.fromString(value);
    require(id.version() == 4 && id.toString().equals(value));
    return value;
  }

  private static Instant timestamp(JsonNode node, String field) {
    String value = text(node, field);
    require(value.endsWith("Z"));
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw invalid();
    }
  }

  private static int integer(JsonNode node, String field, int minimum, int maximum) {
    JsonNode value = node.get(field);
    require(value != null && value.isIntegralNumber() && value.canConvertToInt());
    int result = value.intValue();
    require(result >= minimum && result <= maximum);
    return result;
  }

  private static double number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    require(value != null && value.isNumber() && Double.isFinite(value.doubleValue()));
    return value.doubleValue();
  }

  private static double probability(JsonNode node, String field) {
    double value = number(node, field);
    require(value >= 0 && value <= 1);
    return value;
  }

  private static JsonNode array(JsonNode node, String field) {
    JsonNode value = node.get(field);
    require(value != null && value.isArray() && !value.isEmpty());
    return value;
  }

  private static void finiteJson(JsonNode node) {
    if (node.isNumber()) {
      require(Double.isFinite(node.doubleValue()));
    }
    node.forEach(PredictionResponseDecoder::finiteJson);
  }

  private static void require(boolean condition) {
    if (!condition) {
      throw invalid();
    }
  }

  private static InvalidPredictionResponseException invalid() {
    return new InvalidPredictionResponseException(
        "Prediction service returned an invalid response.");
  }
}
