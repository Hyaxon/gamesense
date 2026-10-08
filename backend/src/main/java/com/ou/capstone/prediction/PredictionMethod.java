package com.ou.capstone.prediction;

/**
 * Algorithm families supported by the shared contract, rather than installed model identifiers.
 *
 * <p>Multiple registered models can share a family; availability is controlled by Python's
 * registry.
 */
public enum PredictionMethod {
  ELO,
  GLICKO2,
  TRUESKILL,
  MONTE_CARLO,
  MACHINE_LEARNING,
  BRADLEY_TERRY,
  HOME_GROWN,
  RANDOM
}
