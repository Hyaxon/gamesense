package com.ou.capstone.ingestion.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// One calendar day in a team's schedule, exactly as the external feed sends it. Every value is a
// raw string (scores, dates, etc.); converting them is left to the later normalization step.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceScheduleEntry(
    String index,
    String timestamp,
    String dayOfWeek,
    String location,
    String opponent,
    String outcome,
    String pointsScored,
    String pointsAllowed) {}
