package com.ou.capstone.ingestion.historical.mapper;

import java.util.UUID;

import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.historical.MalformedRecordException;
import com.ou.capstone.ingestion.historical.raw.RawTeam;

public class TeamMapper {

    public Team map(RawTeam raw) {
        if (raw.name() == null || raw.name().isBlank()) {
            throw new MalformedRecordException("Team is missing a name");
        }
        if (raw.league() == null || raw.league().isBlank()) {
            throw new MalformedRecordException("Team is missing a conference: " + raw.name());
        }
        String name = raw.name().trim();
        return new Team(
            UUID.randomUUID().toString(),
            name,                                       // externalId, the feed has no other id
            name,
            null,                            // shortName, not in the feed
            raw.league().trim(),
            null                               // logoUrl
        );
    }
}
