package com.ou.capstone.ingestion.mapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds canonical IDs from source identifiers. The same team name or game key always gives the
 * same ID, so a record keeps its identity across sources, reloads, and restarts, and overlapping
 * records from different sources collapse into one.
 */
public final class CanonicalIds {

  private CanonicalIds() {}

  // Canonical ID for a team, from its source identifier (the full team name in this feed).
  public static String teamId(String externalTeamId) {
    return fromText("team|" + externalTeamId);
  }

  // Canonical ID for a game, from its source key (date plus the two team names).
  public static String gameId(String externalGameKey) {
    return fromText("game|" + externalGameKey);
  }

  private static String fromText(String text) {
    return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
