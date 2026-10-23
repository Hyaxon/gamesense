package com.ou.capstone.ingestion.historical.raw;

import java.util.List;

/** the top level of Schedule.json, one object with a teams list */
public record RawFeed(List<RawTeam> teams) {}
