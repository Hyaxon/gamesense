package com.ou.capstone.data;

/**
 * Reference to the immutable canonical data selected for prediction in one season.
 *
 * <p>The identifier must refer to a snapshot made available to Python. It is not a file path or a
 * trained-model identifier. This reference neither publishes data nor defines a historical cutoff.
 */
public record PredictionSnapshot(String dataSnapshotId, int season) {}
