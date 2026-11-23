package com.ou.capstone.controller;

import com.ou.capstone.data.CanonicalDataProvider;
import com.ou.capstone.data.TeamFilter;
import com.ou.capstone.domain.Team;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes canonical team data through /teams. Uses CanonicalDataProvider so HTTP handlers remain
 * independent of the storage implementation. Retrieval reads already loaded data and does not fetch
 * source feeds.
 */
@RestController
@RequestMapping("/teams")
public class TeamsController {

  private final CanonicalDataProvider canonicalDataProvider;

  public TeamsController(CanonicalDataProvider canonicalDataProvider) {
    this.canonicalDataProvider = canonicalDataProvider;
  }

  // Optional filters can be combined.
  // example usage: GET /teams?season=2025&conference=Big Ten
  @GetMapping
  public List<Team> getTeams(
      @RequestParam(name = "season", required = false) Integer season,
      @RequestParam(name = "conference", required = false) String conference) {
    return canonicalDataProvider.findTeams(new TeamFilter(conference, season));
  }

  // Uses canonical IDs. An unknown team returns 404.
  // example usage: GET /teams/{teamId}
  @GetMapping("/{teamId}")
  public ResponseEntity<Team> getTeam(@PathVariable String teamId) {
    return ResponseEntity.of(canonicalDataProvider.findTeam(teamId));
  }
}
