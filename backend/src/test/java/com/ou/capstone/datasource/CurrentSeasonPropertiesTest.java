package com.ou.capstone.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Verifies the data source settings come from configuration rather than code, so they can be
// changed without touching any Java.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "gamesense.ingestion.enabled=false",
      "gamesense.datasource.url=http://other-source.test/feed",
      "gamesense.datasource.read-timeout=10s"
    })
class CurrentSeasonPropertiesTest {

  @Autowired private CurrentSeasonProperties properties;

  @Test
  void settingsAreReadFromConfiguration() {
    assertThat(properties.url()).isEqualTo("http://other-source.test/feed");
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
  }
}
