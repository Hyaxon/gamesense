package com.ou.capstone.domain;

import java.time.Instant;

// One matchup between two teams. Scores and winnerTeamId are only set once status is FINAL
public record Game(
    String id,               // UUID v4
    String externalId,       // optional
    int season,
    Integer week,            // optional
    String homeTeamId,
    String awayTeamId,
    Venue venue,             // optional, null for Adam's feed
    Boolean isNeutralSite,   // optional
    Instant scheduledAt,
    GameStatus status,
    Integer homeScore,       // null until the game has a result
    Integer awayScore,
    String winnerTeamId      // null if not FINAL or if tied
) {}