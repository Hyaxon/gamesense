package com.ou.capstone.prediction.orchestration;

import com.ou.capstone.prediction.contract.ExecutionKind;
import java.util.Map;

/**
 * Settings for one configured model execution, independent of the matchup being predicted.
 *
 * <p>modelId identifies a concrete Python adapter, such as coin-flip-v1. The orchestrator supplies
 * the matchup, snapshot ID, and a fresh execution ID when constructing a ModelExecutionRequest.
 * Selecting a model does not register it in Python or guarantee its availability.
 *
 * <p>configuration contains model-specific JSON settings and may be absent. Common seed and trials
 * controls belong in their own fields. A deterministic execution allows neither control; stochastic
 * execution allows an optional seed; simulation requires trials and allows an optional seed. Seeds
 * require adapter support and range from 0 through 2147483647; trials range from 1 through 100000.
 *
 * <p>This record carries settings. Common input validation is performed before orchestration sends
 * any requests; Python validates model-specific configuration and capabilities.
 */
public record ModelSelection(
    String modelId,
    ExecutionKind executionKind,
    Map<String, Object> configuration,
    Integer seed,
    Integer trials) {}
