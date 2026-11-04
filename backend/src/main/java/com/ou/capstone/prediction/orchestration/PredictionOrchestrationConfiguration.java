package com.ou.capstone.prediction.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ou.capstone.data.PredictionDataProvider;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import com.ou.capstone.prediction.contract.ExecutionKind;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in orchestration wiring; enabling it requires a canonical data-provider bean. */
@Configuration
@ConditionalOnProperty(prefix = "prediction.orchestration", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PredictionOrchestrationConfiguration.Properties.class)
public class PredictionOrchestrationConfiguration {
  private static final JsonMapper CONFIGURATION_MAPPER =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  @ConfigurationProperties(prefix = "prediction.orchestration")
  public record Properties(List<ConfiguredModel> models) {
    List<ModelSelection> toSelections() {
      List<ModelSelection> selections =
          models == null ? List.of() : models.stream().map(ConfiguredModel::toSelection).toList();
      new PredictionInputValidator().validateSelections(selections);
      return selections;
    }
  }

  /** JSON preserves model-specific numeric, boolean, array, and null values during binding. */
  public record ConfiguredModel(
      String modelId,
      ExecutionKind executionKind,
      String configurationJson,
      Integer seed,
      Integer trials) {
    private ModelSelection toSelection() {
      Map<String, Object> configuration = null;
      if (configurationJson != null) {
        try {
          configuration =
              CONFIGURATION_MAPPER.readValue(
                  configurationJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException error) {
          // Parser diagnostics can contain the supplied configuration; keep the startup error safe.
          throw new IllegalArgumentException(
              "Model configuration-json must be a valid JSON object.");
        }
        if (configuration == null) {
          throw new IllegalArgumentException(
              "Model configuration-json must be a valid JSON object.");
        }
      }
      return new ModelSelection(modelId, executionKind, configuration, seed, trials);
    }
  }

  @Bean
  public PredictionOrchestrator predictionOrchestrator(
      PredictionDataProvider dataProvider,
      PythonPredictionClient predictionClient,
      Properties properties) {
    return new PredictionOrchestrator(dataProvider, predictionClient, properties.toSelections());
  }
}
