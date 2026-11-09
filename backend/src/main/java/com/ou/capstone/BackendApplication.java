package com.ou.capstone;

import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;

/**
 * Starts the backend web server and loads game data through a Spring startup callback. Uses
 * ApplicationRunner so ingestion runs after Spring has created and wired the services.
 */
@SpringBootApplication
public class BackendApplication implements ApplicationRunner {
  private final CurrentSeasonIngestionService currentSeasonIngestionService;
  private final HistoricalIngestionService historicalIngestionService;
  private final boolean ingestionEnabled;
  private final Resource historicalData;

  public BackendApplication(
      CurrentSeasonIngestionService currentSeasonIngestionService,
      HistoricalIngestionService historicalIngestionService,
      @Value("${gamesense.ingestion.enabled:true}") boolean ingestionEnabled,
      @Value("${gamesense.ingestion.historical-location:classpath:historical/Schedule-2025.json}")
          Resource historicalData) {
    this.currentSeasonIngestionService = currentSeasonIngestionService;
    this.historicalIngestionService = historicalIngestionService;
    this.ingestionEnabled = ingestionEnabled;
    this.historicalData = historicalData;
  }

  /**
   * Loads the live current season, then the 2025 historical file into the shared in-memory store.
   * Reloads on every startup because the in-memory store does not survive a process restart. The
   * historical resource defaults to the packaged dataset, independent of working directory. An
   * ingestion failure propagates and causes startup to fail; if the live load fails, the historical
   * load is not attempted.
   *
   * @throws IOException if the historical file cannot be read
   */
  @Override
  public void run(ApplicationArguments args) throws IOException {
    // Allow context tests to start without network access or local historical files.
    if (!ingestionEnabled) {
      return;
    }
    currentSeasonIngestionService.loadCurrentSeason();
    try (var input = historicalData.getInputStream()) {
      historicalIngestionService.loadStream(input);
    }
  }

  // Starts Spring, which invokes the ApplicationRunner callback as part of startup.
  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
