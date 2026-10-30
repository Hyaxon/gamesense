package com.ou.capstone.ingestion.mapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds canonical IDs from source identifiers. The same team name or game key always gives the
 * same ID, so a record keeps its identity across sources, reloads, and restarts, and overlapping
 * records from different sources collapse into one. IDs are valid version 4 UUIDs, as the
 * prediction service requires, but they are derived from the text, not random.
 */
public final class CanonicalIds {

  // The version number sits in bits 12-15 of the most significant half of a UUID.
  private static final long VERSION_MASK = 0xF000L;
  private static final long VERSION_4 = 0x4000L;

  private CanonicalIds() {}

  // Canonical ID for a team, from its source identifier (the full team name in this feed).
  public static String teamId(String externalTeamId) {
    return fromText("team|" + externalTeamId);
  }

  // Canonical ID for a game, from its source key (date plus the two team names).
  public static String gameId(String externalGameKey) {
    return fromText("game|" + externalGameKey);
  }

  // Hashes the text into a UUID, then marks it as version 4. The prediction service accepts only
  // version 4 IDs, and a name-based UUID is version 3 by default. The hash fills the rest of the
  // bits, so the same text always gives the same ID.
  private static String fromText(String text) {
    UUID hashed = UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));
    long mostSignificantBits = (hashed.getMostSignificantBits() & ~VERSION_MASK) | VERSION_4;
    return new UUID(mostSignificantBits, hashed.getLeastSignificantBits()).toString();
  }
}
