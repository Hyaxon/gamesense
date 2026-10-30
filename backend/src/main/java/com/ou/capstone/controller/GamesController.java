package com.ou.capstone.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ou.capstone.data.CanonicalDataProvider;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;

/**
 * Exposes canonical game data through /games. Uses CanonicalDataProvider so
 * HTTP handlers
 * remain independent of the storage implementation. Loading happens during
 * startup so
 * retrieval requests do not fetch the source feed again.
 */
@RestController
@RequestMapping("/games")
public class GamesController {

    private final CanonicalDataProvider canonicalDataProvider;

    public GamesController(CanonicalDataProvider canonicalDataProvider) {
        this.canonicalDataProvider = canonicalDataProvider;
    }

    // Returns all loaded seasons; an empty array represents a valid collection with
    // no games.
    // example usage: GET /games
    @GetMapping
    public List<Game> getGames() {
        return canonicalDataProvider.findAllGames();
    }

    // Uses canonical IDs to keep lookups independent of
    // source-specific identifiers.
    // A missing game returns 404 so clients can distinguish absence from a
    // successful lookup.
    // example usage: GET /games/{gameId}
    @GetMapping("/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable String gameId) {
        return ResponseEntity.of(canonicalDataProvider.findGame(gameId));
    }

    // Handles status-based requests; enum binding rejects unsupported game
    // statuses.
    // example usage: GET /games?status=FINAL
    @GetMapping(params = "status")
    public Map<String, GameStatus> getGamesByStatus(@RequestParam GameStatus status) {
        return Map.of("status", status);
    }

    // Handles team-based requests using an ID rather than a potentially ambiguous
    // team name.
    // example usage: GET /games?teamId={teamId}
    @GetMapping(params = "teamId")
    public Map<String, String> getGamesByTeam(@RequestParam String teamId) {
        return Map.of("teamid", teamId);
    }

    // Uses Instant for scheduledAt requests so timestamps identify a point in time
    // across zones.
    // example usage: GET /games?scheduledAt=2025-09-06T00:00:00Z
    @GetMapping(params = "scheduledAt")
    public Map<String, Instant> getGamesByInstant(@RequestParam Instant scheduledAt) {
        return Map.of("scheduled at", scheduledAt);
    }
}
