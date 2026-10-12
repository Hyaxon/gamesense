package com.ou.capstone.data;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import java.util.Optional;

/**
 * Canonical data lookups required by prediction orchestration.
 *
 * <p>Implementations supply already-normalized records with stable canonical identifiers. Source
 * retrieval, mapping, status determination, and snapshot publication are separate responsibilities.
 * Tests can implement this interface using canonical fixtures.
 *
 * <p>Lookup methods return Optional.empty() for unavailable records, never null. Operational
 * failures must not be silently represented as missing records.
 */
public interface PredictionDataProvider {

  /**
   * Finds an existing game by its canonical ID, preserving its supplied teams, season, and
   * location. Custom matchups do not require this lookup or the creation of a game record.
   */
  Optional<Game> findGame(String gameId);

  /**
   * Finds the snapshot explicitly selected for the requested season.
   *
   * <p>Returns empty when no snapshot is selected for that season; implementations must not fall
   * back to another season or invent a snapshot ID. Selection does not imply an as-of cutoff or
   * verify the remote Python service's availability.
   */
  Optional<PredictionSnapshot> findSnapshot(int season);

  /**
   * Finds a canonical team within exactly the referenced snapshot and season.
   *
   * <p>A team known elsewhere but absent from this snapshot is unavailable. Records associated with
   * a published snapshot reference must remain stable across lookups.
   */
  Optional<Team> findTeam(PredictionSnapshot snapshot, String teamId);
}
