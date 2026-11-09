package com.ou.capstone.prediction.snapshot;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import java.util.List;
import java.util.Map;

/**
 * Publication payload using the existing canonical Team and Game wire formats. There is no
 * dataSnapshotId because Python assigns it when accepting the publication.
 */
public record SnapshotPublishRequest(
    int season, Map<String, String> provenance, List<Team> teams, List<Game> games) {
  public SnapshotPublishRequest {
    // Detach collection membership from the caller so later changes cannot alter
    // the dataset being serialized for this publication.
    provenance = Map.copyOf(provenance);
    teams = List.copyOf(teams);
    games = List.copyOf(games);
  }
}
