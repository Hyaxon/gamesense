package com.ou.capstone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.nio.file.Path;

import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;

/**
 * Starts the backend web server and loads game data through a Spring startup callback.
 * Uses ApplicationRunner so ingestion runs after Spring has created and wired the services.
 */
@SpringBootApplication
public class BackendApplication implements ApplicationRunner {
  private final CurrentSeasonIngestionService currentSeasonIngestionService;
  private final HistoricalIngestionService historicalIngestionService;

  public BackendApplication(
      CurrentSeasonIngestionService currentSeasonIngestionService,
      HistoricalIngestionService historicalIngestionService) {
    this.currentSeasonIngestionService = currentSeasonIngestionService;
    this.historicalIngestionService = historicalIngestionService;
  }

  /**
   * Loads the live current season, then the 2025 historical file into the shared in-memory store.
   * Reloads on every startup because the in-memory store does not survive a process restart.
   * The relative file path assumes the application is launched from the backend directory.
   * An ingestion failure propagates and causes startup to fail; if the live load fails, the
   * historical load is not attempted.
   *
   * @throws IOException if the historical file cannot be read
   */
  @Override
  public void run(ApplicationArguments args) throws IOException {
    currentSeasonIngestionService.loadCurrentSeason();
    historicalIngestionService.loadFile(Path.of("../data/historical/Schedule-2025.json"));
  }

  // Starts Spring, which invokes the ApplicationRunner callback as part of startup.
  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
