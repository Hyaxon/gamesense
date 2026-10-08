package com.ou.capstone.prediction;

/**
 * One validation issue within a shared error response.
 *
 * <p>The path is a JSON Pointer; an empty path identifies the request root.
 */
public record ErrorDetail(String path, String message) {}
