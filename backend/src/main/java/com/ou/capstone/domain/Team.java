package com.ou.capstone.domain;

// A college football program. conference is the conference name, not an id
public record Team(
    String id,           // UUID v4
    String externalId,   // id from the source feed (the full team name for Adam's feed)
    String name,         // e.g. "Oklahoma Sooners"
    String shortName,    // optional, e.g. "Oklahoma"
    String conference,   // e.g. "Southeastern Conference"
    String logoUrl       // optional
) {}