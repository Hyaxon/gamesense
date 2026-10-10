package com.ou.capstone.ingestion.historical.raw;

import java.util.List;

public record RawTeam(
    String name,
    String league,
    List<RawScheduleEntry> schedule
) {}
