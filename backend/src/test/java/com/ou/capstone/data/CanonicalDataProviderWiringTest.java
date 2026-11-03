package com.ou.capstone.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Verifies the application supplies the canonical store as the existing PredictionDataProvider, so
// orchestration and every other consumer use the same single access path.
@SpringBootTest(
    properties = "gamesense.ingestion.enabled=false",
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CanonicalDataProviderWiringTest {

  @Autowired private PredictionDataProvider predictionDataProvider;

  @Autowired private CanonicalDataProvider canonicalDataProvider;

  @Test
  void predictionProviderAndCanonicalProviderAreTheSameStore() {
    assertThat(predictionDataProvider).isInstanceOf(InMemoryCanonicalDataStore.class);
    assertThat(canonicalDataProvider).isSameAs(predictionDataProvider);
  }
}
