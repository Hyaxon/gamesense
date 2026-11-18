package com.ou.capstone.prediction.service;

import com.ou.capstone.prediction.contract.PredictionMethod;
import java.util.List;

/**
 * Frontend request matching custom-prediction-request.schema.json.
 *
 * <p>Nullable wrappers let the service report missing fields as validation errors. A null methods
 * list means "all methods".
 */
public record CustomPredictionRequest(
    MatchupRequest matchup, List<PredictionMethod> methods, List<SimulationRequest> simulations) {

  /** Matchup as submitted; semantic checks are performed by orchestration. */
  public record MatchupRequest(
      String homeTeamId, String awayTeamId, Integer season, Boolean isNeutralSite) {}

  /** One requested repeated-trial simulation. */
  public record SimulationRequest(PredictionMethod method, Integer trials, Integer seed) {}
}
