package com.ou.capstone.ingestion.historical.raw;

public record RawScheduleEntry(
    String timestamp,
    String location,
    String opponent,
    String outcome,
    String pointsScored,
    String pointsAllowed
) {}
