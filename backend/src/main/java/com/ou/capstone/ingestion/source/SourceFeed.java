package com.ou.capstone.ingestion.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Top-level response from the current-season data source. Mirrors the external format only; it is
// deliberately separate from our internal domain models.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceFeed(List<SourceTeam> teams) {}
