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

  // Adds teams and games to the store. A team or game whose ID is already stored
  // replaces
  // the old one, except that a finished game is never replaced by an unfinished
  // one.
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

  // A season has a snapshot once at least one of its games is loaded.
  @Override
  public synchronized Optional<PredictionSnapshot> findSnapshot(int season) {
    boolean hasGames = games.values().stream().anyMatch(game -> game.season() == season);
    return hasGames
        ? Optional.of(new PredictionSnapshot("season-" + season, season))
        : Optional.empty();
  }

  // A team is in a snapshot if it plays at least one game in that snapshot's
  // season.
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
