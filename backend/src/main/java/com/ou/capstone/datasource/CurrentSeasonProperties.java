package com.ou.capstone.datasource;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Settings for the current-season data source, read from "gamesense.datasource.*" in
// application.properties (or environment variables such as GAMESENSE_DATASOURCE_URL).
@ConfigurationProperties(prefix = "gamesense.datasource")
public record CurrentSeasonProperties(
    String url,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout) {}
