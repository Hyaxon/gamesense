package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Execution provenance: snapshot, mode, UTC timing, duration, and optional actual random seed. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionMetadata(
    ExecutionKind executionKind,
    String dataSnapshotId,
    Instant startedAt,
    Instant completedAt,
    double durationMilliseconds,
    Integer seed) {}
