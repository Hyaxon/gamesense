package com.ou.capstone.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;

/** Game fields enriched with available canonical teams for display, matching GameResponse. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResponse(@JsonUnwrapped Game game, Team homeTeam, Team awayTeam) {}
