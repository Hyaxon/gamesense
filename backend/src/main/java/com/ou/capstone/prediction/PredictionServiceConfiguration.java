package com.ou.capstone.prediction;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds and validates prediction.service settings and creates the reusable Spring client bean.
 *
 * <p>Service location and positive connection/request timeouts are supplied through configuration.
 */
@Configuration
@EnableConfigurationProperties(PredictionServiceConfiguration.Properties.class)
public class PredictionServiceConfiguration {

  @ConfigurationProperties(prefix = "prediction.service")
  public record Properties(URI baseUrl, Duration connectTimeout, Duration requestTimeout) {

    public Properties {
      if (baseUrl == null
          || baseUrl.getHost() == null
          || !("http".equalsIgnoreCase(baseUrl.getScheme())
              || "https".equalsIgnoreCase(baseUrl.getScheme()))
          || baseUrl.getUserInfo() != null
          || baseUrl.getQuery() != null
          || baseUrl.getFragment() != null
          || !(baseUrl.getPath().isEmpty() || baseUrl.getPath().equals("/"))) {
        throw new IllegalArgumentException(
            "Prediction service base URL must be an HTTP(S) root address.");
      }

      if (connectTimeout == null
          || connectTimeout.isZero()
          || connectTimeout.isNegative()
          || requestTimeout == null
          || requestTimeout.isZero()
          || requestTimeout.isNegative()) {
        throw new IllegalArgumentException("Prediction service timeouts must be positive.");
      }
    }
  }

  @Bean
  public PythonPredictionClient pythonPredictionClient(
      ObjectMapper objectMapper, Properties properties) {
    return new PythonPredictionClient(
        objectMapper,
        properties.baseUrl(),
        properties.connectTimeout(),
        properties.requestTimeout());
  }
}
