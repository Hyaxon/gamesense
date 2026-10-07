package com.ou.capstone.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

class PredictionServiceConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
          .withUserConfiguration(PredictionServiceConfiguration.class);

  private ApplicationContextRunner configured() {
    return runner.withPropertyValues(
        "prediction.service.base-url=http://127.0.0.1:8123",
        "prediction.service.connect-timeout=250ms",
        "prediction.service.request-timeout=3s");
  }

  @Test
  void bindsSettingsAndCreatesReusableClient() {
    configured()
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(PythonPredictionClient.class);
              PredictionServiceConfiguration.Properties properties =
                  context.getBean(PredictionServiceConfiguration.Properties.class);
              assertThat(properties.baseUrl()).isEqualTo(URI.create("http://127.0.0.1:8123"));
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
              assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
              assertThat(context.getBean(PythonPredictionClient.class))
                  .isSameAs(context.getBean(PythonPredictionClient.class));
            });
  }

  @Test
  void applicationPropertiesAllowEnvironmentStyleOverrides() throws Exception {
    ResourcePropertySource applicationProperties =
        new ResourcePropertySource(new ClassPathResource("application.properties"));
    runner
        .withInitializer(
            context -> {
              context.getEnvironment().getPropertySources().addLast(applicationProperties);
            })
        .withPropertyValues(
            "PREDICTION_SERVICE_BASE_URL=http://localhost:8765",
            "PREDICTION_SERVICE_CONNECT_TIMEOUT=750ms",
            "PREDICTION_SERVICE_REQUEST_TIMEOUT=9s")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              PredictionServiceConfiguration.Properties properties =
                  context.getBean(PredictionServiceConfiguration.Properties.class);
              assertThat(properties.baseUrl()).isEqualTo(URI.create("http://localhost:8765"));
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(750));
              assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(9));
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://localhost",
        "/relative",
        "http://user@localhost",
        "http://localhost/path",
        "http://localhost?query=1",
        "http://localhost#fragment",
        "http://",
        ""
      })
  void rejectsInvalidServiceAddress(String address) {
    configured()
        .withPropertyValues("prediction.service.base-url=" + address)
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "prediction.service.connect-timeout=0s",
        "prediction.service.connect-timeout=-1s",
        "prediction.service.connect-timeout=invalid",
        "prediction.service.request-timeout=0s",
        "prediction.service.request-timeout=-1s",
        "prediction.service.request-timeout=invalid"
      })
  void rejectsInvalidTimeouts(String property) {
    configured().withPropertyValues(property).run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsMissingSettings() {
    runner.run(context -> assertThat(context).hasFailed());
  }
}
