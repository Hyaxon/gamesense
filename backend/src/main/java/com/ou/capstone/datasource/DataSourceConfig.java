package com.ou.capstone.datasource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Registers the data source settings and the HTTP client (with timeouts) used to call the source.
@Configuration
@EnableConfigurationProperties(CurrentSeasonProperties.class)
public class DataSourceConfig {

  // Builds the HTTP client for the data source using the configured connect and read timeouts.
  @Bean
  RestClient currentSeasonRestClient(CurrentSeasonProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.connectTimeout());
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
