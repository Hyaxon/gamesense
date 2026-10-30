package com.ou.capstone.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ou.capstone.data.CanonicalDataProvider;
import com.ou.capstone.data.GameFilter;
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

    // One handler allows optional criteria to be combined without competing route
    // mappings.
    // example usage:
    // GET /games?teamId={teamId}&date=2025-09-06&status=FINAL&season=2025
    @GetMapping
    public List<Game> getGames(
            @RequestParam(name = "teamId", required = false) String teamId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) GameStatus status,
            @RequestParam(name = "season", required = false) Integer season) {
        return canonicalDataProvider.findGames(new GameFilter(teamId, date, status, season));
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

}
