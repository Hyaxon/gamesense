package com.ou.capstone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.nio.file.Path;

//import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;

// Entry point for the backend Spring Boot application.
@SpringBootApplication
public class BackendApplication implements ApplicationRunner {
  // private final CurrentSeasonIngestionService currentSeasonIngestionService;
  private final HistoricalIngestionService historicalIngestionService;

  public BackendApplication(
      // CurrentSeasonIngestionService currentSeasonIngestionService,
      HistoricalIngestionService historicalIngestionService) {
    // this.currentSeasonIngestionService = currentSeasonIngestionService;
    this.historicalIngestionService = historicalIngestionService;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    // currentSeasonIngestionService.loadCurrentSeason();
    historicalIngestionService.loadFile(Path.of("../data/historical/Schedule-2025.json"));
  }

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
