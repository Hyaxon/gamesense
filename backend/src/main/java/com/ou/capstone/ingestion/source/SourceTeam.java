package com.ou.capstone.ingestion.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// One team from the external feed: its name, conference ("league"), and day-by-day schedule.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceTeam(String name, String league, List<SourceScheduleEntry> schedule) {}
