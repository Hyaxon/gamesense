package com.ou.capstone.prediction.contract;

/**
 * Contract execution modes: repeatable prediction, stochastic prediction, or repeated-trial
 * simulation.
 */
public enum ExecutionKind {
  DETERMINISTIC,
  STOCHASTIC,
  SIMULATION
}
