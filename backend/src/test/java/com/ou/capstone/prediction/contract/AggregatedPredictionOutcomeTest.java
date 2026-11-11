package com.ou.capstone.prediction.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies the aggregate against the shared example and its contract invariants. */
class AggregatedPredictionOutcomeTest {

  private static final File EXAMPLE =
      Path.of("../shared/examples/aggregated-prediction-outcome.json").toFile();
  private static final String CONTRIBUTOR = "7f6e5d4c-3b2a-4190-8b7c-6a5f4e3d2c1b";

  // Spring Boot writes instants as ISO-8601 strings by default, not numeric timestamps.
  private final JsonMapper mapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  @Test
  void serializesExactlyAsSharedExample() throws Exception {
    JsonNode actual =
        mapper.readTree(mapper.writeValueAsString(outcome(List.of(CONTRIBUTOR), 0.72)));

    assertThat(actual).isEqualTo(mapper.readTree(EXAMPLE));
  }

  @Test
  void deserializesSharedExample() throws Exception {
    assertThat(mapper.readValue(EXAMPLE, AggregatedPredictionOutcome.class))
        .isEqualTo(outcome(List.of(CONTRIBUTOR), 0.72));
  }

  @Test
  void rejectsEmptyContributors() {
    assertThatIllegalArgumentException().isThrownBy(() -> outcome(List.of(), 0.72));
  }

  @Test
  void rejectsDuplicateContributors() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> outcome(List.of(CONTRIBUTOR, CONTRIBUTOR), 0.72));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
  void rejectsConfidenceOutsideProbabilityRange(double confidence) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> outcome(List.of(CONTRIBUTOR), confidence));
  }

  @Test
  void rejectsNullId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new AggregatedPredictionOutcome(
                    null,
                    AggregationStrategy.AVERAGE,
                    List.of(CONTRIBUTOR),
                    "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
                    0.72,
                    Instant.parse("2025-08-29T12:00:00Z")));
  }

  @Test
  void keepsAnUnmodifiableCopyOfContributors() {
    List<String> source = new ArrayList<>(List.of(CONTRIBUTOR));
    AggregatedPredictionOutcome outcome = outcome(source, 0.72);

    source.add("another-result");

    assertThat(outcome.contributingResultIds()).containsExactly(CONTRIBUTOR);
    assertThatThrownBy(() -> outcome.contributingResultIds().add("another-result"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private AggregatedPredictionOutcome outcome(List<String> contributors, double confidence) {
    return new AggregatedPredictionOutcome(
        "2b3c4d5e-6f70-4819-9a0b-1c2d3e4f5061",
        AggregationStrategy.AVERAGE,
        contributors,
        "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
        confidence,
        Instant.parse("2025-08-29T12:00:00Z"));
  }
}
