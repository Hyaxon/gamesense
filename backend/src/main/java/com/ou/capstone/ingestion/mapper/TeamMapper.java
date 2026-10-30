package com.ou.capstone.ingestion.mapper;

import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.MalformedRecordException;
import com.ou.capstone.ingestion.source.SourceTeam;

/**
 * turns one raw team into one canonical Team.
 *
 * <p>required fields are name and conference
 */
public class TeamMapper {

  public Team map(SourceTeam raw) {

    // required fields, blank counts as missing
    if (raw.name() == null || raw.name().isBlank()) {
      throw new MalformedRecordException("Team is missing a name");
    }
    if (raw.league() == null || raw.league().isBlank()) {
      throw new MalformedRecordException("Team is missing a conference: " + raw.name());
    }
    String name = raw.name().trim();
    return new Team(
        CanonicalIds.teamId(name),
        name, // externalId, the feed has no other id
        name,
        null, // shortName, not in the feed
        raw.league().trim(),
        null // logoUrl
        );
  }
}
