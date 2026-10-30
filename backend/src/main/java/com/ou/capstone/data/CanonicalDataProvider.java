package com.ou.capstone.data;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import java.util.List;
import java.util.Optional;

/**
 * The one access path for canonical game and team data, whichever source supplied it. Extends the
 * prediction lookups with listing operations. Everything returned is a canonical domain object;
 * source-specific records never cross this interface.
 */
public interface CanonicalDataProvider extends PredictionDataProvider {

  // Every loaded game, across all seasons.
  List<Game> findAllGames();

  // Every loaded game in one season, or an empty list.
  List<Game> findGames(int season);

  // A team by canonical ID, regardless of season. Use a game's homeTeamId/awayTeamId to find the
  // teams that played it.
  Optional<Team> findTeam(String teamId);
}
