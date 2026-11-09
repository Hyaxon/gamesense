package com.ou.capstone.prediction.snapshot;

import com.ou.capstone.data.CanonicalDataProvider;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Builds a season snapshot without dropping or repairing malformed records. */
@Component
public class PredictionSnapshotBuilder {
  private final CanonicalDataProvider dataProvider;

  public PredictionSnapshotBuilder(CanonicalDataProvider dataProvider) {
    this.dataProvider = dataProvider;
  }

  public SnapshotPublishRequest build(int season) {
    if (season < 1) {
      throw new IllegalArgumentException("Snapshot season must be positive.");
    }
    List<Game> games = dataProvider.findGames(season);
    if (games.isEmpty()) {
      throw new IllegalArgumentException("No games available for season " + season);
    }
    Map<String, Team> teams = new LinkedHashMap<>();
    for (Game game : games) {
      validateGame(game, season);
      for (String teamId : List.of(game.homeTeamId(), game.awayTeamId())) {
        teams.computeIfAbsent(
            teamId,
            id ->
                dataProvider
                    .findTeam(id)
                    .orElseThrow(() -> invalidGame(game, "Team is unavailable: " + id)));
      }
    }
    return new SnapshotPublishRequest(
        season,
        Map.of("source", "backend-canonical-data-store"),
        List.copyOf(teams.values()),
        games);
  }

  private static void validateGame(Game game, int season) {
    if (game.season() != season || game.status() == null || game.scheduledAt() == null) {
      throw invalidGame(
          game, "Season, status, and scheduled time must be available and consistent.");
    }
    if (game.homeTeamId() == null
        || game.awayTeamId() == null
        || game.homeTeamId().equals(game.awayTeamId())) {
      throw invalidGame(game, "Game teams must exist and differ.");
    }
    if ((game.homeScore() != null && game.homeScore() < 0)
        || (game.awayScore() != null && game.awayScore() < 0)) {
      throw invalidGame(game, "Scores cannot be negative.");
    }
    if (game.status() == GameStatus.FINAL) {
      if (game.homeScore() == null || game.awayScore() == null || game.isNeutralSite() == null) {
        throw invalidGame(game, "Final games require scores and an explicit location policy.");
      }
      String winner =
          game.homeScore() > game.awayScore()
              ? game.homeTeamId()
              : game.awayScore() > game.homeScore() ? game.awayTeamId() : null;
      if (!Objects.equals(winner, game.winnerTeamId())) {
        throw invalidGame(game, "Winner must agree with final scores.");
      }
    } else if (game.status() == GameStatus.SCHEDULED
        && (game.homeScore() != null || game.awayScore() != null || game.winnerTeamId() != null)) {
      throw invalidGame(game, "Scheduled games cannot contain scores or a winner.");
    }
  }

  private static IllegalArgumentException invalidGame(Game game, String reason) {
    return new IllegalArgumentException("Cannot publish game " + game.id() + ": " + reason);
  }
}
