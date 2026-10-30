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

// Exposes a /games endpoint to retrieve information about stored games.
@RestController
@RequestMapping("/games")
public class GamesController {

    private final CanonicalDataProvider canonicalDataProvider;

    public GamesController(CanonicalDataProvider canonicalDataProvider) {
        this.canonicalDataProvider = canonicalDataProvider;
    }

    @GetMapping
    public List<Game> getGames() {
        return canonicalDataProvider.findAllGames();
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable String gameId) {
        return ResponseEntity.of(canonicalDataProvider.findGame(gameId));
    }

    @GetMapping(params = "status")
    public Map<String, GameStatus> getGamesByStatus(@RequestParam GameStatus status) {
        return Map.of("status", status);
    }

    @GetMapping(params = "teamId")
    public Map<String, String> getGamesByTeam(@RequestParam String teamId) {
        return Map.of("teamid", teamId);
    }

    @GetMapping(params = "scheduledAt")
    public Map<String, Instant> getGamesByInstant(@RequestParam Instant scheduledAt) {
        return Map.of("scheduled at", scheduledAt);
    }
}
