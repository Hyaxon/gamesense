package com.ou.capstone.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.results.GamePredictions;

/**
 * Game fields enriched with available canonical teams and stored predictions, matching
 * GameResponse. Predictions are omitted for games that were never queued for prediction.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResponse(
    @JsonUnwrapped Game game, Team homeTeam, Team awayTeam, GamePredictions predictions) {}
