package com.ou.capstone.domain;

// A grouping of teams. No id, teams reference it by name
public record Conference(
    String name,         // e.g. "Southeastern Conference"
    String shortName     // optional, e.g. "SEC"
) {}