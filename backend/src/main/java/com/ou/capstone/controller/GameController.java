package com.ou.capstone.controller;

import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "http://localhost:5173")
public class GameController {

  @GetMapping
  public List<GameResponse> getGames() {
    return List.of(
        new GameResponse(
            1L,
            "Sat, Oct 24 • 6:00 PM",
            new TeamInfo("Mississippi State", "MSST", 10, "5-2"),
            new TeamInfo("Oklahoma", "OU", 11, "5-2"),
            new PredictionInfo(58, "OU", "#841617"),
            List.of(
                new DetailedPrediction("Bradley-Terry", "Oklahoma", 0.52),
                new DetailedPrediction("Glicko-2", "Oklahoma", 0.54),
                new DetailedPrediction("Machine Learning", "Oklahoma", 0.57),
                new DetailedPrediction("ELO", "Oklahoma", 0.51),
                new DetailedPrediction("Monte Carlo", "Oklahoma", 0.55))));
  }

  public record GameResponse(
      Long id,
      String dateTime,
      TeamInfo homeTeam,
      TeamInfo awayTeam,
      PredictionInfo prediction,
      List<DetailedPrediction> predictions) {}

  public record TeamInfo(String name, String logo, Integer ranking, String record) {}

  public record PredictionInfo(int percentage, String winnerLogo, String color) {}

  public record DetailedPrediction(String method, String predictedWinner, double confidenceScore) {}
}
