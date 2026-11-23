package com.ou.capstone.prediction.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.SimulationOutcome;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import java.util.List;

/**
 * Frontend response matching prediction-response.schema.json. Custom matchups never include a
 * gameId, and aggregation is out of scope, so neither field is modeled.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PredictionResponse(
    Matchup matchup,
    List<SinglePredictionResult> predictions,
    List<SimulationOutcome> simulations) {}
