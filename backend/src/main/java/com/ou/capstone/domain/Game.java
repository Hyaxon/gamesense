package com.ou.capstone.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Canonical scheduled or played game matching the shared Game schema.
 *
 * <p>Optional numeric and boolean fields retain missing values instead of defaulting to zero or
 * false. These records carry data; availability and prediction-input validation belong to services.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Game(
    String id,
    String externalId,
    int season,
    Integer week,
    String homeTeamId,
    String awayTeamId,
    Venue venue,
    Boolean isNeutralSite,
    Instant scheduledAt,
    GameStatus status,
    Integer homeScore,
    Integer awayScore,
    String winnerTeamId) {}
