package com.ou.capstone.data;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds all canonical teams and games in memory and serves them through CanonicalDataProvider.
 * Loading adds to what is already there, so historical seasons and the current season coexist.
 * Records with the same canonical ID are merged into one. Nothing is persisted: data is gone on
 * restart and must be loaded again.
 */
@Component
public class InMemoryCanonicalDataStore implements CanonicalDataProvider {

  private final Map<String, Team> teams = new LinkedHashMap<>();
  private final Map<String, Game> games = new LinkedHashMap<>();

  // Loaded games do not prove that Python has their data. Track acknowledged IDs separately.
  private final Map<Integer, PredictionSnapshot> snapshots = new LinkedHashMap<>();
  private final PredictionSnapshot fallback;

  public InMemoryCanonicalDataStore() {
    this(0, "");
  }

  @Autowired
  public InMemoryCanonicalDataStore(
      @Value("${prediction.snapshot.fallback-season:0}") int fallbackSeason,
      @Value("${prediction.snapshot.fallback-id:}") String fallbackId) {
    // Require the pair so a local file ID cannot accidentally be reused for another season.
    if (fallbackSeason == 0 && fallbackId.isBlank()) {
      fallback = null;
    } else if (fallbackSeason > 0 && !fallbackId.isBlank()) {
      fallback = new PredictionSnapshot(fallbackId, fallbackSeason);
    } else {
      throw new IllegalArgumentException("Snapshot fallback requires a positive season and an ID.");
    }
  }

  // The publication service calls this after Python accepts the dataset. Replacing the
  // selected ID affects future requests; Python keeps the earlier snapshot immutable.
  public synchronized void recordPublishedSnapshot(int season, String snapshotId) {
    if (season < 1 || snapshotId == null || snapshotId.isBlank()) {
      throw new IllegalArgumentException(
          "Published snapshot requires a positive season and an ID.");
    }
    snapshots.put(season, new PredictionSnapshot(snapshotId, season));
  }

  // Adds teams and games to the store. A team or game whose ID is already stored replaces
  // the old one, except that a finished game is never replaced by an unfinished one.
  public synchronized void load(Collection<Team> newTeams, Collection<Game> newGames) {
    for (Team team : newTeams) {
      teams.put(team.id(), team);
    }
    for (Game game : newGames) {
      Game existing = games.get(game.id());
      boolean wouldUndoResult =
          existing != null
              && existing.status() == GameStatus.FINAL
              && game.status() != GameStatus.FINAL;
      if (!wouldUndoResult) {
        games.put(game.id(), game);
      }
    }
  }

  @Override
  public synchronized Optional<Game> findGame(String gameId) {
    return Optional.ofNullable(games.get(gameId));
  }

  // Prefer an acknowledged publication. The fallback is an explicit promise that Python
  // loaded the matching file; without either, let orchestration report missing data.
  @Override
  public synchronized Optional<PredictionSnapshot> findSnapshot(int season) {
    PredictionSnapshot published = snapshots.get(season);
    return Optional.ofNullable(
        published != null
            ? published
            : fallback != null && fallback.season() == season ? fallback : null);
  }

  // A team is in a snapshot if it plays at least one game in that snapshot's season.
  @Override
  public synchronized Optional<Team> findTeam(PredictionSnapshot snapshot, String teamId) {
    boolean playsInSeason =
        games.values().stream()
            .anyMatch(
                game ->
                    game.season() == snapshot.season()
                        && (teamId.equals(game.homeTeamId()) || teamId.equals(game.awayTeamId())));
    return playsInSeason ? Optional.ofNullable(teams.get(teamId)) : Optional.empty();
  }

  @Override
  public synchronized Optional<Team> findTeam(String teamId) {
    return Optional.ofNullable(teams.get(teamId));
  }

  @Override
  public synchronized List<Game> findAllGames() {
    return new ArrayList<>(games.values());
  }

  // Filters the games list by specific attributes.
  @Override
  public synchronized List<Game> findGames(GameFilter filter) {
    return games.values().stream()
        .filter(
            game ->
                filter.teamId() == null
                    || filter.teamId().equals(game.homeTeamId())
                    || filter.teamId().equals(game.awayTeamId()))
        .filter(
            game ->
                filter.date() == null
                    || (game.scheduledAt() != null
                        && filter
                            .date()
                            .equals(game.scheduledAt().atOffset(ZoneOffset.UTC).toLocalDate())))
        .filter(game -> filter.status() == null || filter.status() == game.status())
        .filter(game -> filter.season() == null || filter.season() == game.season())
        .toList();
  }

  @Override
  public synchronized List<Game> findGames(int season) {
    return games.values().stream().filter(game -> game.season() == season).toList();
  }
}
