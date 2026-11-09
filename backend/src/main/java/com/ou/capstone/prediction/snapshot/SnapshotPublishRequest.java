package com.ou.capstone.prediction.snapshot;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import java.util.List;
import java.util.Map;

/** Snapshot publication payload; Python assigns the snapshot ID after validation. */
public record SnapshotPublishRequest(
    int season, Map<String, String> provenance, List<Team> teams, List<Game> games) {
  public SnapshotPublishRequest {
    provenance = Map.copyOf(provenance);
    teams = List.copyOf(teams);
    games = List.copyOf(games);
  }
}
