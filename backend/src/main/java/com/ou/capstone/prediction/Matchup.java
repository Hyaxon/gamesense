package com.ou.capstone.prediction;

/**
 * Canonical home and away team identifiers, season, and neutral-site setting for an execution.
 *
 * <p>This record carries contract data; it does not retrieve teams or validate their availability.
 */
public record Matchup(String homeTeamId, String awayTeamId, int season, boolean isNeutralSite) {}
