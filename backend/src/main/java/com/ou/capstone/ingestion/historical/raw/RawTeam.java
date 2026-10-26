package com.ou.capstone.ingestion.historical.raw;

import java.util.List;

/** one team from the feed's "teams" list, with its whole schedule. */
public record RawTeam(
    String name, // full team name
    String league, // conference name
    List<RawScheduleEntry> schedule // one entry per calendar day
    ) {}
